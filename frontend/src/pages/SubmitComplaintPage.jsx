import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import DashboardLayout from '../components/DashboardLayout';
import { useAuth } from '../context/AuthContext';
import api from '../services/api';

const MAX_IMAGE_BYTES = 5 * 1024 * 1024;
const ACCEPTED_IMAGE_TYPES = new Set(['image/jpeg', 'image/png', 'image/webp']);
const ACCEPTED_EXTENSIONS = /\.(jpe?g|png|webp)$/i;
const initialValues = {
  title: '',
  description: '',
  categoryId: '',
  location: '',
  priority: 'MEDIUM',
};

function SubmitComplaintPage() {
  const { user } = useAuth();
  const isFaculty = user?.role === 'FACULTY';
  const complaintsPath = isFaculty ? '/faculty/my-complaints' : '/student/complaints';
  const [values, setValues] = useState(initialValues);
  const [categories, setCategories] = useState([]);
  const [image, setImage] = useState(null);
  const [imagePreview, setImagePreview] = useState('');
  const [categoriesLoading, setCategoriesLoading] = useState(true);
  const [categoriesError, setCategoriesError] = useState('');
  const [error, setError] = useState('');
  const [success, setSuccess] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const imageInput = useRef(null);

  useEffect(() => {
    let active = true;
    api.get('/complaints/categories')
      .then(({ data }) => {
        if (active) setCategories(data);
      })
      .catch((requestError) => {
        if (active) setCategoriesError(requestError.response?.data?.message || 'Complaint categories could not be loaded.');
      })
      .finally(() => {
        if (active) setCategoriesLoading(false);
      });
    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    if (!image) {
      setImagePreview('');
      return undefined;
    }
    const previewUrl = URL.createObjectURL(image);
    setImagePreview(previewUrl);
    return () => URL.revokeObjectURL(previewUrl);
  }, [image]);

  const updateValue = (event) => {
    setValues((current) => ({ ...current, [event.target.name]: event.target.value }));
  };

  const chooseImage = (event) => {
    const selected = event.target.files?.[0];
    setError('');
    if (!selected) return;
    const typeIsAllowed = ACCEPTED_IMAGE_TYPES.has(selected.type)
      || (!selected.type && ACCEPTED_EXTENSIONS.test(selected.name));
    if (!typeIsAllowed) {
      setImage(null);
      event.target.value = '';
      setError('Choose a JPG, JPEG, PNG, or WEBP image.');
      return;
    }
    if (selected.size === 0) {
      setImage(null);
      event.target.value = '';
      setError('The selected image is empty.');
      return;
    }
    if (selected.size > MAX_IMAGE_BYTES) {
      setImage(null);
      event.target.value = '';
      setError('Images must be 5 MB or smaller.');
      return;
    }
    setImage(selected);
  };

  const removeImage = () => {
    setImage(null);
    if (imageInput.current) imageInput.current.value = '';
  };

  const wrapPage = (content) => isFaculty
    ? (
      <DashboardLayout role="FACULTY" title="Submit complaint" eyebrow="Faculty workspace"
        actions={<Link className="button button-secondary button-small" to={complaintsPath}>My complaints</Link>}>
        {content}
      </DashboardLayout>
    )
    : content;

  const handleSubmit = async (event) => {
    event.preventDefault();
    if (submitting) return;
    setError('');
    setSuccess(null);

    const trimmed = {
      ...values,
      title: values.title.trim(),
      description: values.description.trim(),
      location: values.location.trim(),
    };
    if (!trimmed.title || !trimmed.description || !trimmed.location || !trimmed.categoryId) {
      setError('Complete the title, description, category, and campus location before submitting.');
      return;
    }

    const formData = new FormData();
    formData.append('complaint', new Blob([JSON.stringify({
      ...trimmed,
      categoryId: Number(trimmed.categoryId),
    })], { type: 'application/json' }));
    if (image) formData.append('image', image);

    setSubmitting(true);
    try {
      const { data } = await api.post('/complaints', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
        timeout: 60000,
      });
      setSuccess(data);
      setValues(initialValues);
      setImage(null);
      if (imageInput.current) imageInput.current.value = '';
    } catch (requestError) {
      setError(requestError.response?.data?.message || 'Your complaint could not be submitted. Please try again.');
    } finally {
      setSubmitting(false);
    }
  };

  if (success) {
    return wrapPage(
      <div className="container py-5">
        <div className="card border-0 shadow-sm mx-auto auth-card">
          <div className="card-body p-4 p-lg-5">
            <div className="alert alert-success" role="status">
              Your complaint was submitted successfully.
            </div>
            <h1 className="h3">Complaint {success.complaintId}</h1>
            <p className="mb-1"><strong>Submitted:</strong> {new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(success.submittedAt))}</p>
            <p className="mb-4"><strong>Initial status:</strong> {success.status}</p>
            <div className="d-flex flex-wrap gap-2">
              <Link className="btn btn-primary" to={`${isFaculty ? '/faculty' : '/student'}/complaints/${success.id}`}>View complaint</Link>
              <Link className="btn btn-outline-primary" to={complaintsPath}>My complaints</Link>
              <button className="btn btn-outline-secondary" type="button" onClick={() => setSuccess(null)}>Submit another</button>
            </div>
          </div>
        </div>
      </div>
    );
  }

  return wrapPage(
    <div className="container py-5">
      <div className="card border-0 shadow-sm mx-auto complaint-form-card">
        <div className="card-body p-4 p-lg-5">
          <div className="d-flex justify-content-between flex-wrap align-items-center gap-3 mb-4">
            <div>
              <p className="text-uppercase text-primary fw-semibold mb-2">{isFaculty ? 'Faculty portal' : 'Student portal'}</p>
              <h1 className="h2 mb-0">Submit a complaint</h1>
            </div>
            <Link className="btn btn-outline-primary" to={complaintsPath}>My complaints</Link>
          </div>

          {error && <div className="alert alert-danger" role="alert">{error}</div>}
          {categoriesError && <div className="alert alert-danger" role="alert">{categoriesError}</div>}

          <form onSubmit={handleSubmit}>
            <div className="mb-3">
              <label className="form-label" htmlFor="complaint-title">Complaint title</label>
              <input
                className="form-control"
                id="complaint-title"
                name="title"
                maxLength="200"
                value={values.title}
                onChange={updateValue}
                required
              />
              <div className="form-text">{values.title.trim().length}/200 characters</div>
            </div>

            <div className="mb-3">
              <label className="form-label" htmlFor="complaint-description">Detailed description</label>
              <textarea
                className="form-control"
                id="complaint-description"
                name="description"
                rows="5"
                maxLength="5000"
                value={values.description}
                onChange={updateValue}
                required
              />
              <div className="form-text">{values.description.trim().length}/5000 characters</div>
            </div>

            <div className="row g-3 mb-3">
              <div className="col-md-6">
                <label className="form-label" htmlFor="complaint-category">Category</label>
                <select
                  className="form-select"
                  id="complaint-category"
                  name="categoryId"
                  value={values.categoryId}
                  onChange={updateValue}
                  required
                  disabled={categoriesLoading || categories.length === 0}
                >
                  <option value="">{categoriesLoading ? 'Loading categories…' : 'Select a category'}</option>
                  {categories.map((category) => (
                    <option value={category.id} key={category.id}>{category.name}</option>
                  ))}
                </select>
              </div>
              <div className="col-md-6">
                <label className="form-label" htmlFor="complaint-priority">Priority</label>
                <select className="form-select" id="complaint-priority" name="priority" value={values.priority} onChange={updateValue}>
                  {['LOW', 'MEDIUM', 'HIGH', 'URGENT'].map((priority) => (
                    <option value={priority} key={priority}>{priority}</option>
                  ))}
                </select>
              </div>
            </div>

            <div className="mb-3">
              <label className="form-label" htmlFor="complaint-location">Campus building or location</label>
              <input
                className="form-control"
                id="complaint-location"
                name="location"
                maxLength="160"
                placeholder="For example: Main Building, Room 204"
                value={values.location}
                onChange={updateValue}
                required
              />
            </div>

            <div className="mb-4">
              <label className="form-label" htmlFor="complaint-image">Image attachment <span className="text-secondary">(optional)</span></label>
              <input
                ref={imageInput}
                className="form-control"
                id="complaint-image"
                type="file"
                accept=".jpg,.jpeg,.png,.webp,image/jpeg,image/png,image/webp"
                onChange={chooseImage}
              />
              <div className="form-text">JPG, JPEG, PNG, or WEBP; maximum 5 MB.</div>
              {image && (
                <div className="border rounded-3 p-3 mt-3 d-flex flex-wrap align-items-center gap-3">
                  <img className="complaint-image-preview" src={imagePreview} alt="Selected complaint preview" />
                  <div className="flex-grow-1">
                    <div className="fw-semibold text-break">{image.name}</div>
                    <small className="text-secondary">{(image.size / (1024 * 1024)).toFixed(2)} MB</small>
                  </div>
                  <button className="btn btn-sm btn-outline-danger" type="button" onClick={removeImage}>Remove image</button>
                </div>
              )}
            </div>

            <div className="d-flex flex-wrap gap-2">
              <button className="btn btn-primary" type="submit" disabled={submitting || categoriesLoading || categories.length === 0}>
                {submitting ? 'Submitting…' : 'Submit complaint'}
              </button>
              <Link className="btn btn-outline-secondary" to={complaintsPath}>Cancel</Link>
            </div>
          </form>
        </div>
      </div>
    </div>
  );
}

export default SubmitComplaintPage;
