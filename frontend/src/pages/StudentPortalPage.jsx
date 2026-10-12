import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import AppIcon from '../components/AppIcon';
import ComplaintTable from '../components/ComplaintTable';
import DashboardLayout from '../components/DashboardLayout';
import HorizontalBars from '../components/HorizontalBars';
import MetricCard from '../components/MetricCard';
import StatusDonut from '../components/StatusDonut';
import { useAuth } from '../context/AuthContext';
import api from '../services/api';
import { complaintStatuses, filterAndSortComplaints, summarizeComplaints } from '../utils/complaints';

function StudentPortalPage() {
  const { user } = useAuth();
  const [complaints, setComplaints] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [query, setQuery] = useState('');
  const [status, setStatus] = useState('');

  const loadComplaints = async () => {
    setLoading(true);
    setError('');
    try {
      const { data } = await api.get('/complaints/my');
      setComplaints(data);
    } catch (requestError) {
      setError(requestError.response?.data?.message || 'Your complaint summary could not be loaded.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { loadComplaints(); }, []);

  const filtered = useMemo(
    () => filterAndSortComplaints(complaints, { query, status }),
    [complaints, query, status],
  );
  const counts = summarizeComplaints(complaints);
  const categories = Object.entries(complaints.reduce((result, complaint) => {
    result[complaint.category] = (result[complaint.category] || 0) + 1;
    return result;
  }, {})).map(([label, value]) => ({ label, value })).sort((a, b) => b.value - a.value).slice(0, 5);

  return (
    <DashboardLayout
      role="STUDENT"
      title={`Welcome${user?.name ? `, ${user.name.split(' ')[0]}` : ''}`}
      eyebrow="Student dashboard"
      actions={<button className="button button-secondary button-small" type="button" onClick={loadComplaints} disabled={loading}>
        {loading ? 'Refreshing…' : 'Refresh'}
      </button>}
    >
      <section className="dashboard-welcome">
        <div>
          <h2>Your campus, your voice.</h2>
          <p>See the reports you have submitted and check their latest recorded status.</p>
        </div>
        <Link className="button button-white" to="/student/complaints/new"><AppIcon name="plus" size={18} /> Submit a complaint</Link>
      </section>

      {error && <div className="form-alert form-alert-error" role="alert">{error} <button className="inline-retry" type="button" onClick={loadComplaints} disabled={loading}>Retry</button></div>}
      {loading && <div className="dashboard-loading" role="status"><span className="button-spinner" /> Loading your complaint data…</div>}

      {!loading && !error && (
        <>
          <div className="metric-grid">
            <MetricCard label="Total complaints" value={counts.total} detail="Submitted by you" icon="clipboard" tone="blue" />
            <MetricCard label="Pending" value={counts.pending} detail="Submitted or under review" icon="clock" tone="amber" />
            <MetricCard label="In progress" value={counts.inProgress} detail="Assigned or being handled" icon="chart" tone="violet" />
            <MetricCard label="Resolved" value={counts.resolved} detail="Resolved or closed" icon="check" tone="green" />
          </div>

          <div className="dashboard-grid dashboard-grid-wide-left">
            <section className="panel-card">
              <div className="panel-heading">
                <div><p className="eyebrow">Your activity</p><h2>Recent complaints</h2></div>
                <Link className="text-link" to="/student/complaints">View all <AppIcon name="arrow" size={15} /></Link>
              </div>
              <div className="filter-row">
                <label className="search-field">
                  <span className="visually-hidden">Search complaints</span>
                  <input type="search" placeholder="Search by complaint ID or title" value={query}
                    onChange={(event) => setQuery(event.target.value)} />
                </label>
                <select aria-label="Filter complaints by status" value={status} onChange={(event) => setStatus(event.target.value)}>
                  <option value="">All statuses</option>
                  {complaintStatuses.map((item) => <option key={item} value={item}>{item.replaceAll('_', ' ')}</option>)}
                </select>
                {(query || status) && <button type="button" className="button button-quiet button-small" onClick={() => { setQuery(''); setStatus(''); }}>Reset</button>}
              </div>
              <ComplaintTable complaints={filtered.slice(0, 6)}
                emptyMessage={complaints.length ? 'No complaints match the selected filters.' : 'Your submitted complaints will appear here.'} />
            </section>
            <section className="panel-card">
              <div className="panel-heading"><div><p className="eyebrow">At a glance</p><h2>Status overview</h2></div></div>
              <StatusDonut complaints={complaints} />
              <div className="chart-note">Summary uses your complaint records only.</div>
            </section>
          </div>

          <section className="panel-card">
            <div className="panel-heading">
              <div><p className="eyebrow">Your reports</p><h2>Top categories</h2></div>
            </div>
            <HorizontalBars items={categories} emptyMessage="Category insights will appear when you submit a complaint." />
          </section>
        </>
      )}
    </DashboardLayout>
  );
}

export default StudentPortalPage;
