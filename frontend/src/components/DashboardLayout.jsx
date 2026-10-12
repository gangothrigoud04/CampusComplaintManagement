import { NavLink, useLocation } from 'react-router-dom';
import AppIcon from './AppIcon';
import NotificationBell from './NotificationBell';

const roleLabels = {
  STUDENT: 'Student workspace',
  FACULTY: 'Faculty workspace',
  STAFF: 'Staff workspace',
  ADMIN: 'Administration',
};

function DashboardLayout({ role, title, eyebrow, actions, children }) {
  const location = useLocation();
  const navigation = {
    STUDENT: [
      { label: 'Overview', to: '/student/dashboard', icon: 'chart', end: true },
      { label: 'My complaints', to: '/student/complaints', icon: 'clipboard' },
      { label: 'Submit a complaint', to: '/student/complaints/new', icon: 'plus' },
    ],
    FACULTY: [
      { label: 'Overview', to: '/faculty/dashboard', icon: 'chart', end: true },
      { label: 'My complaints', to: '/faculty/my-complaints', icon: 'clipboard' },
      { label: 'Submit complaint', to: '/faculty/complaints/new', icon: 'plus' },
      { label: 'Student complaints', to: '/faculty/complaints', icon: 'clipboard' },
      { label: 'Analytics', to: '/faculty/analytics', icon: 'chart' },
      { label: 'Reports', to: '/faculty/reports', icon: 'download' },
    ],
    STAFF: [{ label: 'Assigned complaints', to: '/staff/dashboard', icon: 'clipboard', end: true }],
    ADMIN: [
      { label: 'Overview', to: '/admin/dashboard', icon: 'chart', end: true },
      { label: 'Analytics & Reports', to: '/admin/analytics', icon: 'chart' },
      { label: 'All complaints', to: '/admin/complaints', icon: 'clipboard' },
      { label: 'SLA Overview', to: '/admin/sla', icon: 'clock' },
      { label: 'Audit Trail', to: '/admin/audit-trail', icon: 'clock' },
      { label: 'People & departments', to: '/admin/people', icon: 'users' },
    ],
  }[role];

  return (
    <div className={`dashboard-shell dashboard-${role.toLowerCase()}`}>
      <aside className="dashboard-sidebar" aria-label={`${roleLabels[role]} navigation`}>
        <div className="sidebar-caption">{roleLabels[role]}</div>
        <nav className="sidebar-nav">
          {navigation.map((item) => (
            <NavLink
              key={item.label}
              className={({ isActive }) => {
                const isOverview = role === 'ADMIN' && item.to === '/admin/dashboard';
                const isLegacyPeopleLink = role === 'ADMIN'
                  && item.to === '/admin/people'
                  && location.pathname === '/admin/dashboard'
                  && location.hash === '#people';
                const active = isLegacyPeopleLink || (isActive && (!isOverview || location.hash !== '#people'));
                return `sidebar-link${active ? ' active' : ''}`;
              }}
              to={item.to}
              end={item.end}
            >
              <AppIcon name={item.icon} size={18} />
              <span>{item.label}</span>
            </NavLink>
          ))}
        </nav>
        <div className="sidebar-footnote">
          <span className="connection-indicator" />
          Campus services workspace
        </div>
      </aside>
      <section className="dashboard-content">
        <div className="page-heading">
          <div>
            <p className="eyebrow">{eyebrow || roleLabels[role]}</p>
            <h1>{title}</h1>
          </div>
          <div className="page-actions">
            <NotificationBell role={role} />
            {actions}
          </div>
        </div>
        {children}
      </section>
    </div>
  );
}

export default DashboardLayout;
