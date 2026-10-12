import { useEffect, useState } from 'react';
import api, { getApiErrorMessage } from '../services/api';

function AuthenticatedComplaintImage({ complaintId, complaintCode, className = 'complaint-image-detail' }) {
  const [imageUrl, setImageUrl] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let active = true;
    let objectUrl = '';
    setImageUrl('');
    setError('');
    setLoading(true);

    api.get(`/complaints/${complaintId}/image`, { responseType: 'blob' })
      .then(({ data }) => {
        if (!data.type?.startsWith('image/')) {
          throw new Error('The server response was not an image.');
        }
        if (!active) return;
        objectUrl = URL.createObjectURL(data);
        setImageUrl(objectUrl);
      })
      .catch((requestError) => {
        if (!active) return;
        if (requestError.response?.status === 401) {
          setError(getApiErrorMessage(requestError, 'Sign in again to view this complaint image.'));
        } else if (requestError.response?.status === 403) {
          setError('You do not have permission to view this complaint image.');
        } else if (requestError.response?.status === 404) {
          setError('The saved complaint image is missing or unavailable in upload storage.');
        } else {
          setError(requestError.message === 'The server response was not an image.'
            ? requestError.message
            : getApiErrorMessage(requestError, 'The complaint image could not be loaded.'));
        }
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [complaintId]);

  if (imageUrl) {
    return <img className={className} src={imageUrl} alt={`Image attached to ${complaintCode}`} />;
  }
  if (loading) {
    return <span className="complaint-image-message" role="status">Loading image…</span>;
  }
  return <span className="complaint-image-message" role="status">{error || 'The complaint image could not be loaded.'}</span>;
}

export default AuthenticatedComplaintImage;
