import { useState } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import AppIcon from '../components/AppIcon';
import { useAuth } from '../context/AuthContext';
import { getLoginErrorMessage } from '../services/api';

const roles = {
  STUDENT: { label: 'Student', icon: 'student', home: '/student/dashboard' },
  FACULTY: { label: 'Faculty', icon: 'graduation', home: '/faculty/dashboard' },
  STAFF: { label: 'Staff', icon: 'staff', home: '/staff/dashboard' },
  ADMIN: { label: 'Administrator', icon: 'admin', home: '/admin/dashboard' },
};

function LoginPage({ role }) {
  const { login, logout, loading, user } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const roleInfo = roles[role] || null;

  if (!loading && user) {
    if (roleInfo && user.role !== role) return <Navigate to="/unauthorized" replace />;
    return <Navigate to={roles[user.role]?.home || '/'} replace />;
  }

  const handleSubmit = async (event) => {
    event.preventDefault();
    if (submitting) return;
    setError('');
    setSubmitting(true);
    try {
      const account = await login({ email: email.trim(), password });
      if (roleInfo && account.role !== role) {
        logout();
        setError(`This sign-in page is for ${roleInfo.label} accounts. This account has a different role.`);
        return;
      }
      const requested = location.state?.from?.pathname;
      navigate(requested || roles[account.role]?.home || '/', { replace: true });
    } catch (requestError) {
      if (import.meta.env.DEV && requestError.response?.status >= 500) {
        console.error('Sign-in service request failed', {
          status: requestError.response?.status ?? null,
          code: requestError.response?.data?.error ?? requestError.code ?? 'unknown',
        });
      } else if (import.meta.env.DEV && !requestError.response) {
        console.error('Sign-in service request failed', {
          status: null,
          code: requestError.code ?? 'network_error',
        });
      }
      setError(getLoginErrorMessage(requestError));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className={`auth-layout${roleInfo ? ` auth-${role.toLowerCase()}` : ''}`}>
      <div className="auth-side-panel">
        <div className="auth-side-inner">
          <Link className="auth-brand" to="/">
            <span className="brand-mark"><AppIcon name="building" size={20} /></span>
            CampusCare
          </Link>
          <div className="auth-side-copy">
            <span className="auth-side-icon"><AppIcon name={roleInfo?.icon || 'admin'} size={30} /></span>
            <p className="eyebrow">A better campus, together</p>
            <h1>{roleInfo ? `${roleInfo.label} workspace` : 'Welcome back to CampusCare'}</h1>
            <p>Secure access to your role-based campus services and the information you are authorized to view.</p>
          </div>
          <div className="auth-side-foot">Campus reports stay private to the people who need access.</div>
        </div>
      </div>
      <section className="auth-main">
        <div className="auth-form-wrap">
          <p className="eyebrow">{roleInfo ? `${roleInfo.label} sign in` : 'Account access'}</p>
          <h2>Sign in to continue</h2>
          <p className="auth-subtitle">Enter the email and password associated with your account.</p>
          {location.state?.signInRequired && (
            <div className="form-alert form-alert-warning" role="alert">
              Sign in to continue to the page you requested.
            </div>
          )}
          {location.state?.sessionExpired && (
            <div className="form-alert form-alert-warning" role="alert">
              Your session has expired or is no longer valid. Please sign in again.
            </div>
          )}
          {error && <div className="form-alert form-alert-error" role="alert">{error}</div>}
          <form className="auth-form" onSubmit={handleSubmit}>
            <div className="field-group">
              <label htmlFor="login-email">Email address</label>
              <input id="login-email" type="email" autoComplete="username" maxLength="254" value={email}
                onChange={(event) => setEmail(event.target.value)} required />
            </div>
            <div className="field-group">
              <label htmlFor="login-password">Password</label>
              <input id="login-password" type="password" autoComplete="current-password" maxLength="72"
                value={password} onChange={(event) => setPassword(event.target.value)} required />
            </div>
            <button className="button button-primary button-large auth-submit" type="submit" disabled={submitting}>
              {submitting ? <><span className="button-spinner" /> Signing in…</> : 'Sign in'}
              {!submitting && <AppIcon name="arrow" size={18} />}
            </button>
          </form>
          {(!roleInfo || role === 'STUDENT') && (
            <p className="auth-switch">New to CampusCare? <Link to="/student/register">Create a student account</Link></p>
          )}
          <Link className="auth-home-link" to="/">Back to homepage</Link>
        </div>
      </section>
    </div>
  );
}

export default LoginPage;
