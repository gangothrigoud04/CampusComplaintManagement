import { useEffect, useState } from 'react';
import api, { getApiErrorMessage } from '../services/api';

function ComplaintAssignmentEditor({
  complaint,
  departments,
  departmentsLoading = false,
  departmentsError = '',
  compact = false,
  onSaved,
}) {
  const [open, setOpen] = useState(!compact);
  const [departmentId, setDepartmentId] = useState(
    complaint.currentAssignment?.departmentId == null ? '' : String(complaint.currentAssignment.departmentId),
  );
  const [staffId, setStaffId] = useState(
    complaint.currentAssignment?.staffId == null ? '' : String(complaint.currentAssignment.staffId),
  );
  const [staff, setStaff] = useState([]);
  const [staffLoading, setStaffLoading] = useState(false);
  const [staffError, setStaffError] = useState('');
  const [saving, setSaving] = useState(false);
  const [feedback, setFeedback] = useState('');
  const [error, setError] = useState('');

  useEffect(() => {
    setDepartmentId(complaint.currentAssignment?.departmentId == null
      ? '' : String(complaint.currentAssignment.departmentId));
    setStaffId(complaint.currentAssignment?.staffId == null
      ? '' : String(complaint.currentAssignment.staffId));
  }, [
    complaint.id,
    complaint.currentAssignment?.departmentId,
    complaint.currentAssignment?.staffId,
  ]);

  useEffect(() => {
    if (!open || !departmentId) {
      setStaff([]);
      setStaffError('');
      return undefined;
    }

    let active = true;
    setStaffLoading(true);
    setStaffError('');
    setStaff([]);
    api.get('/admin/assignment-options/staff', { params: { departmentId: Number(departmentId) } })
      .then(({ data }) => {
        if (active) {
          setStaff(data);
          setStaffId((current) => data.some((person) => String(person.id) === current) ? current : '');
        }
      })
      .catch((requestError) => {
        if (active) setStaffError(getApiErrorMessage(requestError, 'Eligible Staff could not be loaded.'));
      })
      .finally(() => {
        if (active) setStaffLoading(false);
      });

    return () => { active = false; };
  }, [departmentId, open]);

  const saveAssignment = async (event) => {
    event.preventDefault();
    if (saving || !departmentId || !staffId) return;
    setSaving(true);
    setError('');
    setFeedback('');
    try {
      const { data } = await api.put(`/admin/complaints/${complaint.id}/assignment`, {
        departmentId: Number(departmentId),
        staffId: Number(staffId),
      });
      onSaved(data);
      setFeedback('Assignment saved successfully.');
    } catch (requestError) {
      setError(getApiErrorMessage(requestError, 'The assignment could not be saved.'));
    } finally {
      setSaving(false);
    }
  };

  const currentStaff = complaint.assignedStaff?.length
    ? complaint.assignedStaff.join(', ')
    : complaint.currentAssignment?.staffName || 'Not assigned';

  return (
    <section className={`assignment-editor${compact ? ' assignment-editor-compact' : ''}`}>
      {compact && (
        <button className="button button-secondary button-small" type="button"
          aria-expanded={open} onClick={() => setOpen((wasOpen) => !wasOpen)}>
          {open ? 'Close' : complaint.currentAssignment?.staffId ? 'Change assignment' : 'Assign'}
        </button>
      )}
      {open && (
        <>
          {!compact && <div className="assignment-current">
            <div><span>Current department</span><strong>{complaint.department || 'Not assigned'}</strong></div>
            <div><span>Current staff member</span><strong>{currentStaff}</strong></div>
          </div>}
          {departmentsError && <div className="form-alert form-alert-error" role="alert">{departmentsError}</div>}
          {staffError && <div className="form-alert form-alert-error" role="alert">{staffError}</div>}
          {error && <div className="form-alert form-alert-error" role="alert">{error}</div>}
          {feedback && <div className="form-alert form-alert-success" role="status">{feedback}</div>}
          {departments.length === 0 && !departmentsLoading && !departmentsError ? (
            <p className="assignment-hint">No active departments are available for assignment.</p>
          ) : (
            <form className="assignment-form" onSubmit={saveAssignment}>
              <div className="field-group">
                <label htmlFor={`assignment-department-${complaint.id}`}>Department</label>
                <select id={`assignment-department-${complaint.id}`} value={departmentId}
                  disabled={departmentsLoading || departments.length === 0}
                  onChange={(event) => {
                    setDepartmentId(event.target.value);
                    setStaffId('');
                    setError('');
                    setFeedback('');
                  }}>
                  <option value="">{departmentsLoading ? 'Loading departments…' : 'Select a department'}</option>
                  {departments.map((department) => (
                    <option value={department.id} key={department.id}>{department.name}</option>
                  ))}
                </select>
              </div>
              <div className="field-group">
                <label htmlFor={`assignment-staff-${complaint.id}`}>Eligible Staff</label>
                <select id={`assignment-staff-${complaint.id}`} value={staffId}
                  disabled={!departmentId || staffLoading || staff.length === 0}
                  onChange={(event) => {
                    setStaffId(event.target.value);
                    setError('');
                    setFeedback('');
                  }}>
                  <option value="">
                    {!departmentId ? 'Select a department first'
                      : staffLoading ? 'Loading eligible Staff…'
                        : staffError ? 'Could not load eligible Staff'
                        : staff.length === 0 ? 'No active Staff account is associated with this department'
                          : 'Select a Staff member'}
                  </option>
                  {staff.map((person) => (
                    <option value={person.id} key={person.id}>{person.name}</option>
                  ))}
                </select>
              </div>
              <button className="button button-primary button-small" type="submit"
                disabled={saving || departmentsLoading || staffLoading || !departmentId || !staffId}>
                {saving ? 'Saving…' : 'Save Assignment'}
              </button>
            </form>
          )}
        </>
      )}
    </section>
  );
}

export default ComplaintAssignmentEditor;
