import { useEffect, useState } from 'react';
import api from '../services/api';

function ConnectionStatus() {
  const [status, setStatus] = useState('checking');

  useEffect(() => {
    let isMounted = true;

    const fetchHealth = async () => {
      try {
        const response = await api.get('/health');
        if (isMounted && response?.data?.status === 'UP') {
          setStatus('connected');
        } else if (isMounted) {
          setStatus('unavailable');
        }
      } catch {
        if (isMounted) {
          setStatus('unavailable');
        }
      }
    };

    fetchHealth();

    return () => {
      isMounted = false;
    };
  }, []);

  return (
    <div className="connection-status" role="status" aria-live="polite">
      <span className={`health-dot${status === 'connected' ? ' connected' : ''}`} />
      <span>{status === 'checking' ? 'Checking service status' : status === 'connected' ? 'Service online' : 'Service unavailable'}</span>
    </div>
  );
}

export default ConnectionStatus;
