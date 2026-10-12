import { useEffect, useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import AuthenticatedComplaintImage from '../components/AuthenticatedComplaintImage';
import StatusBadge from '../components/StatusBadge';
import ComplaintAssignmentEditor from '../components/ComplaintAssignmentEditor';
import api, { getApiErrorMessage } from '../services/api';

function nextStaffAction(status) {
  if (status === 'SUBMITTED' || status === 'UNDER_REVIEW') {
    return { status: 'ASSIGNED', label: 'Acknowledge assignment' };
  }
  if (status === 'ASSIGNED') return { status: 'IN_PROGRESS', label: 'Start work' };
  return null;
}

const maxResolutionImageBytes = 5 * 1024 * 1024;
const supportedResolutionImageExtensions = new Set(['jpg', 'jpeg', 'png', 'webp']);
const supportedResolutionImageTypes = new Set(['image/jpeg', 'image/png', 'image/webp']);

function ComplaintDetailsPage() {
  const { id } = useParams();
  const { user } = useAuth();
  const [complaint, setComplaint] = useState(null);
  const [comments, setComments] = useState([]);
  const [commentsLoading, setCommentsLoading] = useState(false);
  const [commentsError, setCommentsError] = useState('');
  const [commentDraft, setCommentDraft] = useState('');
  const [commentSaving, setCommentSaving] = useState(false);
  const [commentMessage, setCommentMessage] = useState('');
  const commentRequestRef = useRef(null);
  const [resolutionNotes, setResolutionNotes] = useState([]);
  const [resolutionNotesLoading, setResolutionNotesLoading] = useState(false);
  const [resolutionNotesError, setResolutionNotesError] = useState('');
  const [resolutionNoteDraft, setResolutionNoteDraft] = useState('');
  const [resolutionNoteSaving, setResolutionNoteSaving] = useState(false);
  const [resolutionNoteMessage, setResolutionNoteMessage] = useState('');
  const [resolutionProofUrl, setResolutionProofUrl] = useState('');
  const [resolutionProofError, setResolutionProofError] = useState('');
  const [resolutionFile, setResolutionFile] = useState(null);
  const [resolutionPreview, setResolutionPreview] = useState('');
  const [resolutionFileError, setResolutionFileError] = useState('');
  const [resolutionNote, setResolutionNote] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [departments, setDepartments] = useState([]);
  const [departmentsLoading, setDepartmentsLoading] = useState(true);
  const [departmentsError, setDepartmentsError] = useState('');
  const [statusSaving, setStatusSaving] = useState(false);
  const [workError, setWorkError] = useState('');
  const [workMessage, setWorkMessage] = useState('');
  const [resolutionSaving, setResolutionSaving] = useState(false);
  const listPath = user?.role === 'ADMIN' ? '/admin/complaints'
    : user?.role === 'STAFF' ? '/staff/dashboard'
      : user?.role === 'FACULTY' ? '/faculty/dashboard' : '/student/complaints';
  const backLabel = user?.role === 'STUDENT' ? '← Back to My Complaints'
    : user?.role === 'FACULTY' ? '← Back to Faculty Portal' : '← Back to complaints';
  const staffAction = user?.role === 'STAFF' ? nextStaffAction(complaint?.status) : null;

  const loadResolutionProofImage = async (proof) => {
    if (!proof?.imageUrl) return;
    setResolutionProofError('');
    try {
      const endpoint = proof.imageUrl.startsWith('/api/')
        ? proof.imageUrl.slice('/api'.length)
        : proof.imageUrl;
      const { data } = await api.get(endpoint, { responseType: 'blob' });
      setResolutionProofUrl(URL.createObjectURL(data));
    } catch (requestError) {
      setResolutionProofError(requestError.response?.status === 404
        ? 'The saved resolution-proof file is missing or unavailable in upload storage. Complaint details are still available.'
        : getApiErrorMessage(requestError, 'The resolution-proof image could not be loaded.'));
    }
  };

  const updateStatus = async () => {
    if (!complaint || !staffAction || statusSaving || commentSaving || resolutionSaving) return;
    if (staffAction.status === 'RESOLVED'
      && !window.confirm('Mark this complaint as resolved? This records the status change in its history.')) {
      return;
    }
    setStatusSaving(true);
    setWorkError('');
    setWorkMessage('');
    try {
      const { data } = await api.patch(`/complaints/${id}/status`, { status: staffAction.status });
      setComplaint(data);
      setWorkMessage(`Complaint status updated to ${staffAction.status.replaceAll('_', ' ').toLowerCase()}.`);
    } catch (requestError) {
      setWorkError(requestError.response?.data?.message || 'Complaint status could not be updated.');
    } finally {
      setStatusSaving(false);
    }
  };

  const selectResolutionImage = (event) => {
    const selected = event.target.files?.[0] || null;
    setResolutionFileError('');
    setResolutionFile(null);
    if (!selected) return;
    const extension = selected.name.split('.').pop()?.toLowerCase();
    if (!supportedResolutionImageExtensions.has(extension)
      || !supportedResolutionImageTypes.has(selected.type)) {
      setResolutionFileError('Choose a JPG, JPEG, PNG, or WEBP image file.');
      event.target.value = '';
      return;
    }
    if (selected.size > maxResolutionImageBytes) {
      setResolutionFileError('The resolution image must be 5 MB or smaller.');
      event.target.value = '';
      return;
    }
    setResolutionFile(selected);
  };

  const resolveComplaint = async (event) => {
    event.preventDefault();
    if (!complaint || complaint.status !== 'IN_PROGRESS' || resolutionSaving || statusSaving || commentSaving) return;
    if (!resolutionFile) {
      setResolutionFileError('Select a resolution-proof image before marking this complaint resolved.');
      return;
    }
    if (!resolutionNote.trim()) {
      setResolutionFileError('Enter a resolution note describing the completed work.');
      return;
    }
    if (!window.confirm('Upload this proof and mark the complaint as resolved?')) return;

    setResolutionSaving(true);
    setWorkError('');
    setWorkMessage('');
    const body = new FormData();
    body.append('image', resolutionFile);
    body.append('resolution', new Blob(
      [JSON.stringify({ note: resolutionNote })],
      { type: 'application/json' },
    ));
    try {
      const { data } = await api.post(`/complaints/${id}/resolution`, body, {
        headers: { 'Content-Type': undefined },
      });
      setComplaint(data);
      setResolutionFile(null);
      setResolutionNote('');
      const input = document.getElementById('resolution-proof-file');
      if (input) input.value = '';
      setWorkMessage('Resolution proof saved and complaint marked resolved.');
      await loadResolutionProofImage(data.resolutionProof);
    } catch (requestError) {
      setWorkError(getApiErrorMessage(requestError, 'Resolution proof could not be saved.'));
    } finally {
      setResolutionSaving(false);
    }
  };

  const addComment = async (event) => {
    event.preventDefault();
    if (!commentDraft.trim() || commentSaving || statusSaving || resolutionSaving) return;
    setCommentSaving(true);
    setCommentsError('');
    setCommentMessage('');
    try {
      if (commentRequestRef.current?.content !== commentDraft) {
        commentRequestRef.current = { content: commentDraft, requestId: crypto.randomUUID() };
      }
      const { data } = await api.post(`/complaints/${id}/comments`, {
        content: commentDraft,
        requestId: commentRequestRef.current.requestId,
      });
      setComments((current) => [...current, data]);
      setCommentDraft('');
      commentRequestRef.current = null;
      setCommentMessage('Comment added.');
    } catch (requestError) {
      setCommentsError(getApiErrorMessage(requestError, 'Comment could not be added.'));
    } finally {
      setCommentSaving(false);
    }
  };

  const addResolutionNote = async (event) => {
    event.preventDefault();
    if (!resolutionNoteDraft.trim() || resolutionNoteSaving) return;
    setResolutionNoteSaving(true);
    setResolutionNotesError('');
    setResolutionNoteMessage('');
    try {
      const { data } = await api.post(`/complaints/${id}/resolution-notes`, {
        content: resolutionNoteDraft,
      });
      setResolutionNotes((current) => [...current, data]);
      setResolutionNoteDraft('');
      setResolutionNoteMessage('Resolution note saved. Complaint status was not changed.');
    } catch (requestError) {
      setResolutionNotesError(getApiErrorMessage(requestError, 'Resolution note could not be saved.'));
    } finally {
      setResolutionNoteSaving(false);
    }
  };

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');
    setResolutionProofError('');
    setResolutionProofUrl('');
    setComplaint(null);
    setComments([]);
    setCommentsLoading(true);
    setCommentsError('');
    setCommentMessage('');
    setResolutionNotes([]);
    setResolutionNotesLoading(user?.role === 'ADMIN' || user?.role === 'STAFF');
    setResolutionNotesError('');
    setResolutionNoteMessage('');
    api.get(`/complaints/${id}`)
      .then(({ data }) => {
        if (!active) return;
        setComplaint(data);
        setLoading(false);
        if (data.resolutionProof) {
          loadResolutionProofImage(data.resolutionProof);
        }
        api.get(`/complaints/${id}/comments`)
          .then(({ data: entries }) => {
            if (active) setComments(entries);
          })
          .catch((requestError) => {
            if (active) setCommentsError(getApiErrorMessage(requestError, 'Comments could not be loaded.'));
          })
          .finally(() => {
            if (active) setCommentsLoading(false);
          });
        if (user?.role === 'ADMIN' || user?.role === 'STAFF') {
          setResolutionNotesLoading(true);
          api.get(`/complaints/${id}/resolution-notes`)
            .then(({ data: entries }) => {
              if (active) setResolutionNotes(entries);
            })
            .catch((requestError) => {
              if (active) setResolutionNotesError(
                getApiErrorMessage(requestError, 'Resolution notes could not be loaded.'),
              );
            })
            .finally(() => {
              if (active) setResolutionNotesLoading(false);
            });
        } else {
          setResolutionNotesLoading(false);
        }
      })
      .catch((requestError) => {
        if (active) {
          setError(requestError.response?.data?.message || 'Complaint details could not be loaded.');
          setCommentsLoading(false);
          setResolutionNotesLoading(false);
        }
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, [id, user?.role]);

  useEffect(() => {
    if (!resolutionFile) {
      setResolutionPreview('');
      return undefined;
    }
    const previewUrl = URL.createObjectURL(resolutionFile);
    setResolutionPreview(previewUrl);
    return () => URL.revokeObjectURL(previewUrl);
  }, [resolutionFile]);

  useEffect(() => () => {
    if (resolutionProofUrl) URL.revokeObjectURL(resolutionProofUrl);
  }, [resolutionProofUrl]);

  useEffect(() => {
    if (user?.role !== 'ADMIN') return undefined;
    let active = true;
    setDepartmentsLoading(true);
    api.get('/admin/departments')
      .then(({ data }) => {
        if (active) setDepartments(data.filter((department) => department.active));
      })
      .catch((requestError) => {
        if (active) {
          setDepartmentsError(requestError.response?.data?.message || 'Active departments could not be loaded.');
        }
      })
      .finally(() => {
        if (active) setDepartmentsLoading(false);
      });
    return () => { active = false; };
  }, [user?.role]);

  return (
    <div className="details-page">
      <div className="details-back"><Link to={listPath}>{backLabel}</Link></div>
      {loading && <div className="dashboard-loading" role="status"><span className="button-spinner" /> Loading complaint details…</div>}
      {error && <div className="form-alert form-alert-error" role="alert">{error}</div>}
      {complaint && (
        <article className="panel-card complaint-detail-card">
            <div className="details-header">
              <div>
                <p className="eyebrow">{complaint.complaintId}</p>
                <h1>{complaint.title}</h1>
              </div>
              <StatusBadge status={complaint.status} />
            </div>
            <div className="details-facts">
              {(user?.role === 'ADMIN' || user?.role === 'FACULTY')
                && <div><span>{user?.role === 'FACULTY' ? 'Reporter' : 'Student'}</span><strong>{complaint.reporterName || '—'}</strong></div>}
              {user?.role === 'ADMIN' && complaint.reporterRollNumber && <div><span>Roll number</span><strong>{complaint.reporterRollNumber}</strong></div>}
              <div><span>Category</span><strong>{complaint.category}</strong></div>
              <div><span>Location</span><strong>{complaint.location}</strong></div>
              <div><span>Priority</span><strong>{complaint.priority}</strong></div>
              {complaint.department && user?.role !== 'ADMIN' && <div><span>Department</span><strong>{complaint.department}</strong></div>}
              <div><span>Submitted</span><strong>{new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(complaint.submittedAt))}</strong></div>
              <div><span>Last updated</span><strong>{new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(complaint.updatedAt))}</strong></div>
              {(user?.role === 'STAFF' || user?.role === 'FACULTY') && complaint.sla && (
                <>
                  <div><span>SLA deadline</span><strong>{new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' })
                    .format(new Date(complaint.sla.deadlineAt))}</strong></div>
                  <div><span>SLA status</span><strong>{complaint.sla.status.replaceAll('_', ' ')}</strong></div>
                  <div><span>Escalation level</span><strong>{complaint.sla.escalationLevel}</strong></div>
                </>
              )}
              {user?.role === 'FACULTY' && complaint.assignedStaff?.length > 0 && (
                <div><span>Assigned staff</span><strong>{complaint.assignedStaff.join(', ')}</strong></div>
              )}
              {user?.role === 'FACULTY' && complaint.currentAssignment?.assignedAt && (
                <div><span>Assigned at</span><strong>{new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' })
                  .format(new Date(complaint.currentAssignment.assignedAt))}</strong></div>
              )}
            </div>
            <div className="details-description"><h2>Description</h2><p>{complaint.description}</p></div>
            {user?.role === 'ADMIN' && (
              <section className="details-description assignment-details">
                <h2>Assignment</h2>
                <ComplaintAssignmentEditor
                  complaint={complaint}
                  departments={departments}
                  departmentsLoading={departmentsLoading}
                  departmentsError={departmentsError}
                  onSaved={setComplaint}
                />
              </section>
            )}
            {user?.role === 'STAFF' && (
              <section className="details-description staff-work-actions" aria-labelledby="work-actions-title">
                <h2 id="work-actions-title">Work actions</h2>
                {staffAction
                  ? <button className="button button-primary" type="button" onClick={updateStatus}
                  disabled={statusSaving || commentSaving || resolutionSaving}>
                      {statusSaving ? 'Saving status…' : staffAction.label}
                    </button>
                  : complaint.status !== 'IN_PROGRESS'
                    ? <p>This complaint has no further Staff status actions.</p>
                    : null}
                {complaint.status === 'IN_PROGRESS' && (
                  <form className="resolution-proof-form" onSubmit={resolveComplaint}>
                    <h3>Resolution Proof</h3>
                    <p>Upload a photo showing the completed repair or resolution before marking this complaint as resolved.</p>
                    <label htmlFor="resolution-proof-file">Resolution image (JPG, JPEG, PNG, or WEBP; maximum 5 MB)</label>
                    <input id="resolution-proof-file" className="form-control" type="file"
                      accept=".jpg,.jpeg,.png,.webp,image/jpeg,image/png,image/webp"
                      onChange={selectResolutionImage}
                      disabled={resolutionSaving || statusSaving || commentSaving} />
                    {resolutionPreview && (
                      <div className="resolution-proof-preview">
                        <img className="complaint-image-detail" src={resolutionPreview} alt="Selected resolution proof preview" />
                        <button className="button button-secondary button-small" type="button"
                          disabled={resolutionSaving}
                          onClick={() => {
                            setResolutionFile(null);
                            const input = document.getElementById('resolution-proof-file');
                            if (input) input.value = '';
                          }}>
                          Remove image
                        </button>
                      </div>
                    )}
                    <label htmlFor="resolution-note">Resolution note</label>
                    <textarea id="resolution-note" className="form-control" rows="3" maxLength="1000"
                      value={resolutionNote}
                      onChange={(event) => setResolutionNote(event.target.value)}
                      placeholder="Describe the completed repair or work"
                      disabled={resolutionSaving || statusSaving || commentSaving} />
                    <div className="work-note-submit">
                      <small>{resolutionNote.length}/1000 characters</small>
                      <button className="button button-primary" type="submit"
                        disabled={resolutionSaving || statusSaving || commentSaving || !resolutionFile || !resolutionNote.trim()}>
                        {resolutionSaving ? 'Uploading proof…' : 'Upload proof and mark resolved'}
                      </button>
                    </div>
                    {resolutionFileError && <div className="form-alert form-alert-error" role="alert">{resolutionFileError}</div>}
                  </form>
                )}
                {workError && <div className="form-alert form-alert-error" role="alert">{workError}</div>}
                {workMessage && <div className="form-alert form-alert-success" role="status">{workMessage}</div>}
              </section>
            )}
            {user?.role === 'ADMIN' && complaint.status === 'RESOLVED' && (
              <section className="details-description staff-work-actions" aria-labelledby="admin-status-actions">
                <h2 id="admin-status-actions">Administrative status</h2>
                <button className="button button-secondary" type="button"
                  disabled={statusSaving || commentSaving || resolutionSaving}
                  onClick={async () => {
                    if (!window.confirm('Close this resolved complaint?')) return;
                    setStatusSaving(true);
                    setWorkError('');
                    setWorkMessage('');
                    try {
                      const { data } = await api.patch(`/complaints/${id}/status`, {
                        status: 'CLOSED',
                        note: 'Resolution reviewed and closed by Admin.',
                      });
                      setComplaint(data);
                      setWorkMessage('Resolved complaint closed.');
                    } catch (requestError) {
                      setWorkError(getApiErrorMessage(requestError, 'Complaint status could not be updated.'));
                    } finally {
                      setStatusSaving(false);
                    }
                  }}>
                  {statusSaving ? 'Saving status…' : 'Close resolved complaint'}
                </button>
                {workError && <div className="form-alert form-alert-error" role="alert">{workError}</div>}
                {workMessage && <div className="form-alert form-alert-success" role="status">{workMessage}</div>}
              </section>
            )}
            <section className="details-timeline complaint-comments" aria-labelledby="comments-title">
              <h2 id="comments-title">Comments</h2>
              {commentsLoading && <p className="assignment-hint" role="status">Loading comments…</p>}
              {commentsError && <div className="form-alert form-alert-error" role="alert">{commentsError}</div>}
              {!commentsLoading && !commentsError && comments.length === 0
                && <p className="assignment-hint">No comments have been added.</p>}
              {comments.length > 0 && (
                <ol>
                  {comments.map((entry) => (
                    <li className="work-note-entry" key={entry.id}>
                      <div>
                        <strong>{entry.authorName} <span className="comment-author-role">{entry.authorRole}</span></strong>
                        <p>{entry.content}</p>
                        <time dateTime={entry.createdAt}>
                          {new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' })
                            .format(new Date(entry.createdAt))}
                        </time>
                      </div>
                    </li>
                  ))}
                </ol>
              )}
              {['STUDENT', 'STAFF', 'ADMIN'].includes(user?.role) && (
                <form className="work-note-form comment-form" onSubmit={addComment}>
                  <label htmlFor="complaint-comment">Add a comment</label>
                  <textarea id="complaint-comment" className="form-control" rows="3" maxLength="1000"
                    value={commentDraft}
                    onChange={(event) => setCommentDraft(event.target.value)}
                    placeholder="Write a comment about this complaint"
                    disabled={commentSaving || statusSaving || resolutionSaving} />
                  <div className="work-note-submit">
                    <small>{commentDraft.length}/1000 characters</small>
                    <button className="button button-secondary" type="submit"
                      disabled={commentSaving || statusSaving || resolutionSaving || !commentDraft.trim()}>
                      {commentSaving ? 'Submitting…' : 'Submit Comment'}
                    </button>
                  </div>
                  {commentMessage && <div className="form-alert form-alert-success" role="status">{commentMessage}</div>}
                </form>
              )}
            </section>
            {(user?.role === 'STAFF' || user?.role === 'ADMIN') && (
              <section className="details-timeline complaint-resolution-notes" aria-labelledby="resolution-notes-title">
                <h2 id="resolution-notes-title">Resolution Notes</h2>
                <p className="assignment-hint">
                  These notes are separate from status changes and resolution-proof uploads.
                </p>
                {resolutionNotesLoading && <p className="assignment-hint" role="status">Loading resolution notes…</p>}
                {resolutionNotesError && <div className="form-alert form-alert-error" role="alert">{resolutionNotesError}</div>}
                {!resolutionNotesLoading && !resolutionNotesError && resolutionNotes.length === 0
                  && <p className="assignment-hint">No resolution notes have been recorded.</p>}
                {resolutionNotes.length > 0 && (
                  <ol>
                    {resolutionNotes.map((entry) => (
                      <li className="work-note-entry" key={entry.id}>
                        <div>
                          <strong>{entry.authorName} <span className="comment-author-role">{entry.authorRole}</span></strong>
                          <p>{entry.content}</p>
                          <time dateTime={entry.createdAt}>
                            {new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' })
                              .format(new Date(entry.createdAt))}
                          </time>
                        </div>
                      </li>
                    ))}
                  </ol>
                )}
                <form className="work-note-form comment-form" onSubmit={addResolutionNote}>
                  <label htmlFor="resolution-note-entry">Add a resolution note</label>
                  <textarea id="resolution-note-entry" className="form-control" rows="3" maxLength="1000"
                    value={resolutionNoteDraft}
                    onChange={(event) => setResolutionNoteDraft(event.target.value)}
                    placeholder="Record a resolution-related observation"
                    disabled={resolutionNoteSaving} />
                  <div className="work-note-submit">
                    <small>{resolutionNoteDraft.length}/1000 characters</small>
                    <button className="button button-secondary" type="submit"
                      disabled={resolutionNoteSaving || !resolutionNoteDraft.trim()}>
                      {resolutionNoteSaving ? 'Saving…' : 'Add Resolution Note'}
                    </button>
                  </div>
                  {resolutionNoteMessage && (
                    <div className="form-alert form-alert-success" role="status">{resolutionNoteMessage}</div>
                  )}
                </form>
              </section>
            )}
            {(user?.role === 'STAFF' || user?.role === 'ADMIN' || user?.role === 'FACULTY') && (
              <section className="details-timeline work-notes" aria-labelledby="work-notes-title">
                <h2 id="work-notes-title">Work notes</h2>
                {complaint.workNotes?.length
                  ? <ol>
                      {complaint.workNotes.map((entry, index) => (
                        <li className="work-note-entry" key={`${entry.createdAt}-${index}`}>
                          <div>
                            <strong>{entry.authorName}</strong>
                            <p>{entry.note}</p>
                            <time dateTime={entry.createdAt}>
                              {new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' })
                                .format(new Date(entry.createdAt))}
                            </time>
                          </div>
                        </li>
                      ))}
                    </ol>
                  : <p className="assignment-hint">No work notes have been recorded.</p>}
              </section>
            )}
            {user?.role !== 'ADMIN' && complaint.assignedStaff?.length > 0 && (
              <section className="details-description">
                <h2>Assigned staff</h2>
                <p>{complaint.assignedStaff.join(', ')}</p>
              </section>
            )}
            {complaint.statusHistory?.length > 0 && (
              <section className="details-timeline" aria-labelledby="status-history-title">
                <h2 id="status-history-title">Status history</h2>
                <ol>
                  {complaint.statusHistory.map((entry, index) => (
                    <li key={`${entry.changedAt}-${index}`}>
                      <StatusBadge status={entry.status} />
                      <div className="status-history-entry">
                        <div className="status-history-meta">
                          {entry.actorName && <strong>{entry.actorName}</strong>}
                          <time dateTime={entry.changedAt}>
                            {new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' })
                              .format(new Date(entry.changedAt))}
                          </time>
                        </div>
                        {entry.note && <p>{entry.note}</p>}
                      </div>
                    </li>
                  ))}
                </ol>
              </section>
            )}
            {complaint.hasImage && (
              <div className="details-image">
                <h2>Attached image</h2>
                <AuthenticatedComplaintImage complaintId={complaint.id} complaintCode={complaint.complaintId} />
              </div>
            )}
            {(complaint.resolutionProof || complaint.status === 'RESOLVED') && (
              <div className="details-image resolution-proof-display">
                <h2>Resolution Proof</h2>
                {complaint.resolutionProof ? (
                  <>
                    <p>Uploaded by {complaint.resolutionProof.staffName} on{' '}
                      {new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' })
                        .format(new Date(complaint.resolutionProof.uploadedAt))}
                    </p>
                    {complaint.resolutionProof.note && <p>{complaint.resolutionProof.note}</p>}
                    {resolutionProofUrl
                      ? <img className="complaint-image-detail" src={resolutionProofUrl}
                          alt={`Resolution proof for ${complaint.complaintId}`} />
                      : <div className="form-alert form-alert-warning" role="status">
                          {resolutionProofError || 'Loading resolution-proof image…'}
                        </div>}
                  </>
                ) : (
                  <div className="form-alert form-alert-warning" role="status">
                    No resolution proof is available for this complaint.
                  </div>
                )}
              </div>
            )}
        </article>
      )}
    </div>
  );
}

export default ComplaintDetailsPage;
