import { Link } from 'react-router-dom';
import AuthenticatedComplaintImage from './AuthenticatedComplaintImage';
import StatusBadge from './StatusBadge';

function formatDate(value) {
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' }).format(new Date(value));
}

function ComplaintTable({
  complaints,
  detailBase = '/student/complaints',
  emptyMessage,
  showReporter = false,
  showAssignments = false,
  showLocation = false,
  showDescription = false,
  showImages = false,
  renderAssignmentAction,
}) {
  if (complaints.length === 0) {
    return <div className="empty-state"><span className="empty-icon">—</span><p>{emptyMessage || 'No complaints match these filters.'}</p></div>;
  }

  return (
    <div className="table-scroll">
      <table className="data-table">
        <thead>
          <tr>
            <th>Complaint</th>
            {showReporter && <th>Student</th>}
            {showImages && <th>Attachment</th>}
            {showDescription && <th>Description</th>}
            <th>Category</th>
            {showLocation && <th>Location</th>}
            <th>Department</th>
            {showAssignments && <th>Assigned staff</th>}
            {renderAssignmentAction && <th>Assignment</th>}
            <th>Priority</th>
            <th>Submitted</th>
            <th>Status</th>
            <th><span className="visually-hidden">Details</span></th>
          </tr>
        </thead>
        <tbody>
          {complaints.map((complaint) => (
            <tr key={complaint.id}>
              <td>
                <span className="table-primary">{complaint.title}</span>
                <span className="table-secondary">{complaint.complaintId}</span>
              </td>
              {showReporter && (
                <td>
                  <span className="table-primary">{complaint.reporterName || '—'}</span>
                  {complaint.reporterRollNumber && <span className="table-secondary">{complaint.reporterRollNumber}</span>}
                </td>
              )}
              {showImages && (
                <td>
                  {complaint.hasImage
                    ? <AuthenticatedComplaintImage
                        complaintId={complaint.id}
                        complaintCode={complaint.complaintId}
                        className="complaint-image-thumbnail" />
                    : '—'}
                </td>
              )}
              {showDescription && <td>{complaint.description || '—'}</td>}
              <td>{complaint.category}</td>
              {showLocation && <td>{complaint.location || '—'}</td>}
              <td>{complaint.department || '—'}</td>
              {showAssignments && <td>{complaint.assignedStaff?.length ? complaint.assignedStaff.join(', ') : 'Unassigned'}</td>}
              {renderAssignmentAction && <td>{renderAssignmentAction(complaint)}</td>}
              <td><span className={`priority-text priority-${String(complaint.priority).toLowerCase()}`}>{complaint.priority}</span></td>
              <td>{formatDate(complaint.submittedAt)}</td>
              <td><StatusBadge status={complaint.status} /></td>
              <td><Link className="table-action" to={`${detailBase}/${complaint.id}`}>View</Link></td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export default ComplaintTable;
