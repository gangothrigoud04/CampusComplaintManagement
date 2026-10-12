import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import AppIcon from '../components/AppIcon';
import ComplaintTable from '../components/ComplaintTable';
import DashboardLayout from '../components/DashboardLayout';
import MetricCard from '../components/MetricCard';
import api from '../services/api';
import { useAuth } from '../context/AuthContext';

function FacultyPortalPage() {
  const { user } = useAuth();
  const [summary, setSummary] = useState(null);
  const [complaints, setComplaints] = useState([]);
  const [analytics, setAnalytics] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const initialLoadStarted = useRef(false);

  const refresh = async () => {
    setLoading(true);
    setError('');
    try {
      const [summaryResult, complaintsResult, analyticsResult] = await Promise.all([
        api.get('/faculty/summary'),
        api.get('/faculty/complaints', { params: { page: 0, size: 5 } }),
        api.get('/faculty/analytics'),
      ]);
      setSummary(summaryResult.data);
      setComplaints(complaintsResult.data.content);
      setAnalytics(analyticsResult.data);
    } catch (requestError) {
      setError(requestError.response?.data?.message || 'Faculty dashboard data could not be loaded.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (initialLoadStarted.current || user?.role !== 'FACULTY') return;
    initialLoadStarted.current = true;
    refresh();
  }, [user]);

  return (
    <DashboardLayout role="FACULTY" title="Overview" eyebrow="Faculty workspace"
      actions={<>
        <Link className="button button-primary button-small" to="/faculty/complaints/new"><AppIcon name="plus" size={17} /> Submit complaint</Link>
        <button className="button button-secondary button-small" type="button" onClick={refresh} disabled={loading}>
          {loading ? 'Refreshing…' : 'Refresh'}
        </button>
      </>}>
      {error && <div className="form-alert form-alert-error" role="alert">{error}
        <button className="inline-retry" type="button" onClick={refresh} disabled={loading}>Retry</button>
      </div>}
      {loading && <div className="dashboard-loading" role="status"><span className="button-spinner" /> Loading your dashboard…</div>}
      {!loading && !error && summary && analytics && (
        <>
          <section className="dashboard-welcome faculty-welcome">
            <div><h2>Welcome, {user?.name || 'Faculty member'}.</h2>
              <p>Campus-wide view of complaints submitted by Students.</p></div>
            <span className="role-chip"><AppIcon name="graduation" size={16} /> Faculty</span>
          </section>
          <div className="metric-grid faculty-dashboard-metrics">
            <MetricCard label="Student complaints" value={summary.total} detail="Campus-wide student reports" icon="clipboard" tone="blue" />
            <MetricCard label="Pending" value={summary.pending} detail="Submitted or under review" icon="clock" tone="amber" />
            <MetricCard label="Assigned" value={summary.assigned} detail="Assigned to a service team" icon="clipboard" tone="violet" />
            <MetricCard label="In progress" value={summary.inProgress} detail="Currently being handled" icon="clock" tone="violet" />
            <MetricCard label="Resolved" value={summary.resolved} detail={`${summary.resolutionRate.toFixed(1)}% resolution rate`} icon="check" tone="green" />
          </div>
          {summary.rejected > 0 && <p className="chart-note">{summary.rejected} complaint{summary.rejected === 1 ? '' : 's'} rejected.</p>}
          <div className="dashboard-grid dashboard-grid-wide-left">
            <section className="panel-card">
              <div className="panel-heading">
                <div><p className="eyebrow">Recent activity</p><h2>Recent Student complaints</h2></div>
                <Link className="text-link" to="/faculty/complaints">View all</Link>
              </div>
              <ComplaintTable complaints={complaints} detailBase="/faculty/complaints"
                showReporter showAssignments showLocation showDescription showImages
                emptyMessage="No Student complaints have been submitted yet." />
            </section>
            <section className="panel-card">
              <div className="panel-heading"><div><p className="eyebrow">Status overview</p><h2>Student complaint statuses</h2></div></div>
              <div className="faculty-bar-list">
                {analytics.byStatus.filter((item) => item.count > 0).map((item) => (
                  <div className="faculty-bar-item" key={item.label}>
                    <div><span>{item.label.replaceAll('_', ' ')}</span><strong>{item.count}</strong></div>
                    <div className="faculty-bar-track"><span style={{ width: `${Math.max(3, (item.count / Math.max(summary.total, 1)) * 100)}%` }} /></div>
                  </div>
                ))}
                {summary.total === 0 && <p className="chart-empty">Status data will appear after a complaint is submitted.</p>}
              </div>
              <Link className="text-link faculty-analytics-link" to="/faculty/analytics">Explore analytics <AppIcon name="arrow" size={15} /></Link>
            </section>
          </div>
        </>
      )}
    </DashboardLayout>
  );
}

export default FacultyPortalPage;
