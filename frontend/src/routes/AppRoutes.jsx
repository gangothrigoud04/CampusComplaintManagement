import { Link, Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import HomePage from '../pages/HomePage';
import StudentPortalPage from '../pages/StudentPortalPage';
import FacultyPortalPage from '../pages/FacultyPortalPage';
import FacultyComplaintsPage from '../pages/FacultyComplaintsPage';
import FacultyAnalyticsPage from '../pages/FacultyAnalyticsPage';
import StaffPortalPage from '../pages/StaffPortalPage';
import AdminPortalPage from '../pages/AdminPortalPage';
import AdminComplaintsPage from '../pages/AdminComplaintsPage';
import AdminAnalyticsPage from '../pages/AdminAnalyticsPage';
import AuditTrailPage from '../pages/AuditTrailPage';
import SlaDashboardPage from '../pages/SlaDashboardPage';
import LoginPage from '../pages/LoginPage';
import RegisterPage from '../pages/RegisterPage';
import MyComplaintsPage from '../pages/MyComplaintsPage';
import SubmitComplaintPage from '../pages/SubmitComplaintPage';
import ComplaintDetailsPage from '../pages/ComplaintDetailsPage';

function NotFoundPage() {
  return (
    <div className="container py-5">
      <div className="card border-0 shadow-sm">
        <div className="card-body p-5 text-center">
          <h1 className="display-5 fw-bold mb-3">Page not found</h1>
          <p className="text-secondary mb-4">
            The page you are looking for does not exist or may have moved.
          </p>
          <Link className="btn btn-primary" to="/">
            Return to homepage
          </Link>
        </div>
      </div>
    </div>
  );
}

function UnauthorizedPage() {
  return (
    <div className="container py-5">
      <div className="card border-0 shadow-sm">
        <div className="card-body p-5 text-center">
          <h1 className="h2 fw-bold mb-3">You do not have access to this portal</h1>
          <p className="text-secondary mb-4">Sign in with an account assigned to this role, or return to the homepage.</p>
          <Link className="btn btn-primary" to="/">Return to homepage</Link>
        </div>
      </div>
    </div>
  );
}

function ProtectedRoute({ role, children }) {
  const { user, loading } = useAuth();
  const location = useLocation();

  if (loading) {
    return <div className="dashboard-loading" role="status"><span className="button-spinner" /> Checking your session…</div>;
  }
  if (!user) {
    const loginPath = role ? `/${role.toLowerCase()}/login` : '/login';
    const sessionExpired = window.sessionStorage.getItem('campusSessionExpired') === 'true';
    return <Navigate to={loginPath} replace state={{ from: location, sessionExpired, signInRequired: !sessionExpired }} />;
  }
  if (user.role !== role) {
    return <UnauthorizedPage />;
  }
  return children;
}

function AdminOverviewRoute() {
  const location = useLocation();
  const section = location.hash === '#people' ? 'people' : 'overview';
  return <ProtectedRoute role="ADMIN"><AdminPortalPage section={section} /></ProtectedRoute>;
}

function AppRoutes() {
  return (
    <Routes>
      <Route path="/" element={<HomePage />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/student/login" element={<LoginPage role="STUDENT" />} />
      <Route path="/faculty/login" element={<LoginPage role="FACULTY" />} />
      <Route path="/staff/login" element={<LoginPage role="STAFF" />} />
      <Route path="/admin/login" element={<LoginPage role="ADMIN" />} />
      <Route path="/student/register" element={<RegisterPage />} />
      <Route path="/unauthorized" element={<UnauthorizedPage />} />
      <Route path="/student" element={<ProtectedRoute role="STUDENT"><StudentPortalPage /></ProtectedRoute>} />
      <Route path="/student/dashboard" element={<ProtectedRoute role="STUDENT"><StudentPortalPage /></ProtectedRoute>} />
      <Route path="/student/complaints" element={<ProtectedRoute role="STUDENT"><MyComplaintsPage /></ProtectedRoute>} />
      <Route path="/student/complaints/new" element={<ProtectedRoute role="STUDENT"><SubmitComplaintPage /></ProtectedRoute>} />
      <Route path="/student/complaints/:id" element={<ProtectedRoute role="STUDENT"><ComplaintDetailsPage /></ProtectedRoute>} />
      <Route path="/faculty" element={<ProtectedRoute role="FACULTY"><FacultyPortalPage /></ProtectedRoute>} />
      <Route path="/faculty/dashboard" element={<ProtectedRoute role="FACULTY"><FacultyPortalPage /></ProtectedRoute>} />
      <Route path="/faculty/my-complaints" element={<ProtectedRoute role="FACULTY"><MyComplaintsPage /></ProtectedRoute>} />
      <Route path="/faculty/complaints/new" element={<ProtectedRoute role="FACULTY"><SubmitComplaintPage /></ProtectedRoute>} />
      <Route path="/faculty/complaints" element={<ProtectedRoute role="FACULTY"><FacultyComplaintsPage /></ProtectedRoute>} />
      <Route path="/faculty/reports" element={<ProtectedRoute role="FACULTY"><FacultyComplaintsPage reports /></ProtectedRoute>} />
      <Route path="/faculty/analytics" element={<ProtectedRoute role="FACULTY"><FacultyAnalyticsPage /></ProtectedRoute>} />
      <Route path="/faculty/complaints/:id" element={<ProtectedRoute role="FACULTY"><ComplaintDetailsPage /></ProtectedRoute>} />
      <Route path="/staff" element={<ProtectedRoute role="STAFF"><StaffPortalPage /></ProtectedRoute>} />
      <Route path="/staff/dashboard" element={<ProtectedRoute role="STAFF"><StaffPortalPage /></ProtectedRoute>} />
      <Route path="/staff/complaints/:id" element={<ProtectedRoute role="STAFF"><ComplaintDetailsPage /></ProtectedRoute>} />
      <Route path="/admin" element={<Navigate to="/admin/dashboard" replace />} />
      <Route path="/admin/dashboard" element={<AdminOverviewRoute />} />
      <Route path="/admin/analytics" element={<ProtectedRoute role="ADMIN"><AdminAnalyticsPage /></ProtectedRoute>} />
      <Route path="/admin/people" element={<ProtectedRoute role="ADMIN"><AdminPortalPage section="people" /></ProtectedRoute>} />
      <Route path="/admin/complaints" element={<ProtectedRoute role="ADMIN"><AdminComplaintsPage /></ProtectedRoute>} />
      <Route path="/admin/sla" element={<ProtectedRoute role="ADMIN"><SlaDashboardPage /></ProtectedRoute>} />
      <Route path="/admin/audit-trail" element={<ProtectedRoute role="ADMIN"><AuditTrailPage /></ProtectedRoute>} />
      <Route path="/admin/complaints/:id" element={<ProtectedRoute role="ADMIN"><ComplaintDetailsPage /></ProtectedRoute>} />
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}

export default AppRoutes;
