import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import DashboardLayout from '../components/DashboardLayout';
import api, { getApiErrorMessage } from '../services/api';

const pageSize = 20;
const actionTypes = [
  'COMPLAINT_SUBMITTED',
  'COMPLAINT_IMAGE_UPLOADED',
  'COMMENT_ADDED',
  'RESOLUTION_NOTE_ADDED',
  'WORK_NOTE_ADDED',
  'COMPLAINT_ASSIGNED',
  'COMPLAINT_STATUS_CHANGED',
  'COMPLAINT_RESOLVED',
  'RESOLUTION_PROOF_UPLOADED',
  'SLA_ESCALATED',
  'ACCOUNT_CREATED',
  'ACCOUNT_UPDATED',
  'ACCOUNT_DEACTIVATED',
  'ACCOUNT_ACTIVATED',
  'DEPARTMENT_CREATED',
  'DEPARTMENT_UPDATED',
  'DEPARTMENT_DEACTIVATED',
  'DEPARTMENT_ACTIVATED',
];

function formatDate(value) {
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' })
    .format(new Date(value));
}

function formatState(value) {
  if (!value) return 'Not recorded';
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    return value;
  }
}

function AuditTrailPage() {
  const [records, setRecords] = useState([]);
  const [searchDraft, setSearchDraft] = useState('');
  const [search, setSearch] = useState('');
  const [action, setAction] = useState('');
  const [role, setRole] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [refreshKey, setRefreshKey] = useState(0);
  const [expandedId, setExpandedId] = useState(null);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');
    api.get('/admin/audit', {
      params: {
        page,
        size: pageSize,
        search: search || undefined,
        action: action || undefined,
        role: role || undefined,
        from: from || undefined,
        to: to || undefined,
      },
    }).then(({ data }) => {
      if (!active) return;
      const items = Array.isArray(data?.content) ? data.content : [];
      setRecords(items);
      setTotalPages(typeof data?.totalPages === 'number' ? data.totalPages : 0);
      setTotalElements(typeof data?.totalElements === 'number' ? data.totalElements : items.length);
      setExpandedId(null);
    }).catch((requestError) => {
      if (active) {
        setError(getApiErrorMessage(requestError, 'Audit records could not be loaded. Please retry.'));
      }
    }).finally(() => {
      if (active) setLoading(false);
    });
    return () => { active = false; };
  }, [page, search, action, role, from, to, refreshKey]);

  const submitSearch = (event) => {
    event.preventDefault();
    setPage(0);
    setSearch(searchDraft.trim());
  };

  const [exporting, setExporting] = useState(false);

  const handleExportCsv = async () => {
    setExporting(true);
    try {
      const params = {};
      if (search) params.search = search;
      if (action) params.action = action;
      if (role) params.role = role;
      if (from) params.from = from;
      if (to) params.to = to;

      const response = await api.get('/admin/audit/export.csv', {
        params,
        responseType: 'blob',
      });
      const blob = new Blob([response.data], { type: 'text/csv;charset=utf-8;' });
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', `audit-trail-${new Date().toISOString().slice(0, 10)}.csv`);
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(url);
    } catch (err) {
      alert(getApiErrorMessage(err, 'Audit trail CSV export failed.'));
    } finally {
      setExporting(false);
    }
  };

  const handlePrint = () => {
    window.print();
  };

  const resetFilters = () => {
    setSearchDraft('');
    setSearch('');
    setAction('');
    setRole('');
    setFrom('');
    setTo('');
    setPage(0);
  };

  return (
    <DashboardLayout
      role="ADMIN"
      title="Audit Trail"
      eyebrow="Administration · Accountability"
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
        <h2>CampusCare — Audit Trail Report</h2>
        <p className="report-meta">
          <strong>Generated:</strong> {new Date().toLocaleString()} | <strong>Matching Records:</strong> {totalElements} | <strong>Filters:</strong> {(search || action || role || from || to) ? [search ? `Search: "${search}"` : '', action ? `Action: ${action}` : '', role ? `Role: ${role}` : '', from ? `From: ${from}` : '', to ? `To: ${to}` : ''].filter(Boolean).join(' · ') : 'All'}
        </p>
        <hr />
      </div>
      <section className="panel-card audit-trail-panel">
        <div className="panel-heading">
          <div>
            <p className="eyebrow">Recorded activity</p>
            <h2>Administrative and complaint events</h2>
          </div>
          {!loading && !error && <span className="record-count">{totalElements} records</span>}
        </div>
        <form className="filter-row audit-trail-filters" onSubmit={submitSearch}>
          <label className="search-field">
            <span className="visually-hidden">Search audit records</span>
            <input type="search" placeholder="Search users, actions, complaints or details"
              value={searchDraft} onChange={(event) => setSearchDraft(event.target.value)} />
          </label>
          <button className="button button-secondary button-small" type="submit">Search</button>
          <select aria-label="Filter audit records by action" value={action}
            onChange={(event) => { setPage(0); setAction(event.target.value); }}>
            <option value="">All actions</option>
            {actionTypes.map((item) => <option value={item} key={item}>{item.replaceAll('_', ' ')}</option>)}
          </select>
          <select aria-label="Filter audit records by role" value={role}
            onChange={(event) => { setPage(0); setRole(event.target.value); }}>
            <option value="">All roles</option>
            {['ADMIN', 'STAFF', 'FACULTY', 'STUDENT', 'SYSTEM'].map((item) => (
              <option value={item} key={item}>{item}</option>
            ))}
          </select>
          <label className="audit-date-filter">From
            <input aria-label="Audit records from date" type="date" value={from}
              onChange={(event) => { setPage(0); setFrom(event.target.value); }} />
          </label>
          <label className="audit-date-filter">To
            <input aria-label="Audit records to date" type="date" value={to}
              onChange={(event) => { setPage(0); setTo(event.target.value); }} />
          </label>
          {(search || action || role || from || to) && (
            <button className="button button-quiet button-small" type="button" onClick={resetFilters}>
              Reset filters
            </button>
          )}
        </form>

        {error && <div className="form-alert form-alert-error" role="alert">
          {error}{' '}
          <button className="inline-retry" type="button" onClick={() => setRefreshKey((value) => value + 1)}
            disabled={loading}>Retry</button>
        </div>}
        {loading && <div className="dashboard-loading" role="status">
          <span className="button-spinner" /> Loading audit records…
        </div>}
        {!loading && !error && records.length === 0 && (
          <div className="empty-state">
            <span className="empty-icon">—</span>
            <p>No audit records match these filters.</p>
            <p className="chart-note">Successful supported actions will appear here as they are recorded.</p>
          </div>
        )}
        {!loading && !error && records.length > 0 && (
          <>
            <div className="table-scroll">
              <table className="data-table audit-trail-table">
                <thead>
                  <tr>
                    <th>Date and time</th>
                    <th>User</th>
                    <th>Role</th>
                    <th>Action</th>
                    <th>Related entity</th>
                    <th>Details</th>
                  </tr>
                </thead>
                <tbody>
                  {records.map((record) => (
                    <tr key={record.id}>
                      <td><time dateTime={record.createdAt}>{formatDate(record.createdAt)}</time></td>
                      <td>
                        <span className="table-primary">{record.actorName}</span>
                        <span className="table-secondary">
                          {record.actorUserId ? `User #${record.actorUserId}` : 'Automated system event'}
                        </span>
                      </td>
                      <td><span className={`role-pill role-${record.actorRole.toLowerCase()}`}>{record.actorRole}</span></td>
                      <td>{record.actionType.replaceAll('_', ' ')}</td>
                      <td>
                        {record.complaintId ? (
                          <Link to={`/admin/complaints/${record.complaintId}`}>
                            {record.complaintReference || `Complaint #${record.complaintId}`}
                          </Link>
                        ) : (
                          <span>{record.entityReference || `${record.entityType} #${record.entityId}`}</span>
                        )}
                      </td>
                      <td>
                        <p className="audit-description">{record.description}</p>
                        {(record.beforeState || record.afterState) && (
                          <button className="inline-retry" type="button"
                            aria-expanded={expandedId === record.id}
                            onClick={() => setExpandedId((current) => current === record.id ? null : record.id)}>
                            {expandedId === record.id ? 'Hide changes' : 'View changes'}
                          </button>
                        )}
                        {expandedId === record.id && (
                          <div className="audit-change-details">
                            <div><strong>Before</strong><pre>{formatState(record.beforeState)}</pre></div>
                            <div><strong>After</strong><pre>{formatState(record.afterState)}</pre></div>
                          </div>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            {totalPages > 1 && (
              <nav className="complaint-pagination" aria-label="Audit trail pages">
                <button className="button button-quiet button-small" type="button"
                  onClick={() => setPage((current) => current - 1)} disabled={page === 0 || loading}>
                  Previous
                </button>
                <span>Page {page + 1} of {totalPages}</span>
                <button className="button button-quiet button-small" type="button"
                  onClick={() => setPage((current) => current + 1)}
                  disabled={page + 1 >= totalPages || loading}>Next</button>
              </nav>
            )}
          </>
        )}
      </section>
    </DashboardLayout>
  );
}

export default AuditTrailPage;
