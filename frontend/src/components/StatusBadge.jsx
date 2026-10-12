const statusClasses = {
  SUBMITTED: 'status-pending',
  UNDER_REVIEW: 'status-pending',
  ASSIGNED: 'status-assigned',
  IN_PROGRESS: 'status-progress',
  RESOLVED: 'status-resolved',
  CLOSED: 'status-resolved',
  REJECTED: 'status-rejected',
};

function StatusBadge({ status }) {
  return (
    <span className={`status-badge ${statusClasses[status] || 'status-neutral'}`}>
      {String(status || 'Unknown').replaceAll('_', ' ')}
    </span>
  );
}

export default StatusBadge;
