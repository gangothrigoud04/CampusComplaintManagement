import { useEffect, useMemo, useState } from 'react';
import ComplaintTable from '../components/ComplaintTable';
import ComplaintAssignmentEditor from '../components/ComplaintAssignmentEditor';
import DashboardLayout from '../components/DashboardLayout';
import api from '../services/api';
import { complaintStatuses, filterAdminComplaints } from '../utils/complaints';

const pageSize = 10;

function AdminComplaintsPage() {
  const [complaints, setComplaints] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [departments, setDepartments] = useState([]);
  const [departmentsLoading, setDepartmentsLoading] = useState(true);
  const [departmentsError, setDepartmentsError] = useState('');
  const [query, setQuery] = useState('');
  const [status, setStatus] = useState('');
  const [category, setCategory] = useState('');
  const [priority, setPriority] = useState('');
  const [department, setDepartment] = useState('');
  const [page, setPage] = useState(0);
  const [exporting, setExporting] = useState(false);

  const handleExportCsv = async () => {
    setExporting(true);
    try {
      const params = {};
      if (status) params.status = status;
      if (category) params.category = category;
      if (priority) params.priority = priority;
      if (department) params.department = department;
      if (query) params.search = query;

      const response = await api.get('/complaints/export.csv', {
        params,
        responseType: 'blob',
      });
      const blob = new Blob([response.data], { type: 'text/csv;charset=utf-8;' });
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', `complaints-register-${new Date().toISOString().slice(0, 10)}.csv`);
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(url);
    } catch (err) {
      alert(err.response?.data?.message || 'Complaint CSV export failed.');
    } finally {
      setExporting(false);
    }
  };

  const handlePrint = () => {
    window.print();
  };

  const loadComplaints = async () => {
    setLoading(true);
    setError('');
    setDepartmentsLoading(true);
    setDepartmentsError('');
    const [complaintResult, departmentResult] = await Promise.allSettled([
      api.get('/complaints'),
      api.get('/admin/departments'),
    ]);
    if (complaintResult.status === 'fulfilled' && Array.isArray(complaintResult.value.data)) {
      setComplaints(complaintResult.value.data);
    } else {
      const requestError = complaintResult.status === 'rejected' ? complaintResult.reason : null;
      setError(requestError?.response?.data?.message || 'Complaints could not be loaded. Please retry.');
    }
    if (departmentResult.status === 'fulfilled' && Array.isArray(departmentResult.value.data)) {
      setDepartments(departmentResult.value.data.filter((department) => department.active));
    } else {
      const requestError = departmentResult.status === 'rejected' ? departmentResult.reason : null;
      setDepartmentsError(requestError?.response?.data?.message || 'Active departments could not be loaded.');
    }
    setDepartmentsLoading(false);
    setLoading(false);
  };

  useEffect(() => { loadComplaints(); }, []);

  const categories = useMemo(
    () => [...new Set(complaints.map((item) => item.category).filter(Boolean))].sort(),
    [complaints],
  );
  const complaintDepartments = useMemo(
    () => [...new Set(complaints.map((item) => item.department).filter(Boolean))].sort(),
    [complaints],
  );
  const filtered = useMemo(() => filterAdminComplaints(complaints, {
    query, status, category, priority, department,
  }),
  [complaints, query, status, category, priority, department]);
  const pageCount = Math.ceil(filtered.length / pageSize);
  const currentPage = Math.min(page, Math.max(0, pageCount - 1));
  const visibleComplaints = filtered.slice(currentPage * pageSize, (currentPage + 1) * pageSize);
  const hasFilters = query || status || category || priority || department;
  const resetFilters = () => {
    setQuery('');
    setStatus('');
    setCategory('');
    setPriority('');
    setDepartment('');
    setPage(0);
  };
  const updateAssignment = (updated) => {
    setComplaints((current) => current.map((complaint) => complaint.id === updated.id ? updated : complaint));
  };

  return (
    <DashboardLayout
      role="ADMIN"
      title="All complaints"
      eyebrow="Complaint management"
      actions={
        <div className="analytics-header-actions print-hidden">
          <button className="button button-secondary button-small" type="button" onClick={loadComplaints} disabled={loading}>
            {loading ? 'Refreshing…' : 'Refresh'}
          </button>
          <button className="button button-secondary button-small" type="button" onClick={handleExportCsv} disabled={loading || exporting}>
            {exporting ? 'Exporting…' : 'Export CSV'}
          </button>
          <button className="button button-primary button-small" type="button" onClick={handlePrint}>
            Print Report
          </button>
        </div>
      }
    >
      <div className="print-only report-print-header">
        <h2>CampusCare — Complaint Register Report</h2>
        <p className="report-meta">
          <strong>Generated:</strong> {new Date().toLocaleString()} | <strong>Matching Records:</strong> {filtered.length} of {complaints.length} | <strong>Filters:</strong> {hasFilters ? [query ? `Search: "${query}"` : '', status ? `Status: ${status}` : '', category ? `Category: ${category}` : '', priority ? `Priority: ${priority}` : '', department ? `Dept: ${department}` : ''].filter(Boolean).join(' · ') : 'All'}
        </p>
        <hr />
      </div>
      {error && <div className="form-alert form-alert-error" role="alert">
        {error}{' '}
        <button className="inline-retry" type="button" onClick={loadComplaints} disabled={loading}>Retry</button>
      </div>}
      {loading && <div className="dashboard-loading" role="status"><span className="button-spinner" /> Loading complaints…</div>}
      {!loading && !error && (
        <section className="panel-card">
          <div className="panel-heading">
            <div><p className="eyebrow">Complaint register</p><h2>Complaint records</h2></div>
            <span className="record-count">{visibleComplaints.length} of {filtered.length} matching · {complaints.length} total</span>
          </div>
          <div className="filter-row admin-complaint-filters">
            <label className="search-field">
              <span className="visually-hidden">Search complaints</span>
              <input type="search" placeholder="Search complaint ID or title" value={query}
                onChange={(event) => { setPage(0); setQuery(event.target.value); }} />
            </label>
            <select aria-label="Filter complaints by status" value={status}
              onChange={(event) => { setPage(0); setStatus(event.target.value); }}>
              <option value="">All statuses</option>
              {complaintStatuses.map((item) => <option value={item} key={item}>{item.replaceAll('_', ' ')}</option>)}
            </select>
            <select aria-label="Filter complaints by category" value={category}
              onChange={(event) => { setPage(0); setCategory(event.target.value); }}>
              <option value="">All categories</option>
              {categories.map((item) => <option value={item} key={item}>{item}</option>)}
            </select>
            <select aria-label="Filter complaints by priority" value={priority}
              onChange={(event) => { setPage(0); setPriority(event.target.value); }}>
              <option value="">All priorities</option>
              {['URGENT', 'HIGH', 'MEDIUM', 'LOW'].map((item) => <option value={item} key={item}>{item}</option>)}
            </select>
            <select aria-label="Filter complaints by department" value={department}
              onChange={(event) => { setPage(0); setDepartment(event.target.value); }}>
              <option value="">All departments</option>
              {complaintDepartments.map((item) => <option value={item} key={item}>{item}</option>)}
            </select>
            {hasFilters && <button className="button button-quiet button-small" type="button" onClick={resetFilters}>Reset Filters</button>}
          </div>
          {complaints.length === 0 ? (
            <div className="empty-state"><span className="empty-icon">—</span><p>No complaints are recorded yet.</p></div>
          ) : (
            <ComplaintTable
              complaints={visibleComplaints}
              detailBase="/admin/complaints"
              showReporter
              showAssignments
              renderAssignmentAction={(complaint) => (
                <ComplaintAssignmentEditor
                  complaint={complaint}
                  departments={departments}
                  departmentsLoading={departmentsLoading}
                  departmentsError={departmentsError}
                  compact
                  onSaved={updateAssignment}
                />
              )}
              emptyMessage="No complaints match the selected filters."
            />
          )}
          {pageCount > 1 && (
            <nav className="complaint-pagination" aria-label="Complaint pages">
              <button className="button button-quiet button-small" type="button"
                onClick={() => setPage(currentPage - 1)} disabled={currentPage === 0}>Previous</button>
              <span>Page {currentPage + 1} of {pageCount}</span>
              <button className="button button-quiet button-small" type="button"
                onClick={() => setPage(currentPage + 1)} disabled={currentPage >= pageCount - 1}>Next</button>
            </nav>
          )}
          <p className="chart-note">Assignment changes are recorded in the complaint history. Complaint status is not changed by assigning Staff.</p>
        </section>
      )}
    </DashboardLayout>
  );
}

export default AdminComplaintsPage;
