import { Fragment, useEffect, useMemo, useState } from 'react';
import { useLocation } from 'react-router-dom';
import AppIcon from '../components/AppIcon';
import ComplaintTable from '../components/ComplaintTable';
import DashboardLayout from '../components/DashboardLayout';
import HorizontalBars from '../components/HorizontalBars';
import MetricCard from '../components/MetricCard';
import StatusDonut from '../components/StatusDonut';
import api from '../services/api';
import { summarizeAdminComplaints } from '../utils/complaints';

const initialProvision = { name: '', email: '', password: '', role: 'STAFF', departmentId: '' };

function currentMonthLabel(date) {
  return new Intl.DateTimeFormat(undefined, { month: 'short' }).format(date);
}

function AdminPortalPage({ section = 'overview' }) {
  const location = useLocation();
  const isPeopleSection = section === 'people' || location.hash === '#people';
  const [complaints, setComplaints] = useState([]);
  const [users, setUsers] = useState([]);
  const [departments, setDepartments] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [query, setQuery] = useState('');
  const [complaintQuery, setComplaintQuery] = useState('');
  const [roleFilter, setRoleFilter] = useState('');
  const [provision, setProvision] = useState(initialProvision);
  const [departmentForm, setDepartmentForm] = useState({ name: '', description: '' });
  const [editingUser, setEditingUser] = useState(null);
  const [userEditForm, setUserEditForm] = useState(null);
  const [editingDepartment, setEditingDepartment] = useState(null);
  const [departmentEditForm, setDepartmentEditForm] = useState(null);
  const [savingUser, setSavingUser] = useState(false);
  const [savingDepartment, setSavingDepartment] = useState(false);
  const [notice, setNotice] = useState('');
  const [formError, setFormError] = useState('');

  const loadDashboard = async () => {
    setLoading(true);
    setError('');
    try {
      const [complaintResponse, userResponse, departmentResponse] = await Promise.all([
        api.get('/complaints'),
        api.get('/admin/users'),
        api.get('/admin/departments'),
      ]);
      setComplaints(complaintResponse.data);
      setUsers(userResponse.data);
      setDepartments(departmentResponse.data);
    } catch (requestError) {
      setError(requestError.response?.data?.message || 'Administrative data could not be loaded. Please retry.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { loadDashboard(); }, []);

  const filteredUsers = useMemo(() => users.filter((user) => {
    const matchesText = `${user.name} ${user.email} ${user.role}`.toLowerCase().includes(query.toLowerCase());
    return matchesText && (!roleFilter || user.role === roleFilter);
  }), [users, query, roleFilter]);

  const filteredComplaints = useMemo(() => complaints.filter((complaint) => (
    `${complaint.complaintId} ${complaint.title} ${complaint.category} ${complaint.status}`
      .toLowerCase().includes(complaintQuery.toLowerCase())
  )), [complaints, complaintQuery]);

  const metrics = summarizeAdminComplaints(complaints);
  const categories = Object.entries(complaints.reduce((result, complaint) => {
    result[complaint.category] = (result[complaint.category] || 0) + 1;
    return result;
  }, {})).map(([label, value]) => ({ label, value })).sort((a, b) => b.value - a.value).slice(0, 6);
  const priorities = ['URGENT', 'HIGH', 'MEDIUM', 'LOW'].map((label) => ({
    label,
    value: complaints.filter((complaint) => complaint.priority === label).length,
  })).filter((item) => item.value > 0);
  const departmentCounts = departments.map((department) => ({
    label: department.name,
    value: users.filter((user) => user.departmentId === department.id).length,
  })).filter((item) => item.value > 0).sort((a, b) => b.value - a.value).slice(0, 6);
  const monthlyIntake = Array.from({ length: 6 }, (_, offset) => {
    const month = new Date();
    month.setDate(1);
    month.setMonth(month.getMonth() - (5 - offset));
    const year = month.getFullYear();
    const monthIndex = month.getMonth();
    return {
      label: currentMonthLabel(month),
      value: complaints.filter((complaint) => {
        const submitted = new Date(complaint.submittedAt);
        return submitted.getFullYear() === year && submitted.getMonth() === monthIndex;
      }).length,
    };
  });

  const submitProvision = async (event) => {
    event.preventDefault();
    if (savingUser) return;
    setFormError('');
    setNotice('');
    setSavingUser(true);
    try {
      await api.post('/admin/users', {
        ...provision,
        name: provision.name.trim(),
        email: provision.email.trim(),
        ...(provision.departmentId ? { departmentId: Number(provision.departmentId) } : { departmentId: null }),
      });
      setProvision(initialProvision);
      setNotice(`${provision.role === 'STAFF' ? 'Staff' : 'Faculty'} account created successfully.`);
      const { data } = await api.get('/admin/users');
      setUsers(data);
    } catch (requestError) {
      setFormError(requestError.response?.data?.message || 'Account provisioning failed.');
    } finally {
      setSavingUser(false);
    }
  };

  const submitDepartment = async (event) => {
    event.preventDefault();
    if (savingDepartment) return;
    setFormError('');
    setNotice('');
    setSavingDepartment(true);
    try {
      await api.post('/admin/departments', {
        name: departmentForm.name.trim(),
        description: departmentForm.description.trim() || null,
      });
      setDepartmentForm({ name: '', description: '' });
      setNotice('Department created and made available for student registration.');
      const { data } = await api.get('/admin/departments');
      setDepartments(data);
    } catch (requestError) {
      setFormError(requestError.response?.data?.message || 'Department could not be created.');
    } finally {
      setSavingDepartment(false);
    }
  };

  const startEditingUser = (account) => {
    setEditingUser(account.id);
    setUserEditForm({
      name: account.name || '',
      email: account.email || '',
      departmentId: account.departmentId == null ? '' : String(account.departmentId),
      rollNumber: account.rollNumber || '',
      className: account.className || '',
      section: account.section || '',
      role: account.role,
    });
    setFormError('');
    setNotice('');
  };

  const submitUserUpdate = async (event) => {
    event.preventDefault();
    setSavingUser(true);
    setFormError('');
    setNotice('');
    try {
      await api.put(`/admin/users/${editingUser}`, {
        name: userEditForm.name.trim(),
        email: userEditForm.email.trim(),
        departmentId: userEditForm.departmentId ? Number(userEditForm.departmentId) : null,
        rollNumber: userEditForm.rollNumber.trim() || null,
        className: userEditForm.className.trim() || null,
        section: userEditForm.section.trim() || null,
      });
      setNotice('Account details updated.');
      setEditingUser(null);
      setUserEditForm(null);
      const { data } = await api.get('/admin/users');
      setUsers(data);
    } catch (requestError) {
      setFormError(requestError.response?.data?.message || 'Account details could not be updated.');
    } finally {
      setSavingUser(false);
    }
  };

  const deactivateUser = async (account) => {
    if (!window.confirm(`Deactivate ${account.name}'s account? This preserves their complaint history and prevents sign-in.`)) return;
    setFormError('');
    setNotice('');
    try {
      await api.patch(`/admin/users/${account.id}/deactivate`);
      setNotice(`${account.name}'s account was deactivated.`);
      const { data } = await api.get('/admin/users');
      setUsers(data);
    } catch (requestError) {
      setFormError(requestError.response?.data?.message || 'Account could not be deactivated.');
    }
  };

  const activateUser = async (account) => {
    setFormError('');
    setNotice('');
    try {
      await api.patch(`/admin/users/${account.id}/activate`);
      setNotice(`${account.name}'s account was reactivated.`);
      const { data } = await api.get('/admin/users');
      setUsers(data);
    } catch (requestError) {
      setFormError(requestError.response?.data?.message || 'Account could not be reactivated.');
    }
  };

  const startEditingDepartment = (department) => {
    setEditingDepartment(department.id);
    setDepartmentEditForm({ name: department.name, description: department.description || '' });
  };

  const submitDepartmentUpdate = async (event) => {
    event.preventDefault();
    setSavingDepartment(true);
    setFormError('');
    setNotice('');
    try {
      await api.put(`/admin/departments/${editingDepartment}`, {
        name: departmentEditForm.name.trim(),
        description: departmentEditForm.description.trim() || null,
      });
      setNotice('Department details updated.');
      setEditingDepartment(null);
      setDepartmentEditForm(null);
      const { data } = await api.get('/admin/departments');
      setDepartments(data);
    } catch (requestError) {
      setFormError(requestError.response?.data?.message || 'Department could not be updated.');
    } finally {
      setSavingDepartment(false);
    }
  };

  const deactivateDepartment = async (department) => {
    if (!window.confirm(`Deactivate ${department.name}? Existing accounts and complaints will retain this department, but it will no longer be available for registration.`)) return;
    setFormError('');
    setNotice('');
    try {
      await api.patch(`/admin/departments/${department.id}/deactivate`);
      setNotice(`${department.name} was deactivated.`);
      const { data } = await api.get('/admin/departments');
      setDepartments(data);
    } catch (requestError) {
      setFormError(requestError.response?.data?.message || 'Department could not be deactivated.');
    }
  };

  const activateDepartment = async (department) => {
    setFormError('');
    setNotice('');
    try {
      await api.patch(`/admin/departments/${department.id}/activate`);
      setNotice(`${department.name} is available for registration again.`);
      const { data } = await api.get('/admin/departments');
      setDepartments(data);
    } catch (requestError) {
      setFormError(requestError.response?.data?.message || 'Department could not be reactivated.');
    }
  };

  return (
    <DashboardLayout role="ADMIN"
      title={isPeopleSection ? 'People & departments' : 'Campus overview'}
      eyebrow={isPeopleSection ? 'Administration · People & departments' : 'Administration'}
      actions={<button className="button button-secondary button-small" type="button" onClick={loadDashboard} disabled={loading}>{loading ? 'Refreshing…' : 'Refresh data'}</button>}>
      {notice && <div className="form-alert form-alert-success" role="status">{notice}</div>}
      {formError && <div className="form-alert form-alert-error" role="alert">{formError}</div>}
      {error && <div className="form-alert form-alert-error" role="alert">{error} <button className="inline-retry" onClick={loadDashboard}>Retry</button></div>}
      {loading && <div className="dashboard-loading" role="status"><span className="button-spinner" /> Loading campus data…</div>}
      {!loading && !error && (
        <>
          {!isPeopleSection && <>
          <div className="dashboard-welcome admin-welcome">
            <div><h2>Campus operations at a glance.</h2><p>Overview metrics are calculated from current complaint and account records.</p></div>
            <span className="role-chip"><AppIcon name="admin" size={16} /> Administrator</span>
          </div>
          <div className="metric-grid">
            <MetricCard label="Total complaints" value={metrics.total} detail="Records in the register" icon="clipboard" tone="blue" />
            <MetricCard label="Submitted" value={metrics.submitted} detail="Awaiting initial review" icon="clock" tone="amber" />
            <MetricCard label="In progress" value={metrics.inProgress} detail="Assigned or being handled" icon="chart" tone="violet" />
            <MetricCard label="Resolved" value={metrics.resolved} detail="Resolved or closed" icon="check" tone="green" />
          </div>

          <div className="dashboard-grid dashboard-grid-equal">
            <section className="panel-card">
              <div className="panel-heading"><div><p className="eyebrow">Complaint register</p><h2>Complaints by status</h2></div></div>
              <StatusDonut complaints={complaints} />
            </section>
            <section className="panel-card">
              <div className="panel-heading"><div><p className="eyebrow">Intake</p><h2>Reports received</h2></div></div>
              <HorizontalBars items={monthlyIntake} emptyMessage="No complaint submission data is available." />
              <p className="chart-note">Counts use submitted timestamps. Resolution dates are not available in the current API.</p>
            </section>
          </div>

          <div className="dashboard-grid dashboard-grid-equal">
            <section className="panel-card">
              <div className="panel-heading"><div><p className="eyebrow">Issue patterns</p><h2>Complaints by category</h2></div></div>
              <HorizontalBars items={categories} emptyMessage="Category data will appear as complaints are submitted." />
            </section>
            <section className="panel-card">
              <div className="panel-heading"><div><p className="eyebrow">Account directory</p><h2>Users by department</h2></div></div>
              <HorizontalBars items={departmentCounts} emptyMessage="No department-linked accounts are available yet." />
            </section>
          </div>

          <section className="panel-card" id="complaints">
            <div className="panel-heading"><div><p className="eyebrow">Read-only register</p><h2>All complaints</h2></div><span className="record-count">{filteredComplaints.length} records</span></div>
            <div className="filter-row">
              <label className="search-field"><span className="visually-hidden">Search complaints</span>
                <input type="search" placeholder="Search ID or title" value={complaintQuery} onChange={(event) => setComplaintQuery(event.target.value)} />
              </label>
              {complaintQuery && <button className="button button-quiet button-small" type="button" onClick={() => setComplaintQuery('')}>Reset</button>}
            </div>
            <ComplaintTable complaints={filteredComplaints} detailBase="/admin/complaints" emptyMessage="No complaints are recorded yet." />
            <p className="chart-note">Assignment and status changes are not available in the current workflow.</p>
          </section>
          </>}

          {isPeopleSection && <section className="panel-card" id="people">
            <div className="panel-heading"><div><p className="eyebrow">Authorized account management</p><h2>People and departments</h2></div><span className="record-count">{users.length} accounts · {departments.length} departments</span></div>
            <div className="admin-management-grid">
              <form className="management-form" onSubmit={submitProvision}>
                <h3>Provision Staff or Faculty</h3>
                <p>Only Staff and Faculty roles can be created here. Admin accounts require the controlled bootstrap process.</p>
                <div className="field-group"><label htmlFor="provision-name">Full name</label><input id="provision-name" value={provision.name} onChange={(event) => setProvision({ ...provision, name: event.target.value })} required maxLength="160" /></div>
                <div className="field-group"><label htmlFor="provision-email">Email</label><input id="provision-email" type="email" value={provision.email} onChange={(event) => setProvision({ ...provision, email: event.target.value })} required maxLength="254" /></div>
                <div className="field-group"><label htmlFor="provision-password">Initial password</label><input id="provision-password" type="password" value={provision.password} onChange={(event) => setProvision({ ...provision, password: event.target.value })} required minLength="12" maxLength="72" /><small>At least 12 characters with a letter and a number.</small></div>
                <div className="field-row">
                  <div className="field-group"><label htmlFor="provision-role">Role</label><select id="provision-role" value={provision.role} onChange={(event) => setProvision({ ...provision, role: event.target.value })}><option value="STAFF">Staff</option><option value="FACULTY">Faculty</option></select></div>
                  <div className="field-group"><label htmlFor="provision-department">Department</label><select id="provision-department" value={provision.departmentId} onChange={(event) => setProvision({ ...provision, departmentId: event.target.value })}><option value="">No department</option>{departments.filter((item) => item.active).map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></div>
                </div>
                <button className="button button-primary button-small" type="submit" disabled={savingUser}>{savingUser ? 'Creating account…' : 'Create account'}</button>
              </form>
              <form className="management-form" onSubmit={submitDepartment}>
                <h3>Set up a department</h3>
                <p>Departments created here appear in the required Student registration selector.</p>
                <div className="field-group"><label htmlFor="department-name">Department name</label><input id="department-name" value={departmentForm.name} onChange={(event) => setDepartmentForm({ ...departmentForm, name: event.target.value })} required maxLength="120" /></div>
                <div className="field-group"><label htmlFor="department-description">Description <span className="optional-label">Optional</span></label><textarea id="department-description" rows="3" maxLength="500" value={departmentForm.description} onChange={(event) => setDepartmentForm({ ...departmentForm, description: event.target.value })} /></div>
                <button className="button button-secondary button-small" type="submit" disabled={savingDepartment}>{savingDepartment ? 'Saving…' : 'Add department'}</button>
                <div className="department-list">
                  {departments.length === 0 && <p className="chart-empty">No departments configured yet.</p>}
                  {departments.map((department) => <div className="department-list-item" key={department.id}>
                    {editingDepartment === department.id ? (
                      <form className="department-edit-form" onSubmit={submitDepartmentUpdate}>
                        <input aria-label="Department name" value={departmentEditForm.name} maxLength="120" required onChange={(event) => setDepartmentEditForm({ ...departmentEditForm, name: event.target.value })} />
                        <input aria-label="Department description" value={departmentEditForm.description} maxLength="500" onChange={(event) => setDepartmentEditForm({ ...departmentEditForm, description: event.target.value })} />
                        <button className="button button-primary button-small" type="submit" disabled={savingDepartment}>{savingDepartment ? 'Saving…' : 'Save'}</button>
                        <button className="button button-quiet button-small" type="button" onClick={() => setEditingDepartment(null)}>Cancel</button>
                      </form>
                    ) : (
                      <>
                        <span><AppIcon name="building" size={16} /> {department.name}<small>{department.active ? 'Active' : 'Inactive'}</small></span>
                        <div className="management-actions">
                          <button className="button button-quiet button-small" type="button" onClick={() => startEditingDepartment(department)}>Edit</button>
                          {department.active && <button className="button button-quiet button-small" type="button" onClick={() => deactivateDepartment(department)}>Deactivate</button>}
                          {!department.active && <button className="button button-quiet button-small" type="button" onClick={() => activateDepartment(department)}>Reactivate</button>}
                        </div>
                      </>
                    )}
                  </div>)}
                </div>
              </form>
            </div>
            <div className="filter-row management-filters">
              <label className="search-field"><span className="visually-hidden">Search users</span><input type="search" placeholder="Search name, email or role" value={query} onChange={(event) => setQuery(event.target.value)} /></label>
              <select aria-label="Filter accounts by role" value={roleFilter} onChange={(event) => setRoleFilter(event.target.value)}><option value="">All roles</option>{['STUDENT', 'FACULTY', 'STAFF', 'ADMIN'].map((role) => <option value={role} key={role}>{role}</option>)}</select>
              {(query || roleFilter) && <button className="button button-quiet button-small" type="button" onClick={() => { setQuery(''); setRoleFilter(''); }}>Reset</button>}
            </div>
            <div className="table-scroll">
              <table className="data-table"><thead><tr><th>Name</th><th>Email</th><th>Role</th><th>Department</th><th>Status</th><th>Actions</th></tr></thead>
                <tbody>{filteredUsers.map((account) => {
                  const departmentName = departments.find((department) => department.id === account.departmentId)?.name;
                  return <Fragment key={account.id}>
                    <tr><td><span className="table-primary">{account.name}</span></td><td>{account.email}</td><td><span className={`role-pill role-${account.role.toLowerCase()}`}>{account.role}</span></td><td>{departmentName || '—'}</td><td>{account.active ? 'Active' : 'Inactive'}</td><td>
                      {account.role !== 'ADMIN' && account.active && <>
                        <button className="button button-quiet button-small" type="button" onClick={() => startEditingUser(account)}>Edit</button>
                        <button className="button button-quiet button-small" type="button" onClick={() => deactivateUser(account)}>Deactivate</button>
                      </>}
                      {account.role !== 'ADMIN' && !account.active && <button className="button button-quiet button-small" type="button" onClick={() => activateUser(account)}>Reactivate</button>}
                    </td></tr>
                    {editingUser === account.id && <tr><td colSpan="6"><form className="user-edit-form" onSubmit={submitUserUpdate}>
                      <div className="field-group"><label htmlFor={`edit-user-name-${account.id}`}>Name</label><input id={`edit-user-name-${account.id}`} value={userEditForm.name} required maxLength="160" onChange={(event) => setUserEditForm({ ...userEditForm, name: event.target.value })} /></div>
                      <div className="field-group"><label htmlFor={`edit-user-email-${account.id}`}>Email</label><input id={`edit-user-email-${account.id}`} type="email" value={userEditForm.email} required maxLength="254" onChange={(event) => setUserEditForm({ ...userEditForm, email: event.target.value })} /></div>
                      <div className="field-group"><label htmlFor={`edit-user-department-${account.id}`}>Department</label><select id={`edit-user-department-${account.id}`} value={userEditForm.departmentId} onChange={(event) => setUserEditForm({ ...userEditForm, departmentId: event.target.value })}><option value="">No department</option>{departments.filter((item) => item.active).map((department) => <option key={department.id} value={department.id}>{department.name}</option>)}</select></div>
                      {account.role === 'STUDENT' && <>
                        <div className="field-group"><label htmlFor={`edit-user-roll-${account.id}`}>Roll number</label><input id={`edit-user-roll-${account.id}`} value={userEditForm.rollNumber} required maxLength="40" onChange={(event) => setUserEditForm({ ...userEditForm, rollNumber: event.target.value })} /></div>
                        <div className="field-group"><label htmlFor={`edit-user-class-${account.id}`}>Class</label><input id={`edit-user-class-${account.id}`} value={userEditForm.className} required maxLength="100" onChange={(event) => setUserEditForm({ ...userEditForm, className: event.target.value })} /></div>
                        <div className="field-group"><label htmlFor={`edit-user-section-${account.id}`}>Section</label><input id={`edit-user-section-${account.id}`} value={userEditForm.section} required maxLength="40" onChange={(event) => setUserEditForm({ ...userEditForm, section: event.target.value })} /></div>
                      </>}
                      <div className="management-actions"><button className="button button-primary button-small" type="submit" disabled={savingUser}>{savingUser ? 'Saving…' : 'Save changes'}</button><button className="button button-quiet button-small" type="button" onClick={() => { setEditingUser(null); setUserEditForm(null); }}>Cancel</button></div>
                    </form></td></tr>}
                  </Fragment>;
                })}
                  {filteredUsers.length === 0 && <tr><td className="table-empty" colSpan="6">No accounts match this search.</td></tr>}
                </tbody>
              </table>
            </div>
            <p className="chart-note">Deactivation preserves historical complaint relationships. Administrator accounts cannot be edited or deactivated here. Complaint assignment and status changes are not exposed by the current backend.</p>
          </section>}
        </>
      )}
    </DashboardLayout>
  );
}

export default AdminPortalPage;
