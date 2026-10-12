import { useEffect, useState } from 'react';
import { Link, Navigate } from 'react-router-dom';
import AppIcon from '../components/AppIcon';
import { useAuth } from '../context/AuthContext';
import api, { getApiErrorMessage } from '../services/api';

const initialValues = {
  name: '',
  rollNumber: '',
  departmentId: '',
  email: '',
  password: '',
};

function RegisterPage() {
  const { register, loading, user } = useAuth();
  const [values, setValues] = useState(initialValues);
  const [departments, setDepartments] = useState([]);
  const [departmentsLoading, setDepartmentsLoading] = useState(true);
  const [departmentError, setDepartmentError] = useState('');
  const [error, setError] = useState('');
  const [registered, setRegistered] = useState(false);
  const [departmentRetry, setDepartmentRetry] = useState(0);
  const [submitting, setSubmitting] = useState(false);
  const [showPassword, setShowPassword] = useState(false);

  useEffect(() => {
    let active = true;
    setDepartmentsLoading(true);
    setDepartmentError('');
    api.get('/auth/departments')
      .then(({ data }) => {
        if (!Array.isArray(data)) throw new Error('Department service returned an invalid response.');
        if (active) setDepartments(data);
      })
      .catch((requestError) => {
        if (active) {
          setDepartments([]);
          setDepartmentError(requestError.response?.data?.message
            || (requestError.response ? 'Department options could not be loaded.' : 'Could not connect to the department service. Check that the backend is running and retry.'));
        }
      })
      .finally(() => {
        if (active) setDepartmentsLoading(false);
      });
    return () => { active = false; };
  }, [departmentRetry]);

  if (!loading && user) return <Navigate to="/student/dashboard" replace />;

  const update = (event) => {
    setValues((current) => ({ ...current, [event.target.name]: event.target.value }));
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    if (submitting) return;
    setError('');
    const details = Object.fromEntries(Object.entries(values).map(([key, value]) => [
      key,
      key === 'password' ? value : value.trim(),
    ]));
    if (!details.name || !details.rollNumber || !details.departmentId || !details.email || !details.password) {
      setError('Complete all five fields to create your student account.');
      return;
    }
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(details.email)) {
      setError('Enter a valid email address.');
      return;
    }
    if (details.password.length < 12 || !/[A-Za-z]/.test(details.password) || !/\d/.test(details.password)) {
      setError('Use at least 12 characters with at least one letter and one number.');
      return;
    }

    setSubmitting(true);
    try {
      const result = await register({
        ...details,
        departmentId: Number(details.departmentId),
      });
      if (result.user?.role !== 'STUDENT') {
        throw new Error('Registration returned an unexpected account role. Please contact support.');
      }
      setValues(initialValues);
      setRegistered(true);
    } catch (requestError) {
      setError(getApiErrorMessage(requestError, 'We could not create your account. Check the submitted details and try again.'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="registration-layout">
      <aside className="registration-aside">
        <Link className="auth-brand" to="/">
          <span className="brand-mark"><AppIcon name="building" size={20} /></span>
          CampusCare
        </Link>
        <div className="registration-aside-copy">
          <span className="auth-side-icon"><AppIcon name="student" size={30} /></span>
          <p className="eyebrow">Your campus, your voice</p>
          <h1>One account. A clearer way to be heard.</h1>
          <p>Create a student account to submit campus concerns and follow your own reports.</p>
          <div className="registration-promise"><AppIcon name="check" size={17} /> Your reports are tied to your verified account.</div>
        </div>
      </aside>
      <section className="registration-main">
        <div className="registration-form-wrap">
          <p className="eyebrow">Student registration</p>
          <h2>Create your account</h2>
          <p className="auth-subtitle">All five fields are required. Your role is set to Student automatically.</p>
          {registered && (
            <div className="form-alert form-alert-success" role="status">
              Your student account was created. <Link to="/student/login">Sign in to your account</Link>.
            </div>
          )}
          {error && <div className="form-alert form-alert-error" role="alert">{error}</div>}
          {departmentError && (
            <div className="form-alert form-alert-error" role="alert">
              {departmentError}{' '}
              <button className="inline-retry" type="button" onClick={() => setDepartmentRetry((value) => value + 1)} disabled={departmentsLoading}>
                {departmentsLoading ? 'Loading…' : 'Retry'}
              </button>
            </div>
          )}
          {!registered && <form className="registration-form" onSubmit={handleSubmit}>
            <div className="field-group">
              <label htmlFor="register-name">Full Name</label>
              <input id="register-name" name="name" type="text" autoComplete="name" maxLength="160"
                value={values.name} onChange={update} required />
            </div>
            <div className="field-group">
              <label htmlFor="register-roll">Roll Number</label>
              <input id="register-roll" name="rollNumber" type="text" autoComplete="off" maxLength="40"
                value={values.rollNumber} onChange={update} required />
              <small>Letters, numbers, and leading zeroes are preserved.</small>
            </div>
            <div className="field-group">
              <label htmlFor="register-department">Department</label>
              <select id="register-department" name="departmentId" value={values.departmentId}
                onChange={update} disabled={departmentsLoading || departments.length === 0} required>
                <option value="">{departmentsLoading ? 'Loading departments…' : departments.length === 0 ? 'No departments available' : 'Select your department'}</option>
                {departments.map((department) => <option key={department.id} value={department.id}>{department.name}</option>)}
              </select>
              {!departmentsLoading && departments.length === 0 && !departmentError && (
                <small>No active departments are configured. Ask an administrator to set up an official department before registering.</small>
              )}
            </div>
            <div className="field-group">
              <label htmlFor="register-email">Email Address</label>
              <input id="register-email" name="email" type="email" autoComplete="email" maxLength="254"
                value={values.email} onChange={update} required />
            </div>
            <div className="field-group">
              <label htmlFor="register-password">Password</label>
              <div className="password-wrap">
                <input id="register-password" name="password" type={showPassword ? 'text' : 'password'}
                  autoComplete="new-password" minLength="12" maxLength="72"
                  value={values.password} onChange={update} required />
                <button className="password-toggle" type="button" onClick={() => setShowPassword((visible) => !visible)}
                  aria-label={showPassword ? 'Hide password' : 'Show password'}>
                  {showPassword ? 'Hide' : 'Show'}
                </button>
              </div>
              <small>At least 12 characters, including a letter and a number.</small>
            </div>
            <button className="button button-primary button-large auth-submit" type="submit"
              disabled={submitting || departmentsLoading || departments.length === 0 || Boolean(departmentError)}>
              {submitting ? <><span className="button-spinner" /> Creating account…</> : 'Create student account'}
              {!submitting && <AppIcon name="arrow" size={18} />}
            </button>
          </form>}
          <p className="auth-switch">Already registered? <Link to="/student/login">Student login</Link></p>
          <Link className="auth-home-link" to="/">Back to homepage</Link>
        </div>
      </section>
    </div>
  );
}

export default RegisterPage;
