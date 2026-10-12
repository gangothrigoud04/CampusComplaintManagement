import { useEffect, useState } from 'react';
import AppIcon from '../components/AppIcon';
import DashboardLayout from '../components/DashboardLayout';
import MetricCard from '../components/MetricCard';
import HorizontalBars from '../components/HorizontalBars';
import api from '../services/api';

const statusPalette = {
  SUBMITTED: '#d99b27',
  UNDER_REVIEW: '#e9b85c',
  ASSIGNED: '#8a75c5',
  IN_PROGRESS: '#4382c3',
  RESOLVED: '#32936c',
  CLOSED: '#76b99b',
  REJECTED: '#cb5a5a',
};

const priorityPalette = {
  URGENT: '#d14343',
  HIGH: '#f08c00',
  MEDIUM: '#2b8a3e',
  LOW: '#1c7ed6',
};

function formatHours(hours) {
  if (hours == null) return 'N/A';
  if (hours < 1) return `${Math.round(hours * 60)} mins`;
  if (hours >= 48) return `${(hours / 24).toFixed(1)} days`;
  return `${hours.toFixed(1)} hrs`;
}

function AdminAnalyticsPage() {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  // Filter states
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [departmentId, setDepartmentId] = useState('');
  const [categoryId, setCategoryId] = useState('');
  const [priority, setPriority] = useState('');
  const [status, setStatus] = useState('');
  const [assignedStaffId, setAssignedStaffId] = useState('');
  const [exporting, setExporting] = useState(false);

  const hasFilters = Boolean(from || to || departmentId || categoryId || priority || status || assignedStaffId);

  const resetFilters = () => {
    setFrom('');
    setTo('');
    setDepartmentId('');
    setCategoryId('');
    setPriority('');
    setStatus('');
    setAssignedStaffId('');
  };

  const loadAnalytics = async () => {
    setLoading(true);
    setError('');
    try {
      const params = {};
      if (from) params.from = from;
      if (to) params.to = to;
      if (departmentId) params.departmentId = departmentId;
      if (categoryId) params.categoryId = categoryId;
      if (priority) params.priority = priority;
      if (status) params.status = status;
      if (assignedStaffId) params.assignedStaffId = assignedStaffId;

      const response = await api.get('/admin/analytics', { params });
      setData(response.data);
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to load analytics data. Please retry.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadAnalytics();
  }, [from, to, departmentId, categoryId, priority, status, assignedStaffId]);

  const handleExportCsv = async () => {
    setExporting(true);
    try {
      const params = {};
      if (from) params.from = from;
      if (to) params.to = to;
      if (departmentId) params.departmentId = departmentId;
      if (categoryId) params.categoryId = categoryId;
      if (priority) params.priority = priority;
      if (status) params.status = status;
      if (assignedStaffId) params.assignedStaffId = assignedStaffId;

      const response = await api.get('/admin/analytics/export.csv', {
        params,
        responseType: 'blob',
      });
      const blob = new Blob([response.data], { type: 'text/csv;charset=utf-8;' });
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', `admin-analytics-${new Date().toISOString().slice(0, 10)}.csv`);
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(url);
    } catch (err) {
      alert(err.response?.data?.message || 'CSV export failed. Please retry.');
    } finally {
      setExporting(false);
    }
  };

  const handlePrint = () => {
    window.print();
  };

  const summary = data?.summary || {
    total: 0,
    submitted: 0,
    underReview: 0,
    assigned: 0,
    inProgress: 0,
    resolved: 0,
    closed: 0,
    rejected: 0,
    overdue: 0,
    averageResolutionHours: null,
    resolvedWithinSlaCount: 0,
    resolvedAfterSlaCount: 0,
    resolvedWithinSlaPercentage: 0,
    resolvedAfterSlaPercentage: 0,
  };

  const filterOptions = data?.filterOptions || { departments: [], categories: [], staffMembers: [] };
  const byStatus = data?.byStatus || [];
  const byPriority = data?.byPriority || [];
  const byCategory = (data?.byCategory || []).map((item) => ({ label: item.label, value: item.count }));
  const byDepartment = (data?.byDepartment || []).map((item) => ({ label: item.label, value: item.count }));
  const monthlyTrends = data?.monthlyTrends || [];
  const weeklyTrends = data?.weeklyTrends || [];
  const staffWorkload = data?.staffWorkload || [];
  const byEscalationLevel = data?.byEscalationLevel || [];

  const maxMonthly = Math.max(...monthlyTrends.map((t) => t.count), 1);
  const maxWeekly = Math.max(...weeklyTrends.map((t) => t.count), 1);

  return (
    <DashboardLayout
      role="ADMIN"
      title="Analytics & Reports"
      eyebrow="Operational Intelligence"
      actions={
        <div className="analytics-header-actions print-hidden">
          <button
            className="button button-secondary button-small"
            type="button"
            onClick={loadAnalytics}
            disabled={loading}
          >
            <AppIcon name="clock" size={15} />
            <span>{loading ? 'Refreshing…' : 'Refresh Data'}</span>
          </button>
          <button
            className="button button-secondary button-small"
            type="button"
            onClick={handleExportCsv}
            disabled={loading || exporting}
          >
            <AppIcon name="download" size={15} />
            <span>{exporting ? 'Exporting…' : 'Export CSV'}</span>
          </button>
          <button
            className="button button-primary button-small"
            type="button"
            onClick={handlePrint}
          >
            <span>Print Report</span>
          </button>
        </div>
      }
    >
      {/* Print Header */}
      <div className="print-only report-print-header">
        <h2>CampusCare — Campus Complaint Management System</h2>
        <h3>Administrative Analytics & SLA Performance Report</h3>
        <p className="report-meta">
          <strong>Generated:</strong> {new Date().toLocaleString()} | <strong>Filter Scope:</strong>{' '}
          {hasFilters
            ? [
                from ? `From: ${from}` : '',
                to ? `To: ${to}` : '',
                departmentId ? `Department ID: ${departmentId}` : '',
                categoryId ? `Category ID: ${categoryId}` : '',
                priority ? `Priority: ${priority}` : '',
                status ? `Status: ${status}` : '',
                assignedStaffId ? `Staff ID: ${assignedStaffId}` : '',
              ].filter(Boolean).join(' · ')
            : 'All Historical Records (No filters applied)'}
        </p>
        <hr />
      </div>

      {error && (
        <div className="form-alert form-alert-error print-hidden" role="alert">
          {error}{' '}
          <button className="inline-retry" type="button" onClick={loadAnalytics} disabled={loading}>
            Retry
          </button>
        </div>
      )}

      {/* Filter Row */}
      <section className="panel-card analytics-filters-panel print-hidden">
        <div className="panel-heading">
          <div>
            <p className="eyebrow">Data controls</p>
            <h3>Filter metrics & breakdown</h3>
          </div>
          {hasFilters && (
            <button className="button button-quiet button-small" type="button" onClick={resetFilters}>
              Reset all filters
            </button>
          )}
        </div>
        <div className="filter-row analytics-filter-row">
          <label className="filter-field">
            <span className="field-caption">From date</span>
            <input
              type="date"
              value={from}
              onChange={(e) => setFrom(e.target.value)}
              aria-label="Filter from date"
            />
          </label>
          <label className="filter-field">
            <span className="field-caption">To date</span>
            <input
              type="date"
              value={to}
              onChange={(e) => setTo(e.target.value)}
              aria-label="Filter to date"
            />
          </label>
          <label className="filter-field">
            <span className="field-caption">Department</span>
            <select
              value={departmentId}
              onChange={(e) => setDepartmentId(e.target.value)}
              aria-label="Filter by department"
            >
              <option value="">All Departments</option>
              {filterOptions.departments.map((dept) => (
                <option key={dept.id} value={dept.id}>
                  {dept.name}
                </option>
              ))}
            </select>
          </label>
          <label className="filter-field">
            <span className="field-caption">Category</span>
            <select
              value={categoryId}
              onChange={(e) => setCategoryId(e.target.value)}
              aria-label="Filter by category"
            >
              <option value="">All Categories</option>
              {filterOptions.categories.map((cat) => (
                <option key={cat.id} value={cat.id}>
                  {cat.name}
                </option>
              ))}
            </select>
          </label>
          <label className="filter-field">
            <span className="field-caption">Priority</span>
            <select
              value={priority}
              onChange={(e) => setPriority(e.target.value)}
              aria-label="Filter by priority"
            >
              <option value="">All Priorities</option>
              {['URGENT', 'HIGH', 'MEDIUM', 'LOW'].map((p) => (
                <option key={p} value={p}>
                  {p}
                </option>
              ))}
            </select>
          </label>
          <label className="filter-field">
            <span className="field-caption">Status</span>
            <select
              value={status}
              onChange={(e) => setStatus(e.target.value)}
              aria-label="Filter by status"
            >
              <option value="">All Statuses</option>
              {['SUBMITTED', 'UNDER_REVIEW', 'ASSIGNED', 'IN_PROGRESS', 'RESOLVED', 'CLOSED', 'REJECTED'].map((s) => (
                <option key={s} value={s}>
                  {s.replace('_', ' ')}
                </option>
              ))}
            </select>
          </label>
          <label className="filter-field">
            <span className="field-caption">Assigned staff</span>
            <select
              value={assignedStaffId}
              onChange={(e) => setAssignedStaffId(e.target.value)}
              aria-label="Filter by assigned staff"
            >
              <option value="">All Staff</option>
              {filterOptions.staffMembers.map((staff) => (
                <option key={staff.id} value={staff.id}>
                  {staff.name}
                </option>
              ))}
            </select>
          </label>
        </div>
      </section>

      {loading && !data && (
        <div className="dashboard-loading" role="status">
          <span className="button-spinner" /> Loading campus analytics…
        </div>
      )}

      {/* Summary KPI Cards */}
      <div className="analytics-kpi-grid">
        <MetricCard
          label="Total Complaints"
          value={summary.total}
          detail="Across selected filters"
          icon="clipboard"
          tone="blue"
        />
        <MetricCard
          label="In Progress / Assigned"
          value={summary.inProgress + summary.assigned}
          detail={`${summary.submitted} newly submitted`}
          icon="clock"
          tone="amber"
        />
        <MetricCard
          label="Resolved / Closed"
          value={summary.resolved + summary.closed}
          detail={`${summary.total > 0 ? Math.round(((summary.resolved + summary.closed) / summary.total) * 100) : 0}% of complaints`}
          icon="check"
          tone="green"
        />
        <MetricCard
          label="SLA Compliance Rate"
          value={`${summary.resolvedWithinSlaPercentage}%`}
          detail={`${summary.resolvedWithinSlaCount} within SLA · ${summary.resolvedAfterSlaCount} breached`}
          icon="chart"
          tone={summary.resolvedWithinSlaPercentage >= 75 ? 'green' : 'amber'}
        />
        <MetricCard
          label="Overdue Active"
          value={summary.overdue}
          detail="Pending deadline breaches"
          icon="alert"
          tone={summary.overdue > 0 ? 'red' : 'green'}
        />
        <MetricCard
          label="Avg Resolution Time"
          value={formatHours(summary.averageResolutionHours)}
          detail="From submission to resolution"
          icon="clock"
          tone="blue"
        />
      </div>

      {/* Grid of Breakdowns & Trends */}
      <div className="analytics-charts-grid">
        {/* Monthly Complaint Intake Trend */}
        <section className="panel-card chart-panel">
          <div className="panel-heading">
            <div>
              <p className="eyebrow">Volume over time</p>
              <h3>Monthly Intake Trend (Last 6 Months)</h3>
            </div>
            <span className="record-count">{monthlyTrends.reduce((acc, t) => acc + t.count, 0)} total in period</span>
          </div>
          {monthlyTrends.length === 0 ? (
            <div className="chart-empty">No monthly trend data available.</div>
          ) : (
            <div className="analytics-bar-chart">
              {monthlyTrends.map((item) => {
                const heightPct = Math.max((item.count / maxMonthly) * 100, item.count > 0 ? 8 : 2);
                return (
                  <div className="analytics-bar-col" key={item.period}>
                    <div className="bar-val-label">{item.count}</div>
                    <div className="bar-track-vertical">
                      <div className="bar-fill-vertical" style={{ height: `${heightPct}%` }} />
                    </div>
                    <div className="bar-x-label">{item.period}</div>
                  </div>
                );
              })}
            </div>
          )}
        </section>

        {/* Weekly Complaint Intake Trend */}
        <section className="panel-card chart-panel">
          <div className="panel-heading">
            <div>
              <p className="eyebrow">Recent momentum</p>
              <h3>Weekly Intake Trend (Last 8 Weeks)</h3>
            </div>
            <span className="record-count">{weeklyTrends.reduce((acc, t) => acc + t.count, 0)} total in period</span>
          </div>
          {weeklyTrends.length === 0 ? (
            <div className="chart-empty">No weekly trend data available.</div>
          ) : (
            <div className="analytics-bar-chart">
              {weeklyTrends.map((item) => {
                const heightPct = Math.max((item.count / maxWeekly) * 100, item.count > 0 ? 8 : 2);
                return (
                  <div className="analytics-bar-col" key={item.period}>
                    <div className="bar-val-label">{item.count}</div>
                    <div className="bar-track-vertical">
                      <div className="bar-fill-vertical weekly-fill" style={{ height: `${heightPct}%` }} />
                    </div>
                    <div className="bar-x-label">{item.period}</div>
                  </div>
                );
              })}
            </div>
          )}
        </section>

        {/* Status Distribution */}
        <section className="panel-card chart-panel">
          <div className="panel-heading">
            <div>
              <p className="eyebrow">Lifecycle distribution</p>
              <h3>Complaints by Status</h3>
            </div>
            <span className="record-count">{summary.total} total</span>
          </div>
          <div className="status-bars-container">
            {byStatus.map((item) => {
              const pct = summary.total > 0 ? Math.round((item.count / summary.total) * 100) : 0;
              return (
                <div className="status-row" key={item.label}>
                  <div className="status-caption">
                    <span
                      className="status-color-indicator"
                      style={{ backgroundColor: statusPalette[item.label] || '#999' }}
                    />
                    <span>{item.label.replace('_', ' ')}</span>
                    <strong>
                      {item.count} <small>({pct}%)</small>
                    </strong>
                  </div>
                  <div className="bar-track">
                    <span
                      style={{
                        width: `${pct}%`,
                        backgroundColor: statusPalette[item.label] || '#999',
                      }}
                    />
                  </div>
                </div>
              );
            })}
          </div>
        </section>

        {/* Priority Breakdown */}
        <section className="panel-card chart-panel">
          <div className="panel-heading">
            <div>
              <p className="eyebrow">Urgency levels</p>
              <h3>Complaints by Priority</h3>
            </div>
          </div>
          <div className="status-bars-container">
            {byPriority.map((item) => {
              const pct = summary.total > 0 ? Math.round((item.count / summary.total) * 100) : 0;
              return (
                <div className="status-row" key={item.label}>
                  <div className="status-caption">
                    <span
                      className="status-color-indicator"
                      style={{ backgroundColor: priorityPalette[item.label] || '#999' }}
                    />
                    <span>{item.label} Priority</span>
                    <strong>
                      {item.count} <small>({pct}%)</small>
                    </strong>
                  </div>
                  <div className="bar-track">
                    <span
                      style={{
                        width: `${pct}%`,
                        backgroundColor: priorityPalette[item.label] || '#999',
                      }}
                    />
                  </div>
                </div>
              );
            })}
          </div>
        </section>

        {/* Categories Distribution */}
        <section className="panel-card chart-panel">
          <div className="panel-heading">
            <div>
              <p className="eyebrow">Domain areas</p>
              <h3>Complaints by Category</h3>
            </div>
          </div>
          <HorizontalBars items={byCategory} emptyMessage="No complaints recorded for categories." />
        </section>

        {/* Department Distribution */}
        <section className="panel-card chart-panel">
          <div className="panel-heading">
            <div>
              <p className="eyebrow">Campus departments</p>
              <h3>Complaints by Department</h3>
            </div>
          </div>
          <HorizontalBars items={byDepartment} emptyMessage="No complaints recorded for departments." />
        </section>
      </div>

      {/* Escalation Level Breakdown */}
      <section className="panel-card mb-4">
        <div className="panel-heading">
          <div>
            <p className="eyebrow">SLA oversight</p>
            <h3>SLA Escalation Levels Summary</h3>
          </div>
        </div>
        <div className="escalation-summary-grid">
          {byEscalationLevel.map((lvl) => (
            <div className={`escalation-pill-card level-${lvl.label.replace('Level ', '')}`} key={lvl.label}>
              <span className="escalation-pill-label">{lvl.label}</span>
              <strong className="escalation-pill-count">{lvl.count}</strong>
              <small className="escalation-pill-desc">
                {lvl.label === 'Level 0'
                  ? 'Standard SLA'
                  : lvl.label === 'Level 1'
                  ? 'Staff Reminder'
                  : lvl.label === 'Level 2'
                  ? 'Dept Escalation'
                  : 'Executive Alert'}
              </small>
            </div>
          ))}
        </div>
      </section>

      {/* Staff Workload Table */}
      <section className="panel-card mb-4">
        <div className="panel-heading">
          <div>
            <p className="eyebrow">Operations & staffing</p>
            <h3>Staff Workload & Performance</h3>
          </div>
          <span className="record-count">{staffWorkload.length} staff members</span>
        </div>
        {staffWorkload.length === 0 ? (
          <div className="empty-state">
            <span className="empty-icon">—</span>
            <p>No staff workload records match the selected filters.</p>
          </div>
        ) : (
          <div className="table-responsive">
            <table className="complaint-table staff-workload-table">
              <thead>
                <tr>
                  <th>Staff Member</th>
                  <th>Department</th>
                  <th className="text-center">Active Assigned</th>
                  <th className="text-center">In Progress</th>
                  <th className="text-center">Resolved</th>
                  <th className="text-center">Total Handled</th>
                </tr>
              </thead>
              <tbody>
                {staffWorkload.map((staff) => (
                  <tr key={staff.staffId}>
                    <td>
                      <strong>{staff.staffName}</strong>
                    </td>
                    <td>{staff.departmentName || '—'}</td>
                    <td className="text-center">
                      <span className="workload-badge badge-assigned">{staff.assignedCount}</span>
                    </td>
                    <td className="text-center">
                      <span className="workload-badge badge-in-progress">{staff.inProgressCount}</span>
                    </td>
                    <td className="text-center">
                      <span className="workload-badge badge-resolved">{staff.resolvedCount}</span>
                    </td>
                    <td className="text-center">
                      <strong>{staff.totalHandled}</strong>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </DashboardLayout>
  );
}

export default AdminAnalyticsPage;
