package com.campus.complaints;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.concurrent.atomic.AtomicReference;

import static com.campus.complaints.ComplaintRequests.SubmitComplaintRequest;

@Service
public class ComplaintService {

    private final ComplaintRepository complaintRepository;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final ComplaintCategoryRepository categoryRepository;
    private final CampusLocationRepository locationRepository;
    private final ComplaintAssignmentRepository assignmentRepository;
    private final ComplaintStatusHistoryRepository statusHistoryRepository;
    private final ComplaintWorkNoteRepository workNoteRepository;
    private final ComplaintResolutionProofRepository resolutionProofRepository;
    private final ComplaintImageStorage imageStorage;
    private final TransactionTemplate transactionTemplate;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final SlaPolicy slaPolicy;

    public ComplaintService(
            ComplaintRepository complaintRepository,
            UserRepository userRepository,
            DepartmentRepository departmentRepository,
            ComplaintCategoryRepository categoryRepository,
            CampusLocationRepository locationRepository,
            ComplaintAssignmentRepository assignmentRepository,
            ComplaintStatusHistoryRepository statusHistoryRepository,
            ComplaintWorkNoteRepository workNoteRepository,
            ComplaintResolutionProofRepository resolutionProofRepository,
            ComplaintImageStorage imageStorage,
            TransactionTemplate transactionTemplate,
            NotificationService notificationService,
            AuditService auditService,
            SlaPolicy slaPolicy) {
        this.complaintRepository = complaintRepository;
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.categoryRepository = categoryRepository;
        this.locationRepository = locationRepository;
        this.assignmentRepository = assignmentRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.workNoteRepository = workNoteRepository;
        this.resolutionProofRepository = resolutionProofRepository;
        this.imageStorage = imageStorage;
        this.transactionTemplate = transactionTemplate;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.slaPolicy = slaPolicy;
    }

    public ComplaintResponse submit(
            AuthenticatedUser principal,
            SubmitComplaintRequest request,
            MultipartFile image) {
        String imageReference = image == null || image.isEmpty() ? null : imageStorage.store(image);
        try {
            return transactionTemplate.execute(status -> {
                User reporter = findActiveUser(principal.id());
                if (reporter.getRole() != UserRole.STUDENT && reporter.getRole() != UserRole.FACULTY) {
                    throw new ResponseStatusException(
                            HttpStatus.FORBIDDEN, "Only Students and Faculty can submit complaints.");
                }
                ComplaintCategory category = categoryRepository.findById(request.categoryId())
                        .filter(ComplaintCategory::isActive)
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.BAD_REQUEST, "Select an available complaint category."));
                String locationName = request.location().trim();
                if (locationName.isEmpty()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Location is required.");
                }
                CampusLocation location = locationRepository
                        .findFirstByBuildingIgnoreCaseAndRoomIsNull(locationName)
                        .orElseGet(() -> locationRepository.save(new CampusLocation(locationName, null, null)));
                String complaintCode = "CMP-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                        + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
                Complaint complaint = new Complaint(
                        reporter,
                        category,
                        location,
                        reporter.getDepartment(),
                        request.title().trim(),
                        request.description().trim(),
                        complaintCode,
                        request.priority(),
                        imageReference);
                complaint.setSlaTargetHours(
                        slaPolicy.targetHours(request.priority()), slaPolicy.dueSoonPercent());
                complaint = complaintRepository.saveAndFlush(complaint);
                statusHistoryRepository.saveAndFlush(new ComplaintStatusHistory(
                        complaint, reporter, null, ComplaintStatus.SUBMITTED, "Complaint submitted."));
                notificationService.complaintSubmitted(complaint);
                auditService.record(principal, "COMPLAINT_SUBMITTED", "COMPLAINT",
                        complaint.getId().toString(), complaint.getComplaintCode(),
                        complaint.getId(), complaint.getComplaintCode(), "Complaint submitted.",
                        null, Map.of(
                                "status", complaint.getStatus().name(),
                                "category", category.getName(),
                                "location", location.getBuilding(),
                                "priority", complaint.getPriority().name()));
                if (imageReference != null) {
                    auditService.record(principal, "COMPLAINT_IMAGE_UPLOADED", "COMPLAINT_IMAGE",
                            complaint.getId().toString(), complaint.getComplaintCode(),
                            complaint.getId(), complaint.getComplaintCode(),
                            "Complaint attachment uploaded.", null, Map.of("uploaded", true));
                }
                return ComplaintResponse.from(complaint);
            });
        } catch (RuntimeException | Error exception) {
            if (imageReference != null) {
                imageStorage.delete(imageReference);
            }
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public List<ComplaintResponse> myComplaints(AuthenticatedUser principal) {
        if (principal.role() != UserRole.STUDENT && principal.role() != UserRole.FACULTY) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Only Students and Faculty can view their own complaint list.");
        }
        return complaintRepository.findByReporter_IdOrderByCreatedAtDesc(principal.id()).stream()
                .map(ComplaintResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ComplaintResponse> assignedComplaints(AuthenticatedUser principal) {
        if (principal.role() != UserRole.STAFF) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only Staff can view assigned complaints.");
        }
        List<ComplaintAssignment> assignments = assignmentRepository
                .findByAssignee_IdAndUnassignedAtIsNullOrderByAssignedAtDesc(principal.id());
        List<Complaint> complaints = assignments.stream()
                .map(ComplaintAssignment::getComplaint)
                .distinct()
                .toList();
        Map<Long, List<String>> assignedStaffByComplaint = assignedStaffByComplaint(complaints);
        return complaints.stream()
                .map(complaint -> ComplaintResponse.from(
                        complaint, assignedStaffByComplaint.getOrDefault(complaint.getId(), List.of()), List.of()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ComplaintResponse> allComplaints(AuthenticatedUser principal) {
        requireRole(principal, UserRole.ADMIN);
        List<Complaint> complaints = complaintRepository.findAll();
        if (complaints.isEmpty()) {
            return List.of();
        }
        List<Long> complaintIds = complaints.stream().map(Complaint::getId).toList();
        Map<Long, List<ComplaintAssignment>> assignmentsByComplaint = assignmentRepository
                .findByComplaint_IdInAndUnassignedAtIsNull(complaintIds).stream()
                .collect(Collectors.groupingBy(assignment -> assignment.getComplaint().getId()));
        return complaints.stream()
                .sorted((first, second) -> second.getCreatedAt().compareTo(first.getCreatedAt()))
                .map(complaint -> ComplaintResponse.fromAdmin(
                        complaint,
                        assignedStaffNames(assignmentsByComplaint.get(complaint.getId())),
                        List.of(),
                        currentAssignment(complaint, assignmentsByComplaint.get(complaint.getId()))))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EligibleStaff> eligibleStaff(AuthenticatedUser principal, Long departmentId) {
        requireRole(principal, UserRole.ADMIN);
        findActiveDepartment(departmentId);
        return userRepository.findByRoleAndActiveTrueAndDepartment_IdOrderByFullNameAsc(
                        UserRole.STAFF, departmentId).stream()
                .map(user -> new EligibleStaff(user.getId(), user.getFullName()))
                .toList();
    }

    @Transactional
    public ComplaintResponse assignComplaint(
            AuthenticatedUser principal,
            Long complaintId,
            ComplaintRequests.AssignmentRequest request) {
        requireRole(principal, UserRole.ADMIN);
        Complaint complaint = complaintRepository.findByIdForUpdate(complaintId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Complaint was not found."));
        Department department = findActiveDepartment(request.departmentId());
        User staff = userRepository.findById(request.staffId())
                .filter(User::isActive)
                .filter(user -> user.getRole() == UserRole.STAFF)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Choose an active Staff account."));
        if (staff.getDepartment() == null || !staff.getDepartment().getId().equals(department.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Choose Staff who belong to the selected department.");
        }

        List<ComplaintAssignment> previousAssignments = assignmentRepository
                .findByComplaint_IdAndUnassignedAtIsNullOrderByAssignedAtAsc(complaintId);
        List<Map<String, Object>> previousState = previousAssignments.stream()
                .map(previous -> Map.<String, Object>of(
                        "staffId", previous.getAssignee().getId(),
                        "staffName", previous.getAssignee().getFullName()))
                .toList();
        previousAssignments.forEach(ComplaintAssignment::unassign);
        complaint.assignToDepartment(department);
        ComplaintAssignment assignment = assignmentRepository.saveAndFlush(new ComplaintAssignment(
                complaint, staff, findActiveUser(principal.id()), null));
        notificationService.assignmentChanged(complaint, assignment, previousAssignments);
        auditService.record(principal, "COMPLAINT_ASSIGNED", "COMPLAINT",
                complaint.getId().toString(), complaint.getComplaintCode(),
                complaint.getId(), complaint.getComplaintCode(), "Complaint assignment updated.",
                Map.of("assignments", previousState),
                Map.of("departmentId", department.getId(),
                        "staffId", staff.getId(),
                        "staffName", staff.getFullName()));
        return getComplaint(principal, complaintId);
    }

    @Transactional(readOnly = true)
    public ComplaintResponse getComplaint(AuthenticatedUser principal, Long complaintId) {
        Complaint complaint = findAuthorizedComplaint(principal, complaintId);
        List<ComplaintAssignment> assignments = assignmentRepository
                .findByComplaint_IdAndUnassignedAtIsNullOrderByAssignedAtAsc(complaintId);
        List<String> assignedStaff = assignedStaffNames(assignments);
        List<ComplaintResponse.StatusHistoryEntry> statusHistory = statusHistoryRepository
                .findByComplaint_IdOrderByChangedAtAsc(complaintId).stream()
                .map(history -> new ComplaintResponse.StatusHistoryEntry(
                        history.getPreviousStatus(),
                        history.getNewStatus(),
                        history.getChangedAt(),
                        history.getChangedBy() == null ? null : history.getChangedBy().getFullName(),
                        history.getNote()))
                .toList();
        List<ComplaintResponse.WorkNoteEntry> workNotes = principal.role() == UserRole.ADMIN
                || principal.role() == UserRole.STAFF
                || principal.role() == UserRole.FACULTY
                ? workNoteRepository.findByComplaint_IdAndRecordTypeOrderByCreatedAtAscIdAsc(
                                complaintId, ComplaintNoteType.WORK_NOTE).stream()
                        .map(note -> new ComplaintResponse.WorkNoteEntry(
                                note.getAuthor().getFullName(), note.getNote(), note.getCreatedAt()))
                        .toList()
                : List.of();
        ComplaintResponse.ResolutionProofEntry resolutionProof = resolutionProofRepository
                .findByComplaint_Id(complaintId)
                .map(proof -> new ComplaintResponse.ResolutionProofEntry(
                        "/api/complaints/" + complaintId + "/resolution-proof",
                        proof.getUploadedBy().getFullName(),
                        proof.getUploadedAt(),
                        statusHistory.stream()
                                .filter(entry -> entry.status() == ComplaintStatus.RESOLVED)
                                .map(ComplaintResponse.StatusHistoryEntry::note)
                                .filter(note -> note != null && !note.isBlank())
                                .reduce((first, second) -> second)
                                .orElse(null)))
                .orElse(null);
        if (principal.role() == UserRole.ADMIN) {
            return ComplaintResponse.fromAdmin(
                    complaint, assignedStaff, statusHistory, currentAssignment(complaint, assignments),
                    workNotes, resolutionProof);
        }
        if (principal.role() == UserRole.FACULTY) {
            return ComplaintResponse.fromFacultyDetails(
                    complaint, assignedStaff, statusHistory, workNotes, resolutionProof,
                    currentAssignment(complaint, assignments));
        }
        return ComplaintResponse.fromDetails(complaint, assignedStaff, statusHistory, workNotes, resolutionProof);
    }

    @Transactional
    public ComplaintResponse updateStaffStatus(
            AuthenticatedUser principal,
            Long complaintId,
            ComplaintRequests.StatusUpdateRequest request) {
        if (principal.role() != UserRole.STAFF && principal.role() != UserRole.ADMIN) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Only assigned Staff or an Admin can update complaint status.");
        }
        Complaint complaint = complaintRepository.findByIdForUpdate(complaintId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Complaint was not found."));
        User actor = findActiveUser(principal.id());
        if (principal.role() == UserRole.STAFF) {
            requireAssignedStaff(principal, complaintId);
            if (actor.getRole() != UserRole.STAFF) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only Staff can update complaint status.");
            }
        } else if (actor.getRole() != UserRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only an Admin can close resolved complaints.");
        }

        ComplaintStatus previousStatus = complaint.getStatus();
        boolean allowed = principal.role() == UserRole.STAFF
                ? isValidStaffTransition(previousStatus, request.status())
                : principal.role() == UserRole.ADMIN
                        && previousStatus == ComplaintStatus.RESOLVED
                        && request.status() == ComplaintStatus.CLOSED;
        if (!allowed) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "This complaint cannot move from " + previousStatus
                            + " to " + request.status() + " using this action.");
        }
        complaint.updateStatus(request.status());
        ComplaintStatusHistory history = statusHistoryRepository.saveAndFlush(new ComplaintStatusHistory(
                complaint, actor, previousStatus, request.status(),
                request.note() == null || request.note().isBlank()
                        ? (principal.role() == UserRole.ADMIN ? "Admin closed the resolved complaint."
                                : "Staff updated complaint status.")
                        : request.note().trim()));
        notificationService.statusChanged(complaint, history);
        auditService.record(principal, "COMPLAINT_STATUS_CHANGED", "COMPLAINT",
                complaint.getId().toString(), complaint.getComplaintCode(),
                complaint.getId(), complaint.getComplaintCode(), "Complaint status changed.",
                Map.of("status", previousStatus.name()),
                Map.of("status", request.status().name()));
        return getComplaint(principal, complaintId);
    }

    public ComplaintResponse resolveStaffComplaint(
            AuthenticatedUser principal,
            Long complaintId,
            ComplaintRequests.ResolutionRequest request,
            MultipartFile proofImage) {
        requireRole(principal, UserRole.STAFF);
        if (proofImage == null || proofImage.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload a resolution-proof image before resolving.");
        }
        String resolutionNote = request.note().trim();
        AtomicReference<String> storedImageReference = new AtomicReference<>();
        try {
            return transactionTemplate.execute(transactionStatus -> {
                Complaint complaint = complaintRepository.findByIdForUpdate(complaintId)
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND, "Complaint was not found."));
                requireAssignedStaff(principal, complaintId);
                User staff = findActiveUser(principal.id());
                if (staff.getRole() != UserRole.STAFF) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only Staff can resolve complaints.");
                }
                if (complaint.getStatus() != ComplaintStatus.IN_PROGRESS) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "Only an in-progress complaint can be resolved.");
                }
                if (resolutionProofRepository.findByComplaint_Id(complaintId).isPresent()) {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT, "A resolution proof has already been recorded for this complaint.");
                }

                String imageReference = imageStorage.storeResolutionProof(proofImage);
                storedImageReference.set(imageReference);
                resolutionProofRepository.saveAndFlush(
                        new ComplaintResolutionProof(complaint, staff, imageReference));
                complaint.updateStatus(ComplaintStatus.RESOLVED);
                ComplaintStatusHistory history = statusHistoryRepository.saveAndFlush(new ComplaintStatusHistory(
                        complaint, staff, ComplaintStatus.IN_PROGRESS, ComplaintStatus.RESOLVED, resolutionNote));
                notificationService.statusChanged(complaint, history);
                auditService.record(principal, "RESOLUTION_PROOF_UPLOADED", "RESOLUTION_PROOF",
                        complaint.getId().toString(), complaint.getComplaintCode(),
                        complaint.getId(), complaint.getComplaintCode(),
                        "Resolution proof uploaded.", null, Map.of("uploaded", true));
                auditService.record(principal, "COMPLAINT_RESOLVED", "COMPLAINT",
                        complaint.getId().toString(), complaint.getComplaintCode(),
                        complaint.getId(), complaint.getComplaintCode(), "Complaint resolved.",
                        Map.of("status", ComplaintStatus.IN_PROGRESS.name()),
                        Map.of("status", ComplaintStatus.RESOLVED.name()));
                return getComplaint(principal, complaintId);
            });
        } catch (RuntimeException | Error exception) {
            imageStorage.delete(storedImageReference.get());
            throw exception;
        }
    }

    @Transactional
    public ComplaintResponse addStaffWorkNote(
            AuthenticatedUser principal,
            Long complaintId,
            ComplaintRequests.WorkNoteRequest request) {
        requireRole(principal, UserRole.STAFF);
        Complaint complaint = complaintRepository.findByIdForUpdate(complaintId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Complaint was not found."));
        requireAssignedStaff(principal, complaintId);
        User staff = findActiveUser(principal.id());
        if (staff.getRole() != UserRole.STAFF) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only Staff can add work notes.");
        }
        String note = request.note().trim();
        if (note.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Work note cannot be empty.");
        }
        ComplaintWorkNote saved = workNoteRepository.saveAndFlush(new ComplaintWorkNote(complaint, staff, note));
        auditService.record(principal, "WORK_NOTE_ADDED", "COMPLAINT_NOTE",
                saved.getId().toString(), complaint.getComplaintCode(), complaint.getId(),
                complaint.getComplaintCode(), "Internal work note added.", null,
                Map.of("noteType", ComplaintNoteType.WORK_NOTE.name()));
        return getComplaint(principal, complaintId);
    }

    @Transactional(readOnly = true)
    public List<ComplaintNoteResponse> getComplaintNotes(
            AuthenticatedUser principal, Long complaintId, ComplaintNoteType noteType) {
        findAuthorizedComplaint(principal, complaintId);
        if (noteType == ComplaintNoteType.RESOLUTION_NOTE
                && principal.role() != UserRole.ADMIN && principal.role() != UserRole.STAFF) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Only assigned Staff or an Admin can view resolution notes.");
        }
        return workNoteRepository
                .findByComplaint_IdAndRecordTypeOrderByCreatedAtAscIdAsc(complaintId, noteType)
                .stream()
                .map(ComplaintNoteResponse::from)
                .toList();
    }

    @Transactional
    public ComplaintNoteResponse addComplaintNote(
            AuthenticatedUser principal,
            Long complaintId,
            ComplaintRequests.ComplaintNoteRequest request,
            ComplaintNoteType noteType) {
        Complaint complaint = findAuthorizedComplaint(principal, complaintId);
        User author = findActiveUser(principal.id());
        if (author.getRole() != principal.role()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Your account role has changed. Sign in again to continue.");
        }

        if (noteType == ComplaintNoteType.COMMENT) {
            if (author.getRole() != UserRole.STUDENT
                    && author.getRole() != UserRole.FACULTY
                    && author.getRole() != UserRole.STAFF
                    && author.getRole() != UserRole.ADMIN) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "Your role cannot add comments to complaints.");
            }
            if (author.getRole() == UserRole.STAFF) {
                requireAssignedStaff(principal, complaintId);
            }
        } else if (noteType == ComplaintNoteType.RESOLUTION_NOTE) {
            if (author.getRole() != UserRole.STAFF && author.getRole() != UserRole.ADMIN) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "Only assigned Staff or an Admin can add resolution notes.");
            }
            if (author.getRole() == UserRole.STAFF) {
                requireAssignedStaff(principal, complaintId);
            }
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported complaint note type.");
        }

        String content = request.content().trim();
        if (content.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Note content cannot be empty.");
        }
        String requestId = noteType == ComplaintNoteType.COMMENT ? request.requestId() : null;
        if (requestId != null) {
            ComplaintWorkNote existing = workNoteRepository
                    .findByComplaint_IdAndAuthor_IdAndRequestId(complaintId, author.getId(), requestId)
                    .orElse(null);
            if (existing != null) {
                if (!existing.getNote().equals(content)) {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT, "This request ID was already used for a different comment.");
                }
                return ComplaintNoteResponse.from(existing);
            }
        }
        ComplaintWorkNote saved = workNoteRepository.saveAndFlush(
                new ComplaintWorkNote(complaint, author, content, noteType, requestId));
        if (noteType == ComplaintNoteType.COMMENT) {
            notificationService.commentAdded(complaint, saved);
        }
        auditService.record(principal,
                noteType == ComplaintNoteType.COMMENT ? "COMMENT_ADDED" : "RESOLUTION_NOTE_ADDED",
                "COMPLAINT_NOTE", saved.getId().toString(), complaint.getComplaintCode(),
                complaint.getId(), complaint.getComplaintCode(),
                noteType == ComplaintNoteType.COMMENT
                        ? "Complaint comment added."
                        : "Resolution note added.",
                null, Map.of("noteType", noteType.name()));
        return ComplaintNoteResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public StoredComplaintImage getImage(AuthenticatedUser principal, Long complaintId) {
        Complaint complaint = findAuthorizedComplaint(principal, complaintId);
        if (complaint.getImageReference() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "This complaint has no image.");
        }
        return imageStorage.read(complaint.getImageReference());
    }

    @Transactional(readOnly = true)
    public StoredComplaintImage getResolutionProof(AuthenticatedUser principal, Long complaintId) {
        findAuthorizedComplaint(principal, complaintId);
        ComplaintResolutionProof proof = resolutionProofRepository.findByComplaint_Id(complaintId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No resolution proof has been recorded for this complaint."));
        return imageStorage.read(proof.getImageReference());
    }

    public List<ComplaintResponse.CategoryOption> categories() {
        return categoryRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(category -> new ComplaintResponse.CategoryOption(category.getId(), category.getName()))
                .toList();
    }

    private Complaint findAuthorizedComplaint(AuthenticatedUser principal, Long complaintId) {
        Complaint complaint = complaintRepository.findById(complaintId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Complaint was not found."));

        boolean authorized = switch (principal.role()) {
            case ADMIN -> true;
            case STUDENT -> complaint.getReporter().getId().equals(principal.id());
            case FACULTY -> complaint.getReporter().getId().equals(principal.id())
                    || complaint.getReporter().getRole() == UserRole.STUDENT;
            case STAFF -> assignmentRepository.existsByComplaint_IdAndAssignee_IdAndUnassignedAtIsNull(
                    complaintId, principal.id());
        };
        if (!authorized) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot access this complaint.");
        }
        return complaint;
    }

    private User findActiveUser(Long userId) {
        return userRepository.findById(userId)
                .filter(User::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required."));
    }

    private void requireAssignedStaff(AuthenticatedUser principal, Long complaintId) {
        if (!assignmentRepository.existsByComplaint_IdAndAssignee_IdAndUnassignedAtIsNull(
                complaintId, principal.id())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "You can only work on complaints assigned to your account.");
        }
    }

    private boolean isValidStaffTransition(ComplaintStatus current, ComplaintStatus requested) {
        return switch (current) {
            case SUBMITTED, UNDER_REVIEW -> requested == ComplaintStatus.ASSIGNED;
            case ASSIGNED -> requested == ComplaintStatus.IN_PROGRESS;
            case IN_PROGRESS -> false;
            case RESOLVED, CLOSED, REJECTED -> false;
        };
    }

    private List<String> assignedStaffNames(List<ComplaintAssignment> assignments) {
        if (assignments == null || assignments.isEmpty()) {
            return List.of();
        }
        return assignments.stream()
                .map(assignment -> assignment.getAssignee().getFullName())
                .distinct()
                .toList();
    }

    private Map<Long, List<String>> assignedStaffByComplaint(List<Complaint> complaints) {
        if (complaints.isEmpty()) {
            return Map.of();
        }
        List<Long> complaintIds = complaints.stream().map(Complaint::getId).toList();
        return assignmentRepository.findByComplaint_IdInAndUnassignedAtIsNull(complaintIds).stream()
                .collect(Collectors.groupingBy(
                        assignment -> assignment.getComplaint().getId(),
                        Collectors.mapping(
                                assignment -> assignment.getAssignee().getFullName(),
                                Collectors.collectingAndThen(
                                        Collectors.toList(),
                                        names -> names.stream().distinct().toList()))));
    }

    private ComplaintResponse.CurrentAssignment currentAssignment(
            Complaint complaint, List<ComplaintAssignment> assignments) {
        ComplaintAssignment active = assignments == null || assignments.isEmpty() ? null : assignments.get(0);
        Long departmentId = complaint.getDepartment() == null ? null : complaint.getDepartment().getId();
        return new ComplaintResponse.CurrentAssignment(
                departmentId,
                active == null ? null : active.getAssignee().getId(),
                active == null ? null : active.getAssignee().getFullName(),
                active == null ? null : active.getAssignedAt());
    }

    private Department findActiveDepartment(Long departmentId) {
        return departmentRepository.findById(departmentId)
                .filter(Department::isActive)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Choose an active department."));
    }

    private void requireRole(AuthenticatedUser principal, UserRole role) {
        if (principal.role() != role) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to access this resource.");
        }
    }

    @Transactional(readOnly = true)
    public byte[] exportComplaintsCsv(
            AuthenticatedUser principal,
            String status,
            String category,
            String priority,
            String department,
            String search) {
        List<ComplaintResponse> complaints;
        if (principal.role() == UserRole.ADMIN) {
            complaints = allComplaints(principal);
        } else if (principal.role() == UserRole.STAFF) {
            complaints = assignedComplaints(principal);
        } else {
            complaints = myComplaints(principal);
        }

        List<ComplaintResponse> filtered = complaints.stream()
                .filter(c -> status == null || status.isBlank() || (c.status() != null && c.status().name().equalsIgnoreCase(status.trim())))
                .filter(c -> category == null || category.isBlank() || (c.category() != null && c.category().equalsIgnoreCase(category.trim())))
                .filter(c -> priority == null || priority.isBlank() || (c.priority() != null && c.priority().name().equalsIgnoreCase(priority.trim())))
                .filter(c -> department == null || department.isBlank() || (c.department() != null && c.department().equalsIgnoreCase(department.trim())))
                .filter(c -> {
                    if (search == null || search.isBlank()) return true;
                    String q = search.trim().toLowerCase();
                    return (c.complaintId() != null && c.complaintId().toLowerCase().contains(q))
                            || (c.title() != null && c.title().toLowerCase().contains(q))
                            || (c.description() != null && c.description().toLowerCase().contains(q))
                            || (c.location() != null && c.location().toLowerCase().contains(q));
                })
                .toList();

        StringBuilder sb = new StringBuilder();
        sb.append("Complaint ID,Title,Status,Priority,Category,Department,Location,Reporter,Created At,SLA Target Hours,Resolved At,Assigned Staff\n");
        for (ComplaintResponse c : filtered) {
            String assignedStaff = c.assignedStaff() == null ? "" : String.join("; ", c.assignedStaff());
            sb.append(escapeCsv(c.complaintId())).append(",")
                    .append(escapeCsv(c.title())).append(",")
                    .append(escapeCsv(c.status() != null ? c.status().name() : "")).append(",")
                    .append(escapeCsv(c.priority() != null ? c.priority().name() : "")).append(",")
                    .append(escapeCsv(c.category())).append(",")
                    .append(escapeCsv(c.department())).append(",")
                    .append(escapeCsv(c.location())).append(",")
                    .append(escapeCsv(c.reporterName())).append(",")
                    .append(escapeCsv(c.submittedAt() != null ? c.submittedAt().toString() : "")).append(",")
                    .append(escapeCsv(c.sla() != null ? String.valueOf(c.sla().targetHours()) : "")).append(",")
                    .append(escapeCsv(c.sla() != null && c.sla().resolvedAt() != null ? c.sla().resolvedAt().toString() : "")).append(",")
                    .append(escapeCsv(assignedStaff)).append("\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "\"\"";
        }
        String clean = value.replace("\r", " ").replace("\n", " ");
        if (clean.startsWith("=") || clean.startsWith("+") || clean.startsWith("-") || clean.startsWith("@")) {
            clean = "'" + clean;
        }
        return "\"" + clean.replace("\"", "\"\"") + "\"";
    }

    public record EligibleStaff(Long id, String name) {
    }
}
