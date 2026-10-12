import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import AppIcon from '../components/AppIcon';
import ComplaintTable from '../components/ComplaintTable';
import DashboardLayout from '../components/DashboardLayout';
import { useAuth } from '../context/AuthContext';
import api from '../services/api';
import { complaintStatuses, filterAndSortComplaints } from '../utils/complaints';

const pageSize = 10;

function MyComplaintsPage() {
  const { user } = useAuth();
  const role = user?.role || 'STUDENT';
  const isFaculty = role === 'FACULTY';
  const detailBase = isFaculty ? '/faculty/complaints' : '/student/complaints';
  const submitPath = isFaculty ? '/faculty/complaints/new' : '/student/complaints/new';
  const [complaints, setComplaints] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [query, setQuery] = useState('');
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(0);

  const loadComplaints = async () => {
    setLoading(true);
    setError('');
    try {
      const { data } = await api.get('/complaints/my');
      setComplaints(data);
    } catch (requestError) {
      setError(requestError.response?.data?.message || 'Your complaints could not be loaded.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { loadComplaints(); }, []);

  const filtered = useMemo(
    () => filterAndSortComplaints(complaints, { query, status }),
    [complaints, query, status],
  );
  const pageCount = Math.ceil(filtered.length / pageSize);
  const currentPage = Math.min(page, Math.max(0, pageCount - 1));
  const visibleComplaints = filtered.slice(currentPage * pageSize, (currentPage + 1) * pageSize);

  return (
    <DashboardLayout role={role} title="My complaints" eyebrow="Your reports"
      actions={<Link className="button button-primary button-small" to={submitPath}><AppIcon name="plus" size={17} /> Submit complaint</Link>}>
      {error && <div className="form-alert form-alert-error" role="alert">{error} <button className="inline-retry" type="button" onClick={loadComplaints} disabled={loading}>Retry</button></div>}
      {loading && <div className="dashboard-loading" role="status"><span className="button-spinner" /> Loading your complaints…</div>}
      {!loading && !error && (
        <section className="panel-card">
          <div className="panel-heading"><div><p className="eyebrow">Secure personal view</p><h2>Complaint history</h2></div><span className="record-count">{visibleComplaints.length} of {filtered.length} matching</span></div>
          <div className="filter-row">
            <label className="search-field"><span className="visually-hidden">Search complaints</span><input type="search" placeholder="Search by complaint ID or title" value={query} onChange={(event) => { setPage(0); setQuery(event.target.value); }} /></label>
            <select aria-label="Filter complaints by status" value={status} onChange={(event) => { setPage(0); setStatus(event.target.value); }}><option value="">All statuses</option>{complaintStatuses.map((item) => <option value={item} key={item}>{item.replaceAll('_', ' ')}</option>)}</select>
            {(query || status) && <button className="button button-quiet button-small" type="button" onClick={() => { setPage(0); setQuery(''); setStatus(''); }}>Reset filters</button>}
          </div>
          {complaints.length === 0 ? (
            <div className="empty-state">
              <span className="empty-icon">—</span>
              <p>You have not submitted any complaints yet.</p>
              <Link className="button button-primary button-small" to={submitPath}>Submit Complaint</Link>
            </div>
          ) : (
            <ComplaintTable complaints={visibleComplaints} detailBase={detailBase}
              emptyMessage="No complaints match your search or status filter." />
          )}
          {pageCount > 1 && (
            <nav className="complaint-pagination" aria-label="Complaint pages">
              <button className="button button-quiet button-small" type="button" onClick={() => setPage(currentPage - 1)} disabled={currentPage === 0}>Previous</button>
              <span>Page {currentPage + 1} of {pageCount}</span>
              <button className="button button-quiet button-small" type="button" onClick={() => setPage(currentPage + 1)} disabled={currentPage >= pageCount - 1}>Next</button>
            </nav>
          )}
        </section>
      )}
    </DashboardLayout>
  );
}

export default MyComplaintsPage;
