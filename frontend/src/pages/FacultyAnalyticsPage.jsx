import { useEffect, useRef, useState } from 'react';
import DashboardLayout from '../components/DashboardLayout';
import MetricCard from '../components/MetricCard';
import { useAuth } from '../context/AuthContext';
import api from '../services/api';

function BarList({ title, eyebrow, items }) {
  const max = Math.max(1, ...items.map((item) => item.count));
  return (
    <section className="panel-card">
      <div className="panel-heading"><div><p className="eyebrow">{eyebrow}</p><h2>{title}</h2></div></div>
      {items.length === 0 ? <div className="empty-state"><p>No data is available yet.</p></div> : (
        <div className="faculty-bar-list">
          {items.map((item) => <div className="faculty-bar-item" key={item.label}>
            <div><span>{item.label.replaceAll('_', ' ')}</span><strong>{item.count}</strong></div>
            <div className="faculty-bar-track"><span style={{ width: `${Math.max(3, (item.count / max) * 100)}%` }} /></div>
          </div>)}
        </div>
      )}
    </section>
  );
}

function FacultyAnalyticsPage() {
  const { user, loading: authLoading } = useAuth();
  const [analytics, setAnalytics] = useState(null);
  const [summary, setSummary] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const initialLoadStarted = useRef(false);

  const refresh = async () => {
    setLoading(true);
    setError('');
    try {
      const [analyticsResult, summaryResult] = await Promise.all([
        api.get('/faculty/analytics'),
        api.get('/faculty/summary'),
      ]);
      setAnalytics(analyticsResult.data);
      setSummary(summaryResult.data);
    } catch (requestError) {
      setError(requestError.response?.data?.message || 'Faculty analytics could not be loaded.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (authLoading || user?.role !== 'FACULTY' || initialLoadStarted.current) return;
    initialLoadStarted.current = true;
    refresh();
  }, [authLoading, user]);
  const peak = Math.max(1, ...(analytics?.monthlySubmissions || []).map((item) => item.count));

  return (
    <DashboardLayout role="FACULTY" title="Analytics" eyebrow="Complaint insights"
      actions={<button className="button button-secondary button-small" type="button" onClick={refresh} disabled={loading}>{loading ? 'Refreshing…' : 'Refresh analytics'}</button>}>
      {error && <div className="form-alert form-alert-error" role="alert">{error}</div>}
      {loading && <div className="dashboard-loading" role="status"><span className="button-spinner" /> Loading analytics…</div>}
      {!loading && !error && analytics && summary && (
        <>
          <p className="faculty-scope-note">Insights include campus-wide complaints submitted by Students.</p>
          <div className="metric-grid metric-grid-three">
            <MetricCard label="Resolution rate" value={`${summary.resolutionRate.toFixed(1)}%`} detail="Resolved or closed complaints" icon="check" tone="green" />
            <MetricCard label="Average resolution" value={analytics.averageResolutionHours === null
              ? '—' : `${analytics.averageResolutionHours.toFixed(1)} hrs`}
              detail="From submission to first resolution" icon="clock" tone="violet" />
            <MetricCard label="Total records" value={summary.total} detail={`${summary.rejected} rejected`} icon="clipboard" tone="blue" />
          </div>
          <div className="dashboard-grid dashboard-grid-equal">
            <BarList title="Status distribution" eyebrow="Workflow" items={analytics.byStatus.filter((item) => item.count > 0)} />
            <BarList title="Priority distribution" eyebrow="Reported priority" items={analytics.byPriority.filter((item) => item.count > 0)} />
          </div>
          <div className="dashboard-grid dashboard-grid-equal">
            <BarList title="By category" eyebrow="Complaint categories" items={analytics.byCategory} />
            <BarList title="By department" eyebrow="Assigned service areas" items={analytics.byDepartment} />
          </div>
          <section className="panel-card">
            <div className="panel-heading"><div><p className="eyebrow">Last 12 months</p><h2>Complaint submissions</h2></div></div>
            <div className="faculty-month-chart" role="img" aria-label="Complaint submissions by month">
              {analytics.monthlySubmissions.map((item) => <div className="faculty-month-column" key={item.month}>
                <span className="faculty-month-value">{item.count || ''}</span>
                <div className="faculty-month-bar"><span style={{ height: `${item.count ? Math.max(4, (item.count / peak) * 100) : 0}%` }} /></div>
                <span className="faculty-month-label">{item.month}</span>
              </div>)}
            </div>
          </section>
        </>
      )}
    </DashboardLayout>
  );
}

export default FacultyAnalyticsPage;
