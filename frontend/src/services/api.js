import axios from 'axios';

const api = axios.create({
  baseURL: '/api',
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
  },
});

function isAuthenticatedSessionFailure(error) {
  const authorization = error.config?.headers?.Authorization;
  return error.response?.status === 401
    && typeof authorization === 'string'
    && authorization.startsWith('Bearer ')
    && error.response.data?.error === 'unauthorized';
}

function getRequestAccessToken(config) {
  const authorization = config?.headers?.Authorization;
  return typeof authorization === 'string' && authorization.startsWith('Bearer ')
    ? authorization.slice('Bearer '.length)
    : null;
}

async function normalizeJsonBlobError(error) {
  const response = error.response;
  const data = response?.data;
  if (typeof Blob === 'undefined' || !(data instanceof Blob)) return;

  const contentType = response.headers?.get?.('content-type')
    || response.headers?.['content-type']
    || response.headers?.['Content-Type']
    || '';
  if (!contentType.toLowerCase().includes('json')) return;

  try {
    response.data = JSON.parse(await data.text());
  } catch (parseError) {
    if (!(parseError instanceof SyntaxError)) throw parseError;
  }
}

function isPublicAuthenticationRequest(config) {
  const method = config.method?.toLowerCase();
  return (method === 'post' && ['/auth/login', '/auth/register'].includes(config.url))
    || (method === 'get' && config.url === '/auth/departments');
}

function removeAuthorizationHeader(config) {
  if (typeof config.headers.delete === 'function') {
    config.headers.delete('Authorization');
  } else {
    delete config.headers.Authorization;
    delete config.headers.authorization;
  }
}

export function getApiErrorMessage(error, fallback) {
  if (isAuthenticatedSessionFailure(error)) {
    return 'Your session has expired or is no longer valid. Sign in again to continue.';
  }
  if (error.response) {
    return error.response.data?.message || fallback;
  }
  if (error.code === 'ECONNABORTED') {
    return 'The request timed out. Please try again.';
  }
  return 'Could not reach the campus service. Check that the backend is running and try again.';
}

export function getLoginErrorMessage(error) {
  if (error.response?.status === 401) {
    return 'Email or password is incorrect. Check your details and try again.';
  }
  if (error.response?.status >= 500) {
    return 'CampusCare could not complete sign-in right now. Please try again shortly.';
  }
  return getApiErrorMessage(error, 'Sign-in failed. Check your details and try again.');
}

api.interceptors.request.use((config) => {
  const token = window.sessionStorage.getItem('campusAccessToken')?.trim();
  if (isPublicAuthenticationRequest(config)) {
    removeAuthorizationHeader(config);
  } else if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  } else {
    removeAuthorizationHeader(config);
  }
  return config;
});

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    await normalizeJsonBlobError(error);
    const failedToken = getRequestAccessToken(error.config);
    const currentToken = window.sessionStorage.getItem('campusAccessToken');
    if (isAuthenticatedSessionFailure(error) && failedToken === currentToken) {
      window.sessionStorage.removeItem('campusAccessToken');
      window.dispatchEvent(new Event('campus:session-expired'));
    }
    return Promise.reject(error);
  },
);

export default api;
