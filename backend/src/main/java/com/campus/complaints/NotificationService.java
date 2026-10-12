package com.campus.complaints;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final ComplaintAssignmentRepository assignmentRepository;

    public NotificationService(
            NotificationRepository notificationRepository,
            UserRepository userRepository,
            ComplaintAssignmentRepository assignmentRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.assignmentRepository = assignmentRepository;
    }

    @Transactional(readOnly = true)
    public NotificationPageResponse list(Long recipientId, int page, int size) {
        Page<Notification> notifications = notificationRepository.findByRecipient_Id(
                recipientId,
                PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        return NotificationPageResponse.from(notifications);
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long recipientId) {
        return notificationRepository.countByRecipient_IdAndReadAtIsNull(recipientId);
    }

    @Transactional
    public NotificationResponse markRead(Long recipientId, Long notificationId) {
        Notification notification = notificationRepository.findByIdAndRecipient_Id(notificationId, recipientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification was not found."));
        notification.markRead();
        return NotificationResponse.from(notification);
    }

    @Transactional
    public int markAllRead(Long recipientId, LocalDateTime readAt) {
        return notificationRepository.markAllRead(recipientId, readAt);
    }

    @Transactional
    public void complaintSubmitted(Complaint complaint) {
        String eventKey = "complaint:" + complaint.getId() + ":submitted";
        create(complaint.getReporter(), complaint, "Complaint submitted",
                "Your complaint " + complaint.getComplaintCode() + " was submitted successfully.",
                "COMPLAINT_SUBMITTED", eventKey);
        userRepository.findByRoleAndActiveTrue(UserRole.ADMIN).forEach(admin ->
                create(admin, complaint, "New complaint submitted",
                        "A new complaint " + complaint.getComplaintCode() + " requires review.",
                        "COMPLAINT_SUBMITTED", eventKey + ":admin"));
        notifyFacultyMonitoring(complaint, eventKey + ":faculty", "New student complaint",
                "A new student complaint " + complaint.getComplaintCode() + " is available for monitoring.",
                "COMPLAINT_SUBMITTED");
    }

    @Transactional
    public void slaEscalated(Complaint complaint, int escalationLevel, String reason) {
        String eventKey = "sla:" + complaint.getId() + ":level:" + escalationLevel;
        userRepository.findByRoleAndActiveTrue(UserRole.ADMIN).forEach(admin ->
                create(admin, complaint, "Complaint SLA escalation",
                        complaint.getComplaintCode() + " reached escalation level " + escalationLevel
                                + ". " + reason,
                        "SLA_ESCALATION", eventKey + ":admin:" + admin.getId()));
        assignmentRepository.findByComplaint_IdAndUnassignedAtIsNullOrderByAssignedAtAsc(complaint.getId())
                .forEach(assignment -> create(assignment.getAssignee(), complaint,
                        "Assigned complaint SLA escalation",
                        "Your assigned complaint " + complaint.getComplaintCode() + " reached escalation level "
                                + escalationLevel + ". " + reason,
                        "SLA_ESCALATION", eventKey + ":staff:" + assignment.getAssignee().getId()));
    }

    @Transactional
    public void slaDueSoon(Complaint complaint) {
        String eventKey = "sla:" + complaint.getId() + ":due-soon";
        userRepository.findByRoleAndActiveTrue(UserRole.ADMIN).forEach(admin ->
                create(admin, complaint, "Complaint deadline approaching",
                        "Complaint " + complaint.getComplaintCode() + " is approaching its resolution deadline.",
                        "SLA_DUE_SOON", eventKey + ":admin:" + admin.getId()));
        assignmentRepository.findByComplaint_IdAndUnassignedAtIsNullOrderByAssignedAtAsc(complaint.getId())
                .forEach(assignment -> create(assignment.getAssignee(), complaint,
                        "Assigned complaint deadline approaching",
                        "Your assigned complaint " + complaint.getComplaintCode() + " is approaching its resolution deadline.",
                        "SLA_DUE_SOON", eventKey + ":staff:" + assignment.getAssignee().getId()));
    }

    @Transactional
    public void assignmentChanged(
            Complaint complaint,
            ComplaintAssignment assignment,
            List<ComplaintAssignment> previousAssignments) {
        User assignedStaff = assignment.getAssignee();
        String eventKey = "assignment:" + assignment.getId();
        create(assignedStaff, complaint, "Complaint assigned to you",
                "Complaint " + complaint.getComplaintCode() + " has been assigned to you.",
                "COMPLAINT_ASSIGNED", eventKey + ":assigned");
        previousAssignments.stream()
                .filter(previous -> !previous.getAssignee().getId().equals(assignedStaff.getId()))
                .forEach(previous -> create(previous.getAssignee(), complaint, "Complaint assignment changed",
                        "Complaint " + complaint.getComplaintCode() + " is no longer assigned to you.",
                        "COMPLAINT_UNASSIGNED", eventKey + ":unassigned:" + previous.getId()));
        create(complaint.getReporter(), complaint, "Complaint assignment updated",
                "The assignment for complaint " + complaint.getComplaintCode() + " has been updated.",
                "COMPLAINT_ASSIGNED", eventKey + ":reporter");
        userRepository.findByRoleAndActiveTrue(UserRole.ADMIN).forEach(admin ->
                create(admin, complaint, "Complaint assignment updated",
                        "Complaint " + complaint.getComplaintCode() + " was assigned to "
                                + assignedStaff.getFullName() + ".",
                        "COMPLAINT_ASSIGNED", eventKey + ":admin"));
        notifyFacultyMonitoring(complaint, eventKey + ":faculty", "Student complaint assignment updated",
                "The assignment for student complaint " + complaint.getComplaintCode() + " was updated.",
                "COMPLAINT_ASSIGNED");
    }

    @Transactional
    public void statusChanged(Complaint complaint, ComplaintStatusHistory history) {
        ComplaintStatus status = history.getNewStatus();
        String eventKey = "status:" + history.getId();
        String message = "Complaint " + complaint.getComplaintCode() + " status changed to "
                + status.name().replace('_', ' ').toLowerCase() + ".";
        create(complaint.getReporter(), complaint,
                status == ComplaintStatus.RESOLVED ? "Complaint resolved" : "Complaint status updated",
                message,
                status == ComplaintStatus.RESOLVED ? "COMPLAINT_RESOLVED" : "COMPLAINT_STATUS_UPDATED",
                eventKey);
        userRepository.findByRoleAndActiveTrue(UserRole.ADMIN).forEach(admin ->
                create(admin, complaint,
                        status == ComplaintStatus.RESOLVED ? "Complaint resolved" : "Complaint status updated",
                        message,
                        status == ComplaintStatus.RESOLVED ? "COMPLAINT_RESOLVED" : "COMPLAINT_STATUS_UPDATED",
                        eventKey + ":admin"));
        assignmentRepository.findByComplaint_IdAndUnassignedAtIsNullOrderByAssignedAtAsc(complaint.getId())
                .forEach(assignment ->
                        create(assignment.getAssignee(), complaint,
                                status == ComplaintStatus.RESOLVED
                                        ? "Assigned complaint resolved" : "Assigned complaint updated",
                                message,
                                status == ComplaintStatus.RESOLVED
                                        ? "COMPLAINT_RESOLVED" : "COMPLAINT_STATUS_UPDATED",
                                eventKey + ":staff:" + assignment.getAssignee().getId()));
        notifyFacultyMonitoring(complaint, eventKey + ":faculty",
                status == ComplaintStatus.RESOLVED ? "Student complaint resolved" : "Student complaint status updated",
                message, status == ComplaintStatus.RESOLVED ? "COMPLAINT_RESOLVED" : "COMPLAINT_STATUS_UPDATED");
    }

    @Transactional
    public void commentAdded(Complaint complaint, ComplaintWorkNote comment) {
        User author = comment.getAuthor();
        String complaintReference = complaint.getComplaintCode();
        String eventKey = "comment:" + comment.getId();

        switch (author.getRole()) {
            case STUDENT -> {
                userRepository.findByRoleAndActiveTrue(UserRole.ADMIN).forEach(admin ->
                        createCommentNotification(admin, author, complaint, eventKey,
                                "New comment on complaint",
                                "A new comment was added to complaint " + complaintReference + "."));
                assignmentRepository.findByComplaint_IdAndUnassignedAtIsNullOrderByAssignedAtAsc(complaint.getId())
                        .forEach(assignment -> createCommentNotification(
                                assignment.getAssignee(), author, complaint, eventKey,
                                "New comment on an assigned complaint",
                                "A new comment was added to assigned complaint " + complaintReference + "."));
                notifyFacultyOfStudentComment(complaint, author, eventKey);
            }
            case STAFF -> {
                createCommentNotification(complaint.getReporter(), author, complaint, eventKey,
                        "New comment on your complaint",
                        "A new comment was added to your complaint " + complaintReference + ".");
                notifyActiveAdministrators(author, complaint, eventKey);
                notifyFacultyOfStudentComment(complaint, author, eventKey);
            }
            case ADMIN -> {
                createCommentNotification(complaint.getReporter(), author, complaint, eventKey,
                        "New comment on your complaint",
                        "A new comment was added to your complaint " + complaintReference + ".");
                assignmentRepository.findByComplaint_IdAndUnassignedAtIsNullOrderByAssignedAtAsc(complaint.getId())
                        .forEach(assignment -> createCommentNotification(
                                assignment.getAssignee(), author, complaint, eventKey,
                                "New comment on an assigned complaint",
                                "A new comment was added to assigned complaint " + complaintReference + "."));
                notifyFacultyOfStudentComment(complaint, author, eventKey);
            }
            case FACULTY -> {
                // Faculty cannot add comments under the existing complaint permissions.
            }
        }
    }

    private void notifyActiveAdministrators(User author, Complaint complaint, String eventKey) {
        userRepository.findByRoleAndActiveTrue(UserRole.ADMIN).forEach(admin ->
                createCommentNotification(admin, author, complaint, eventKey,
                        "New comment on complaint",
                        "A new comment was added to complaint " + complaint.getComplaintCode() + "."));
    }

    private void notifyFacultyOfStudentComment(Complaint complaint, User author, String eventKey) {
        if (complaint.getReporter().getRole() != UserRole.STUDENT) {
            return;
        }
        userRepository.findByRoleAndActiveTrue(UserRole.FACULTY).forEach(faculty ->
                createCommentNotification(faculty, author, complaint, eventKey,
                        "New comment on a monitored complaint",
                        "A new comment was added to student complaint " + complaint.getComplaintCode() + "."));
    }

    private void createCommentNotification(
            User recipient, User author, Complaint complaint, String eventKey, String title, String message) {
        if (recipient.getId().equals(author.getId())) {
            return;
        }
        create(recipient, complaint, title, message, "COMPLAINT_COMMENT",
                eventKey + ":recipient:" + recipient.getId());
    }

    private void notifyFacultyMonitoring(
            Complaint complaint, String eventKey, String title, String message, String type) {
        if (complaint.getReporter().getRole() != UserRole.STUDENT) {
            return;
        }
        userRepository.findByRoleAndActiveTrue(UserRole.FACULTY).forEach(faculty ->
                create(faculty, complaint, title, message, type, eventKey));
    }

    private void create(
            User recipient, Complaint complaint, String title, String message, String type, String eventKey) {
        if (!notificationRepository.existsByRecipient_IdAndEventKey(recipient.getId(), eventKey)) {
            notificationRepository.save(new Notification(recipient, complaint, title, message, type, eventKey));
        }
    }
}
