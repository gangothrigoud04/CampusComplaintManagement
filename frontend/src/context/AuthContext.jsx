import { createContext, useContext, useEffect, useMemo, useState } from 'react';
import api from '../services/api';

const AuthContext = createContext(null);
const TOKEN_KEY = 'campusAccessToken';

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let active = true;
    const token = window.sessionStorage.getItem(TOKEN_KEY);
    if (!token) {
      setLoading(false);
      return () => {
        active = false;
      };
    }

    api.get('/auth/me')
      .then(({ data }) => {
        if (active && window.sessionStorage.getItem(TOKEN_KEY) === token) {
          setUser(data);
        }
      })
      .catch((error) => {
        if (active
          && error.response?.status === 401
          && window.sessionStorage.getItem(TOKEN_KEY) === token) {
          window.sessionStorage.removeItem(TOKEN_KEY);
        }
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, []);

  const acceptAuthentication = (data) => {
    window.sessionStorage.setItem(TOKEN_KEY, data.accessToken);
    window.sessionStorage.removeItem('campusSessionExpired');
    setUser(data.user);
    return data.user;
  };

  useEffect(() => {
    const handleSessionExpired = () => {
      window.sessionStorage.removeItem(TOKEN_KEY);
      window.sessionStorage.setItem('campusSessionExpired', 'true');
      setUser(null);
    };
    window.addEventListener('campus:session-expired', handleSessionExpired);
    return () => window.removeEventListener('campus:session-expired', handleSessionExpired);
  }, []);

  const login = async (credentials) => {
    const { data } = await api.post('/auth/login', credentials);
    return acceptAuthentication(data);
  };

  const register = async (details) => {
    const { data } = await api.post('/auth/register', details);
    return data;
  };

  const logout = () => {
    window.sessionStorage.removeItem(TOKEN_KEY);
    window.sessionStorage.removeItem('campusSessionExpired');
    setUser(null);
  };

  const value = useMemo(() => ({ user, loading, login, register, logout }), [user, loading]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used within an AuthProvider');
  return context;
}

export { TOKEN_KEY };
