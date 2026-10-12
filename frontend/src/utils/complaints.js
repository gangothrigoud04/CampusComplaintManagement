export const complaintStatuses = Object.freeze([
  'SUBMITTED',
  'UNDER_REVIEW',
  'ASSIGNED',
  'IN_PROGRESS',
  'RESOLVED',
  'CLOSED',
  'REJECTED',
]);

const pendingStatuses = new Set(['SUBMITTED', 'UNDER_REVIEW']);
const inProgressStatuses = new Set(['ASSIGNED', 'IN_PROGRESS']);
const resolvedStatuses = new Set(['RESOLVED', 'CLOSED']);

export function filterAndSortComplaints(complaints, { query = '', status = '' } = {}) {
  const normalizedQuery = query.trim().toLocaleLowerCase();
  return complaints
    .filter((complaint) => {
      const matchesSearch = !normalizedQuery
        || `${complaint.complaintId} ${complaint.title}`.toLocaleLowerCase().includes(normalizedQuery);
      return matchesSearch && (!status || complaint.status === status);
    })
    .sort((first, second) => new Date(second.submittedAt) - new Date(first.submittedAt));
}

export function summarizeComplaints(complaints) {
  return {
    total: complaints.length,
    pending: complaints.filter((complaint) => pendingStatuses.has(complaint.status)).length,
    inProgress: complaints.filter((complaint) => inProgressStatuses.has(complaint.status)).length,
    resolved: complaints.filter((complaint) => resolvedStatuses.has(complaint.status)).length,
  };
}

export function filterAdminComplaints(complaints, filters = {}) {
  return filterAndSortComplaints(complaints, filters)
    .filter((complaint) => (!filters.category || complaint.category === filters.category)
      && (!filters.priority || complaint.priority === filters.priority)
      && (!filters.department || complaint.department === filters.department));
}

export function summarizeAdminComplaints(complaints) {
  return {
    total: complaints.length,
    submitted: complaints.filter((complaint) => complaint.status === 'SUBMITTED').length,
    inProgress: complaints.filter((complaint) => ['ASSIGNED', 'IN_PROGRESS'].includes(complaint.status)).length,
    resolved: complaints.filter((complaint) => ['RESOLVED', 'CLOSED'].includes(complaint.status)).length,
  };
}

export function summarizeStaffComplaints(complaints) {
  return {
    total: complaints.length,
    awaitingWork: complaints.filter(
      (complaint) => ['SUBMITTED', 'UNDER_REVIEW', 'ASSIGNED'].includes(complaint.status),
    ).length,
    inProgress: complaints.filter((complaint) => complaint.status === 'IN_PROGRESS').length,
    resolved: complaints.filter((complaint) => complaint.status === 'RESOLVED').length,
  };
}
