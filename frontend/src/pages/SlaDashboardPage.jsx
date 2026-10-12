import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import AppIcon from '../components/AppIcon';
import DashboardLayout from '../components/DashboardLayout';
import { useAuth } from '../context/AuthContext';
import api, { getApiErrorMessage } from '../services/api';

const pageSize = 20;
const slaStatuses = ['ON_TRACK', 'DUE_SOON', 'OVERDUE', 'RESOLVED_WITHIN_SLA', 'RESOLVED_AFTER_SLA'];

function formatDate(value) {
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value));
}

function formatDuration(seconds) {
  const hours = Math.floor(Math.abs(seconds) / 3600);
  const days = Math.floor(hours / 24);
  const remainderHours = hours % 24;
  if (days > 0) return `${days}d ${remainderHours}h`;
  if (hours > 0) return `${hours}h`;
  return `${Math.floor(Math.abs(seconds) / 60)}m`;
}

function SlaDashboardPage() {
  const { user } = useAuth();
  const [data, setData] = useState(null);
  const [staff, setStaff] = useState([]);
  const [staffError, setStaffError] = useState('');
  const [status, setStatus] = useState('');
  const [priority, setPriority] = useState('');
  const [escalationLevel, setEscalationLevel] = useState('');
  const [assignedStaffId, setAssignedStaffId] = useState('');
  const [minimumOverdueHours, setMinimumOverdueHours] = useState('');
  const [deadlineFrom, setDeadlineFrom] = useState('');
  const [deadlineTo, setDeadlineTo] = useState('');
  const [page, setPage] = useState(0);
  const [refreshKey, setRefreshKey] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const filters = { status, priority, escalationLevel, assignedStaffId, minimumOverdueHours, deadlineFrom, deadlineTo };
  const hasAdminSession = user?.role === 'ADMIN'
    && Boolean(window.sessionStorage.getItem('campusAccessToken')?.trim());

  useEffect(() => {
    if (!hasAdminSession) return undefined;
    const controller = new AbortController();
    let active = true;
    const requestId = window.setTimeout(() => {
      api.get('/admin/users', { signal: controller.signal })
        .then(({ data: users }) => {
          if (active) setStaff(users.filter((account) => account.role === 'STAFF' && account.active));
        })
        .catch((requestError) => {
          if (active) {
            setStaffError(getApiErrorMessage(requestError, 'Staff filter options could not be loaded.'));
          }
        });
    }, 0);
    return () => {
      active = false;
      window.clearTimeout(requestId);
      controller.abort();
    };
  }, [hasAdminSession]);

  useEffect(() => {
    if (!hasAdminSession) {
      setLoading(false);
      return undefined;
    }
    const controller = new AbortController();
    let active = true;
    setLoading(true);
    setError('');
    const requestId = window.setTimeout(() => {
      api.get('/admin/sla', {
        signal: controller.signal,
        params: {
          page,
          size: pageSize,
          status: status || undefined,
          priority: priority || undefined,
          escalationLevel: escalationLevel === '' ? undefined : escalationLevel,
          assignedStaffId: assignedStaffId || undefined,
          minimumOverdueHours: minimumOverdueHours === '' ? undefined : minimumOverdueHours,
          deadlineFrom: deadlineFrom || undefined,
          deadlineTo: deadlineTo || undefined,
        },
      }).then(({ data: response }) => {
        if (active) setData(response);
      }).catch((requestError) => {
        if (active) setError(getApiErrorMessage(requestError, 'SLA data could not be loaded. Please retry.'));
      }).finally(() => {
        if (active) setLoading(false);
      });
    }, 0);
    return () => {
      active = false;
      window.clearTimeout(requestId);
      controller.abort();
    };
  }, [page, filters.status, filters.priority, filters.escalationLevel, filters.assignedStaffId,
    filters.minimumOverdueHours, filters.deadlineFrom, filters.deadlineTo, refreshKey, hasAdminSession]);

  const [exporting, setExporting] = useState(false);

  const handleExportCsv = async () => {
    setExporting(true);
    try {
      const params = {};
      if (status) params.status = status;
      if (priority) params.priority = priority;
      if (escalationLevel !== '') params.escalationLevel = escalationLevel;
      if (assignedStaffId) params.assignedStaffId = assignedStaffId;
      if (minimumOverdueHours !== '') params.minimumOverdueHours = minimumOverdueHours;
      if (deadlineFrom) params.deadlineFrom = deadlineFrom;
      if (deadlineTo) params.deadlineTo = deadlineTo;

      const response = await api.get('/admin/sla/export.csv', {
        params,
        responseType: 'blob',
      });
      const blob = new Blob([response.data], { type: 'text/csv;charset=utf-8;' });
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', `sla-report-${new Date().toISOString().slice(0, 10)}.csv`);
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(url);
    } catch (err) {
      alert(err.response?.data?.message || 'SLA CSV export failed.');
    } finally {
      setExporting(false);
    }
  };

  const handlePrint = () => {
    window.print();
  };

  const resetFilters = () => {
    setStatus('');
    setPriority('');
    setEscalationLevel('');
    setAssignedStaffId('');
    setMinimumOverdueHours('');
    setDeadlineFrom('');
    setDeadlineTo('');
    setPage(0);
  };
  const pageData = data?.complaints;
  const pageCount = pageData?.totalPages || 0;

  return (
    <DashboardLayout
      role="ADMIN"
      title="SLA Overview"
      eyebrow="Administration · Service levels"
      actions={
        <div className="analytics-header-actions print-hidden">
          <button className="button button-secondary button-small" type="button"
            onClick={() => setRefreshKey((value) => value + 1)} disabled={loading}>
            {loading ? 'Refreshing…' : 'Refresh'}
          </button>
          <button className="button button-secondary button-small" type="button"
            onClick={handleExportCsv} disabled={loading || exporting}>
            {exporting ? 'Exporting…' : 'Export CSV'}
          </button>
          <button className="button button-primary button-small" type="button" onClick={handlePrint}>
            Print Report
          </button>
        </div>
      }
    >
      <div className="print-only report-print-header">
        <h2>CampusCare — SLA & Escalation Report</h2>
        <p className="report-meta">
          <strong>Generated:</strong> {new Date().toLocaleString()} | <strong>Matching Records:</strong> {pageData?.totalElements ?? 0} | <strong>Filters:</strong> {Object.values(filters).some(Boolean) ? [status ? `Status: ${status}` : '', priority ? `Priority: ${priority}` : '', escalationLevel !== '' ? `Escalation: Level ${escalationLevel}` : '', assignedStaffId ? `Staff: #${assignedStaffId}` : '', minimumOverdueHours !== '' ? `Overdue >= ${minimumOverdueHours}h` : '', deadlineFrom ? `From: ${deadlineFrom}` : '', deadlineTo ? `To: ${deadlineTo}` : ''].filter(Boolean).join(' · ') : 'All'}
        </p>
        <hr />
      </div>
      {error && <div className="form-alert form-alert-error" role="alert">
        {error}{' '}
        <button className="inline-retry" type="button" onClick={() => setRefreshKey((value) => value + 1)}
          disabled={loading}>Retry</button>
      </div>}
      {staffError && <div className="form-alert form-alert-error" role="alert">{staffError}</div>}
      {loading && <div className="dashboard-loading" role="status"><span className="button-spinner" /> Loading SLA data…</div>}
      {!loading && !error && data && (
        <>
          <div className="metric-grid sla-metric-grid">
            {[
              ['Within SLA', data.withinSla, 'chart', ''],
              ['Due soon', data.dueSoon, 'clock', 'amber'],
              ['Overdue', data.overdue, 'clock', 'red'],
              ['Resolved within SLA', data.resolvedWithinSla, 'check', 'green'],
              ['Resolved after SLA', data.resolvedAfterSla, 'check', 'violet'],
            ].map(([label, value, icon, tone]) => (
              <article className={`metric-card ${tone ? `metric-${tone}` : ''}`} key={label}>
                <div className="metric-copy"><span>{label}</span><strong>{value}</strong></div>
                <span className="metric-icon"><AppIcon name={icon} size={18} /></span>
              </article>
            ))}
          </div>

          <section className="panel-card">
            <div className="panel-heading"><div><p className="eyebrow">Escalation distribution</p><h2>Current levels</h2></div></div>
            <div className="sla-escalation-levels">
              {data.escalations.map((item) => <span key={item.level}>Level {item.level}<strong>{item.count}</strong></span>)}
            </div>
          </section>

          <section className="panel-card">
            <div className="panel-heading">
              <div><p className="eyebrow">Live complaint deadlines</p><h2>SLA register</h2></div>
              <span className="record-count">{pageData.totalElements} matching complaints</span>
            </div>
            <div className="filter-row sla-filters">
              <select aria-label="Filter complaints by SLA status" value={status}
                onChange={(event) => { setPage(0); setStatus(event.target.value); }}>
                <option value="">All SLA statuses</option>
                {slaStatuses.map((item) => <option value={item} key={item}>{item.replaceAll('_', ' ')}</option>)}
              </select>
              <select aria-label="Filter complaints by priority" value={priority}
                onChange={(event) => { setPage(0); setPriority(event.target.value); }}>
                <option value="">All priorities</option>
                {['URGENT', 'HIGH', 'MEDIUM', 'LOW'].map((item) => <option value={item} key={item}>{item}</option>)}
              </select>
              <select aria-label="Filter by escalation level" value={escalationLevel}
                onChange={(event) => { setPage(0); setEscalationLevel(event.target.value); }}>
                <option value="">All escalation levels</option>
                {[0, 1, 2, 3].map((item) => <option value={item} key={item}>Level {item}</option>)}
              </select>
              <select aria-label="Filter by assigned staff member" value={assignedStaffId}
                onChange={(event) => { setPage(0); setAssignedStaffId(event.target.value); }}>
                <option value="">All assigned staff</option>
                {staff.map((user) => <option value={user.id} key={user.id}>{user.name}</option>)}
              </select>
              <label className="sla-duration-filter">Overdue ≥ hours
                <input aria-label="Minimum overdue hours" type="number" min="0" value={minimumOverdueHours}
                  onChange={(event) => { setPage(0); setMinimumOverdueHours(event.target.value); }} />
              </label>
              <label className="sla-date-filter">Deadline from
                <input aria-label="Deadline start date" type="date" value={deadlineFrom}
                  onChange={(event) => { setPage(0); setDeadlineFrom(event.target.value); }} />
              </label>
              <label className="sla-date-filter">Deadline to
                <input aria-label="Deadline end date" type="date" value={deadlineTo}
                  onChange={(event) => { setPage(0); setDeadlineTo(event.target.value); }} />
              </label>
              {Object.values(filters).some(Boolean) && (
                <button className="button button-quiet button-small" type="button" onClick={resetFilters}>Reset filters</button>
              )}
            </div>
            {pageData.content.length === 0 ? (
              <div className="empty-state"><span className="empty-icon">—</span><p>No complaints match these SLA filters.</p></div>
            ) : (
              <>
                <div className="table-scroll">
                  <table className="data-table">
                    <thead><tr>
                      <th>Complaint</th><th>Priority</th><th>Assigned staff</th><th>Deadline</th>
                      <th>Time remaining / overdue</th><th>SLA status</th><th>Escalation</th>
                    </tr></thead>
                    <tbody>{pageData.content.map((complaint) => (
                      <tr key={complaint.id}>
                        <td><Link to={`/admin/complaints/${complaint.id}`}>{complaint.complaintReference}</Link>
                          <span className="table-secondary">{complaint.title}</span></td>
                        <td>{complaint.priority}</td>
                        <td>{complaint.assignedStaff || 'Unassigned'}</td>
                        <td><time dateTime={complaint.deadlineAt}>{formatDate(complaint.deadlineAt)}</time></td>
                        <td>{complaint.status.startsWith('RESOLVED')
                          ? 'Stopped'
                          : complaint.secondsUntilDeadline < 0
                            ? `${formatDuration(complaint.secondsUntilDeadline)} overdue`
                            : formatDuration(complaint.secondsUntilDeadline)}</td>
                        <td><span className={`sla-status sla-${complaint.status.toLowerCase().replaceAll('_', '-')}`}>
                          {complaint.status.replaceAll('_', ' ')}</span></td>
                        <td>
                          <span>Level {complaint.escalationLevel}</span>
                          {complaint.escalationHistory.length > 0 && (
                            <span className="table-secondary">
                              {complaint.escalationHistory.map((item) => `L${item.level} · ${formatDate(item.escalatedAt)} · ${item.reason}`).join('; ')}
                            </span>
                          )}
                        </td>
                      </tr>
                    ))}</tbody>
                  </table>
                </div>
                <div className="sla-pagination">
                  <span>Page {page + 1} of {Math.max(pageCount, 1)}</span>
                  <div>
                    <button className="button button-secondary button-small" type="button"
                      onClick={() => setPage((current) => Math.max(0, current - 1))} disabled={page === 0}>Previous</button>
                    <button className="button button-secondary button-small" type="button"
                      onClick={() => setPage((current) => current + 1)} disabled={page + 1 >= pageCount}>Next</button>
                  </div>
                </div>
              </>
            )}
          </section>
        </>
      )}
    </DashboardLayout>
  );
}

export default SlaDashboardPage;
