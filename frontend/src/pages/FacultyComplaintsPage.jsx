import { useEffect, useMemo, useState } from 'react';
import { useLocation } from 'react-router-dom';
import ComplaintTable from '../components/ComplaintTable';
import DashboardLayout from '../components/DashboardLayout';
import api from '../services/api';

const pageSize = 10;
const statuses = ['SUBMITTED', 'UNDER_REVIEW', 'ASSIGNED', 'IN_PROGRESS', 'RESOLVED', 'CLOSED', 'REJECTED'];
const priorities = ['URGENT', 'HIGH', 'MEDIUM', 'LOW'];

function FacultyComplaintsPage({ reports = false }) {
  const location = useLocation();
  const [rows, setRows] = useState([]);
  const [pageInfo, setPageInfo] = useState({ totalElements: 0, totalPages: 0 });
  const [options, setOptions] = useState({ categories: [], departments: [] });
  const [optionsError, setOptionsError] = useState('');
  const [query, setQuery] = useState('');
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState('');
  const [priority, setPriority] = useState('');
  const [categoryId, setCategoryId] = useState('');
  const [departmentId, setDepartmentId] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [sort, setSort] = useState('submittedAt');
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [reloadKey, setReloadKey] = useState(0);
  const [filtersReloadKey, setFiltersReloadKey] = useState(0);
  const [exporting, setExporting] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    let active = true;
    api.get('/faculty/filters')
      .then(({ data }) => {
        if (active) {
          setOptions(data);
          setOptionsError('');
        }
      })
      .catch((requestError) => {
        if (active) setOptionsError(requestError.response?.data?.message || 'Filter choices could not be loaded.');
      });
    return () => { active = false; };
  }, [filtersReloadKey]);

  useEffect(() => {
    let active = true;
    const load = async () => {
      setLoading(true);
      setError('');
      try {
        const { data } = await api.get('/faculty/complaints', {
          params: {
            page, size: pageSize, search: search || undefined, status: status || undefined,
            priority: priority || undefined, categoryId: categoryId || undefined,
            departmentId: departmentId || undefined, from: from || undefined, to: to || undefined, sort,
          },
        });
        if (active) {
          setRows(data.content);
          setPageInfo(data);
        }
      } catch (requestError) {
        if (active) setError(requestError.response?.data?.message || 'Complaints could not be loaded.');
      } finally {
        if (active) setLoading(false);
      }
    };
    load();
    return () => { active = false; };
  }, [page, search, status, priority, categoryId, departmentId, from, to, sort, reloadKey]);

  const exportParams = useMemo(() => ({
    search: search || undefined, status: status || undefined, priority: priority || undefined,
    categoryId: categoryId || undefined, departmentId: departmentId || undefined,
    from: from || undefined, to: to || undefined,
  }), [search, status, priority, categoryId, departmentId, from, to]);

  const exportCsv = async () => {
    setExporting(true);
    setError('');
    try {
      const { data } = await api.get('/faculty/reports.csv', { params: exportParams, responseType: 'blob' });
      const url = URL.createObjectURL(data);
      const anchor = document.createElement('a');
      anchor.href = url;
      anchor.download = 'faculty-complaints.csv';
      document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();
      window.setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (requestError) {
      setError(requestError.response?.data?.message || 'The report could not be exported. Please retry.');
    } finally {
      setExporting(false);
    }
  };

  const resetFilters = () => {
    setQuery('');
    setSearch('');
    setStatus('');
    setPriority('');
    setCategoryId('');
    setDepartmentId('');
    setFrom('');
    setTo('');
    setPage(0);
  };

  const title = reports || location.pathname === '/faculty/reports' ? 'Reports' : 'All complaints';
  const hasFilters = query || status || priority || categoryId || departmentId || from || to;
  const applySearch = (event) => {
    event.preventDefault();
    setPage(0);
    setSearch(query.trim());
  };

  return (
    <DashboardLayout role="FACULTY" title={title} eyebrow="Complaint monitoring"
      actions={<>
        <button className="button button-quiet button-small" type="button" onClick={() => setReloadKey((value) => value + 1)} disabled={loading}>
          {loading ? 'Refreshing…' : 'Refresh'}
        </button>
        <button className="button button-secondary button-small" type="button" onClick={exportCsv} disabled={exporting || loading}>
          {exporting ? 'Preparing…' : 'Export CSV'}
        </button>
      </>}>
      {error && <div className="form-alert form-alert-error" role="alert">{error}
      <button className="inline-retry" type="button" onClick={() => setReloadKey((value) => value + 1)} disabled={loading}>Retry</button>
      </div>}
      {optionsError && <div className="form-alert form-alert-error" role="alert">{optionsError}
        <button className="inline-retry" type="button" onClick={() => setFiltersReloadKey((value) => value + 1)}>Retry filter choices</button>
      </div>}
      <section className="panel-card">
        <div className="panel-heading">
          <div><p className="eyebrow">Authorized records</p><h2>Student-submitted campus complaints</h2></div>
          {!loading && !error && <span className="record-count">{pageInfo.totalElements} records</span>}
        </div>
        <form className="filter-row faculty-filter-row" onSubmit={applySearch}>
          <label className="search-field"><span className="visually-hidden">Search complaints</span>
            <input type="search" placeholder="Search ID, title, category or location" value={query}
              onChange={(event) => setQuery(event.target.value)} />
          </label>
          <button className="button button-secondary button-small" type="submit">Search</button>
          <select aria-label="Filter by status" value={status} onChange={(event) => { setPage(0); setStatus(event.target.value); }}>
            <option value="">All statuses</option>{statuses.map((item) => <option key={item} value={item}>{item.replaceAll('_', ' ')}</option>)}
          </select>
          <select aria-label="Filter by priority" value={priority} onChange={(event) => { setPage(0); setPriority(event.target.value); }}>
            <option value="">All priorities</option>{priorities.map((item) => <option key={item} value={item}>{item}</option>)}
          </select>
          <select aria-label="Filter by category" value={categoryId} onChange={(event) => { setPage(0); setCategoryId(event.target.value); }}>
            <option value="">All categories</option>{options.categories.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}
          </select>
          <select aria-label="Filter by department" value={departmentId} onChange={(event) => { setPage(0); setDepartmentId(event.target.value); }}>
            <option value="">All departments</option>{options.departments.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}
          </select>
          <label className="faculty-date-filter">From <input aria-label="From date" type="date" value={from} onChange={(event) => { setPage(0); setFrom(event.target.value); }} /></label>
          <label className="faculty-date-filter">To <input aria-label="To date" type="date" value={to} onChange={(event) => { setPage(0); setTo(event.target.value); }} /></label>
          <select aria-label="Sort complaints" value={sort} onChange={(event) => setSort(event.target.value)}>
            <option value="submittedAt">Newest first</option><option value="updatedAt">Recently updated</option>
            <option value="priority">Priority</option><option value="status">Status</option>
          </select>
          {hasFilters && <button className="button button-quiet button-small" type="button" onClick={resetFilters}>Reset filters</button>}
        </form>
        {loading && <div className="dashboard-loading" role="status"><span className="button-spinner" /> Loading complaints…</div>}
        {!loading && !error && (
          <>
            <ComplaintTable complaints={rows} detailBase="/faculty/complaints"
              showReporter showAssignments showLocation showDescription showImages
              emptyMessage="No Student complaints match these filters." />
            {pageInfo.totalPages > 1 && <nav className="complaint-pagination" aria-label="Complaint pages">
              <button className="button button-quiet button-small" type="button" onClick={() => setPage((value) => value - 1)} disabled={page === 0}>Previous</button>
              <span>Page {page + 1} of {pageInfo.totalPages}</span>
              <button className="button button-quiet button-small" type="button" onClick={() => setPage((value) => value + 1)} disabled={page + 1 >= pageInfo.totalPages}>Next</button>
            </nav>}
          </>
        )}
      </section>
      <p className="chart-note">Faculty can review Student-submitted campus complaints. CSV exports apply the current search and filters, up to 5,000 records.</p>
    </DashboardLayout>
  );
}

export default FacultyComplaintsPage;
