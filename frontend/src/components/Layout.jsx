import { Link, NavLink } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import AppIcon from './AppIcon';

const portals = [
  { label: 'Student', to: '/student/login' },
  { label: 'Faculty', to: '/faculty/login' },
  { label: 'Staff', to: '/staff/login' },
  { label: 'Admin', to: '/admin/login' },
];

const dashboardForRole = {
  STUDENT: '/student/dashboard',
  FACULTY: '/faculty/dashboard',
  STAFF: '/staff/dashboard',
  ADMIN: '/admin/dashboard',
};

function Layout({ children }) {
  const { user, loading, logout } = useAuth();

  return (
    <div className="app-shell">
      <header className="site-header">
        <nav className="top-navigation">
          <div className="top-navigation-inner">
            <Link className="brand-lockup" to="/" aria-label="CampusCare home">
              <span className="brand-mark"><AppIcon name="building" size={20} /></span>
              <span>Campus<span className="brand-light">Care</span></span>
            </Link>
            <div className="top-nav-links">
              <NavLink to="/" end className={({ isActive }) => `top-nav-link${isActive ? ' active' : ''}`}>Home</NavLink>
              {portals.map((portal) => (
                <NavLink
                  key={portal.label}
                  to={portal.to}
                  className={({ isActive }) => `top-nav-link${isActive ? ' active' : ''}`}
                >
                  {portal.label} Portal
                </NavLink>
              ))}
            </div>
            <div className="top-nav-actions">
              {!loading && !user && (
                <>
                  <Link className="nav-login-link" to="/student/login">Log in</Link>
                  <Link className="button button-primary button-small" to="/student/register">Create account</Link>
                </>
              )}
              {!loading && user && (
                <>
                  <Link className="signed-in-user" to={dashboardForRole[user.role] || '/'}>
                    <span className="user-avatar">{user.name?.slice(0, 1)?.toUpperCase() || 'U'}</span>
                    <span>{user.name}<small>{user.role}</small></span>
                  </Link>
                  <button className="icon-button logout-button" type="button" onClick={logout} aria-label="Log out" title="Log out">
                    <AppIcon name="logout" size={18} />
                  </button>
                </>
              )}
            </div>
          </div>
        </nav>
      </header>

      <main>{children}</main>

      <footer className="site-footer">
        <div className="footer-inner">
          <Link className="brand-lockup footer-brand" to="/">
            <span className="brand-mark"><AppIcon name="building" size={19} /></span>
            <span>Campus<span className="brand-light">Care</span></span>
          </Link>
          <p>A clearer way to care for the place we learn and work.</p>
          <div className="footer-links">
            <Link to="/">Home</Link>
            <Link to="/student/register">Student registration</Link>
            <Link to="/student/login">Portal access</Link>
          </div>
          <span className="footer-copyright">© {new Date().getFullYear()} CampusCare</span>
        </div>
      </footer>
    </div>
  );
}

export default Layout;
