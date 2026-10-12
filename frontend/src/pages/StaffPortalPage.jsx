import { useEffect, useState } from 'react';
import ComplaintTable from '../components/ComplaintTable';
import DashboardLayout from '../components/DashboardLayout';
import MetricCard from '../components/MetricCard';
import StatusDonut from '../components/StatusDonut';
import api from '../services/api';
import { summarizeStaffComplaints } from '../utils/complaints';

function StaffPortalPage() {
  const [complaints, setComplaints] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const loadAssignments = async () => {
    setLoading(true);
    setError('');
    try {
      const { data } = await api.get('/complaints/assigned');
      setComplaints(data);
    } catch (requestError) {
      setError(requestError.response?.data?.message || 'Assigned complaints could not be loaded.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    const refreshWhenVisible = () => {
      if (document.visibilityState === 'visible') loadAssignments();
    };
    window.addEventListener('focus', refreshWhenVisible);
    document.addEventListener('visibilitychange', refreshWhenVisible);
    loadAssignments();
    return () => {
      window.removeEventListener('focus', refreshWhenVisible);
      document.removeEventListener('visibilitychange', refreshWhenVisible);
    };
  }, []);

  const metrics = summarizeStaffComplaints(complaints);

  return (
    <DashboardLayout role="STAFF" title="Assigned complaints" eyebrow="Staff workspace"
      actions={<button className="button button-secondary button-small" type="button" onClick={loadAssignments} disabled={loading}>{loading ? 'Refreshing…' : 'Refresh'}</button>}>
      {error && <div className="form-alert form-alert-error" role="alert">{error}</div>}
      {loading && <div className="dashboard-loading" role="status"><span className="button-spinner" /> Loading assigned complaints…</div>}
      {!loading && !error && (
        <>
          <div className="dashboard-welcome staff-welcome">
            <div><h2>Good work starts with clear context.</h2><p>This view includes only complaints currently assigned to your Staff account.</p></div>
          </div>
          <div className="metric-grid">
            <MetricCard label="Total assigned" value={metrics.total} detail="Unique active assignments" icon="clipboard" tone="blue" />
            <MetricCard label="Awaiting work" value={metrics.awaitingWork} detail="Submitted, review, or assigned" icon="clock" tone="amber" />
            <MetricCard label="In progress" value={metrics.inProgress} detail="Current status recorded" icon="clock" tone="amber" />
            <MetricCard label="Resolved" value={metrics.resolved} detail="Marked resolved" icon="check" tone="green" />
          </div>
          <div className="dashboard-grid dashboard-grid-wide-left">
            <section className="panel-card">
              <div className="panel-heading"><div><p className="eyebrow">Current work</p><h2>Your assigned queue</h2></div></div>
              <ComplaintTable complaints={complaints} detailBase="/staff/complaints" showAssignments showLocation
                emptyMessage="No active assignments are attached to your account." />
            </section>
            <section className="panel-card">
              <div className="panel-heading"><div><p className="eyebrow">Workload</p><h2>Status overview</h2></div></div>
              <StatusDonut complaints={complaints} />
            </section>
          </div>
        </>
      )}
    </DashboardLayout>
  );
}

export default StaffPortalPage;
