const palette = {
  SUBMITTED: '#d99b27',
  UNDER_REVIEW: '#e9b85c',
  ASSIGNED: '#8a75c5',
  IN_PROGRESS: '#4382c3',
  RESOLVED: '#32936c',
  CLOSED: '#76b99b',
  REJECTED: '#cb5a5a',
};

const labelFor = (status) => String(status).replaceAll('_', ' ').toLowerCase();

function StatusDonut({ complaints }) {
  const counts = complaints.reduce((result, complaint) => {
    result[complaint.status] = (result[complaint.status] || 0) + 1;
    return result;
  }, {});
  const total = complaints.length;
  let start = 0;
  const parts = Object.entries(counts).map(([status, count]) => {
    const end = start + (count / Math.max(total, 1)) * 100;
    const part = `${palette[status] || '#8291a5'} ${start}% ${end}%`;
    start = end;
    return part;
  });
  const background = parts.length ? `conic-gradient(${parts.join(', ')})` : '#e8edf3';

  return (
    <div className="donut-chart-wrap">
      <div className="status-donut" style={{ background }} role="img" aria-label={`${total} complaints across ${Object.keys(counts).length} statuses`}>
        <div className="donut-center"><strong>{total}</strong><span>total</span></div>
      </div>
      <ul className="donut-legend">
        {Object.entries(counts).length === 0 && <li className="chart-empty">No complaint status data yet.</li>}
        {Object.entries(counts).map(([status, count]) => (
          <li key={status}><span className="legend-dot" style={{ backgroundColor: palette[status] || '#8291a5' }} />
            <span>{labelFor(status)}</span><strong>{count}</strong>
          </li>
        ))}
      </ul>
    </div>
  );
}

export default StatusDonut;
