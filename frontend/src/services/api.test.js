import assert from 'node:assert/strict';
import test from 'node:test';
import axios from 'axios';

const values = new Map();
const expiredEvents = [];
globalThis.window = new EventTarget();
window.sessionStorage = {
  getItem: (key) => values.get(key) ?? null,
  setItem: (key, value) => values.set(key, String(value)),
  removeItem: (key) => values.delete(key),
};
window.addEventListener('campus:session-expired', () => expiredEvents.push(true));

const { default: api, getApiErrorMessage, getLoginErrorMessage } = await import('./api.js');

test('admin SLA requests use the existing session token as a Bearer token', async () => {
  values.set('campusAccessToken', ' test-access-token ');
  let requestConfig;
  api.defaults.adapter = async (config) => {
    requestConfig = config;
    return { data: [], status: 200, statusText: 'OK', headers: {}, config };
  };

  await api.get('/admin/sla?page=0&size=20');
  assert.equal(requestConfig.headers.Authorization, 'Bearer test-access-token');
});

test('complaint attachment image requests use the authenticated API client and return image blobs', async () => {
  values.set('campusAccessToken', 'faculty-image-access-token');
  let requestConfig;
  const imageBytes = new Blob(['authorized image bytes'], { type: 'image/jpeg' });
  api.defaults.adapter = async (config) => {
    requestConfig = config;
    return {
      data: imageBytes,
      status: 200,
      statusText: 'OK',
      headers: { 'content-type': 'image/jpeg' },
      config,
    };
  };

  const { data } = await api.get('/complaints/5/image', { responseType: 'blob' });

  assert.equal(requestConfig.headers.Authorization, 'Bearer faculty-image-access-token');
  assert.equal(requestConfig.url, '/complaints/5/image');
  assert.equal(requestConfig.responseType, 'blob');
  assert.equal(data, imageBytes);
  assert.equal(data.type, 'image/jpeg');
});

test('notification APIs use the active bearer token for list and read actions', async () => {
  const accessToken = 'notifications-access-token';
  values.set('campusAccessToken', accessToken);
  const requests = [];
  api.defaults.adapter = async (config) => {
    requests.push(config);
    return { data: {}, status: 200, statusText: 'OK', headers: {}, config };
  };

  await api.get('/notifications', { params: { page: 0, size: 10 } });
  await api.get('/notifications/unread-count');
  await api.patch('/notifications/42/read');
  await api.patch('/notifications/read-all');

  assert.deepEqual(requests.map((config) => [config.method, config.url]), [
    ['get', '/notifications'],
    ['get', '/notifications/unread-count'],
    ['patch', '/notifications/42/read'],
    ['patch', '/notifications/read-all'],
  ]);
  assert.ok(requests.every((config) => (
    config.headers.Authorization === `Bearer ${accessToken}`
  )));
});

test('complaint comment and resolution-note APIs use the shared authenticated client', async () => {
  const accessToken = 'complaint-notes-access-token';
  values.set('campusAccessToken', accessToken);
  const requests = [];
  api.defaults.adapter = async (config) => {
    requests.push(config);
    return { data: [], status: 200, statusText: 'OK', headers: {}, config };
  };

  await api.get('/complaints/24/comments');
  await api.post('/complaints/24/comments', { content: 'Student comment' });
  await api.get('/complaints/24/resolution-notes');
  await api.post('/complaints/24/resolution-notes', { content: 'Resolution follow-up' });

  assert.deepEqual(requests.map(({ method, url }) => [method, url]), [
    ['get', '/complaints/24/comments'],
    ['post', '/complaints/24/comments'],
    ['get', '/complaints/24/resolution-notes'],
    ['post', '/complaints/24/resolution-notes'],
  ]);
  assert.ok(requests.every((config) => config.headers.Authorization === `Bearer ${accessToken}`));
});

test('resolution uploads retain the Bearer token and let the browser create the multipart boundary', async () => {
  values.set('campusAccessToken', 'test-access-token');
  const formData = new FormData();
  formData.append('resolution', new Blob(['{"note":"Repair completed"}'], { type: 'application/json' }));
  formData.append('image', new Blob(['image bytes'], { type: 'image/png' }), 'proof.png');
  let requestConfig;
  api.defaults.adapter = async (config) => {
    requestConfig = config;
    return { data: {}, status: 200, statusText: 'OK', headers: {}, config };
  };

  await api.post('/complaints/3/resolution', formData, {
    headers: { 'Content-Type': undefined },
  });

  assert.equal(requestConfig.headers.Authorization, 'Bearer test-access-token');
});

test('admin SLA requests do not reuse a stale default Authorization header when no session token exists', async () => {
  values.delete('campusAccessToken');
  api.defaults.headers.common.Authorization = 'Bearer stale-default-token';
  let requestConfig;
  api.defaults.adapter = async (config) => {
    requestConfig = config;
    return { data: {}, status: 200, statusText: 'OK', headers: {}, config };
  };

  await api.get('/admin/sla', { params: { page: 0, size: 20 } });

  assert.equal(requestConfig.headers.Authorization, undefined);
  delete api.defaults.headers.common.Authorization;
});

test('resolution uploads preserve their body and let the browser set the multipart boundary', async () => {
  values.set('campusAccessToken', 'multipart-access-token');
  const formData = new FormData();
  formData.append('resolution', new Blob(['{"note":"Completed"}'], { type: 'application/json' }));
  let requestConfig;
  api.defaults.adapter = async (config) => {
    requestConfig = config;
    return { data: {}, status: 200, statusText: 'OK', headers: {}, config };
  };

  await api.post('/complaints/3/resolution', formData, { headers: { 'Content-Type': undefined } });

  assert.notEqual(requestConfig.headers.getContentType(), 'multipart/form-data');
  assert.equal(requestConfig.data, formData);
});

test('expired protected-session responses clear the token and signal the auth context', async () => {
  values.set('campusAccessToken', 'expired-test-token');
  api.defaults.adapter = async (config) => {
    const response = { data: { error: 'unauthorized' }, status: 401, statusText: 'Unauthorized', headers: {}, config };
    throw new axios.AxiosError('Unauthorized', 'ERR_BAD_REQUEST', config, null, response);
  };

  await assert.rejects(api.get('/admin/assignment-options/staff', { params: { departmentId: 1 } }));
  assert.equal(values.has('campusAccessToken'), false);
  assert.equal(expiredEvents.length, 1);
  assert.match(
    getApiErrorMessage({
      config: { headers: { Authorization: ['Be', 'arer ', 'invalid-token'].join('') } },
      response: { status: 401, data: { error: 'unauthorized' } },
    }, 'fallback'),
    /session has expired.*sign in again/i,
  );
});

test('role-based 403 responses preserve a valid session', async () => {
  values.set('campusAccessToken', 'valid-session-token');
  const eventCount = expiredEvents.length;
  api.defaults.adapter = async (config) => {
    const response = { data: { error: 'forbidden' }, status: 403, statusText: 'Forbidden', headers: {}, config };
    throw new axios.AxiosError('Forbidden', 'ERR_BAD_REQUEST', config, null, response);
  };

  await assert.rejects(api.get('/complaints/5/image', { responseType: 'blob' }));
  assert.equal(values.get('campusAccessToken'), 'valid-session-token');
  assert.equal(expiredEvents.length, eventCount);
});

test('a late 401 from an old session cannot clear a newly authenticated token', async () => {
  values.set('campusAccessToken', 'old-session-token');
  let rejectRequest;
  api.defaults.adapter = (config) => new Promise((resolve, reject) => {
    rejectRequest = () => reject(new axios.AxiosError(
      'Unauthorized',
      'ERR_BAD_REQUEST',
      config,
      null,
      {
        data: { error: 'unauthorized' },
        status: 401,
        statusText: 'Unauthorized',
        headers: {},
        config,
      },
    ));
  });

  const pendingRequest = api.get('/auth/me');
  await new Promise((resolve) => setImmediate(resolve));
  values.set('campusAccessToken', 'new-session-token');
  rejectRequest();

  await assert.rejects(pendingRequest);
  assert.equal(values.get('campusAccessToken'), 'new-session-token');
  assert.equal(expiredEvents.length, 1);
});

test('public login requests remove inherited bearer credentials', async () => {
  values.set('campusAccessToken', 'stale-session-token');
  let requestConfig;
  api.defaults.adapter = async (config) => {
    requestConfig = config;
    return { data: {}, status: 200, statusText: 'OK', headers: {}, config };
  };

  await api.post('/auth/login', { email: 'faculty@example.test', password: 'example' }, {
    headers: { Authorization: 'Bearer inherited-token' },
  });

  assert.equal(requestConfig.headers.Authorization, undefined);
});

test('public auth failures and unauthenticated requests do not emit session-expiry events repeatedly', async () => {
  const eventCount = expiredEvents.length;
  values.delete('campusAccessToken');
  api.defaults.adapter = async (config) => {
    const response = { data: { error: 'unauthorized' }, status: 401, statusText: 'Unauthorized', headers: {}, config };
    throw new axios.AxiosError('Unauthorized', 'ERR_BAD_REQUEST', config, null, response);
  };

  await assert.rejects(api.get('/faculty/analytics'));
  await assert.rejects(api.post('/auth/login', { email: 'faculty@example.test', password: 'example' }));
  assert.equal(values.has('campusAccessToken'), false);
  assert.equal(expiredEvents.length, eventCount);
});

test('unrelated 401 responses do not clear the existing session', async () => {
  values.set('campusAccessToken', 'existing-test-token');
  api.defaults.adapter = async (config) => {
    const response = { data: { error: 'not_found' }, status: 401, statusText: 'Unauthorized', headers: {}, config };
    throw new axios.AxiosError('Unauthorized', 'ERR_BAD_REQUEST', config, null, response);
  };

  await assert.rejects(api.get('/complaints/3'));
  assert.equal(values.get('campusAccessToken'), 'existing-test-token');
  assert.equal(expiredEvents.length, 1);
  assert.equal(
    getApiErrorMessage({ response: { status: 401, data: { message: 'Authentication is required.' } } }, 'fallback'),
    'Authentication is required.',
  );
});

test('image validation and upload-size errors preserve the session and surface the server message', async () => {
  values.set('campusAccessToken', 'existing-test-token');
  for (const [status, error, message] of [
    [400, 'bad_request', 'Only valid JPG, PNG, and WEBP images are supported.'],
    [413, 'payload_too_large', 'Images must be 5 MB or smaller.'],
  ]) {
    api.defaults.adapter = async (config) => {
      const response = { data: { error, message }, status, statusText: 'Request failed', headers: {}, config };
      throw new axios.AxiosError('Request failed', 'ERR_BAD_REQUEST', config, null, response);
    };

    let requestError;
    try {
      await api.post('/complaints/3/resolution', new FormData(), {
        headers: { 'Content-Type': undefined },
      });
    } catch (errorResponse) {
      requestError = errorResponse;
    }
    assert.equal(getApiErrorMessage(requestError, 'fallback'), message);
    assert.equal(values.get('campusAccessToken'), 'existing-test-token');
  }
  assert.equal(expiredEvents.length, 1);
});

test('invalid login responses do not trigger session-expiry handling', async () => {
  values.set('campusAccessToken', 'existing-test-token');
  api.defaults.adapter = async (config) => {
    const response = { data: { error: 'unauthorized' }, status: 401, statusText: 'Unauthorized', headers: {}, config };
    throw new axios.AxiosError('Unauthorized', 'ERR_BAD_REQUEST', config, null, response);
  };

  await assert.rejects(api.post('/auth/login', { email: 'invalid@example.test', password: 'invalid' }));
  assert.equal(values.get('campusAccessToken'), 'existing-test-token');
  assert.equal(expiredEvents.length, 1);
});

test('login errors distinguish invalid credentials from unavailable services', () => {
  assert.equal(
    getLoginErrorMessage({ response: { status: 401, data: { error: 'unauthorized' } } }),
    'Email or password is incorrect. Check your details and try again.',
  );
  assert.equal(
    getLoginErrorMessage({ response: { status: 500, data: '<html>error</html>' } }),
    'CampusCare could not complete sign-in right now. Please try again shortly.',
  );
  assert.equal(
    getLoginErrorMessage({ code: 'ECONNREFUSED' }),
    'Could not reach the campus service. Check that the backend is running and try again.',
  );
  assert.equal(
    getLoginErrorMessage({ response: { status: 400, data: { message: 'Email is required.' } } }),
    'Email is required.',
  );
});

test('expired sessions on protected image requests normalize JSON blobs and clear the token', async () => {
  for (const endpoint of [
    '/complaints/5/image',
    '/complaints/5/resolution-proof',
  ]) {
    const eventCount = expiredEvents.length;
    values.set('campusAccessToken', 'expired-image-session-token');
    api.defaults.adapter = async (config) => {
      const response = {
        data: new Blob([JSON.stringify({
          error: 'unauthorized',
          message: 'Authentication is required.',
        })], { type: 'application/json' }),
        status: 401,
        statusText: 'Unauthorized',
        headers: { 'content-type': 'application/json' },
        config,
      };
      throw new axios.AxiosError('Unauthorized', 'ERR_BAD_REQUEST', config, null, response);
    };

    let requestError;
    try {
      await api.get(endpoint, { responseType: 'blob' });
    } catch (error) {
      requestError = error;
    }

    assert.ok(requestError);
    assert.deepEqual(requestError.response.data, {
      error: 'unauthorized',
      message: 'Authentication is required.',
    });
    assert.equal(values.has('campusAccessToken'), false);
    assert.equal(expiredEvents.length, eventCount + 1);
    assert.match(getApiErrorMessage(requestError, 'fallback'), /session has expired.*sign in again/i);
  }
});

test('admin audit search requests attach session Bearer token and pass query parameters', async () => {
  values.set('campusAccessToken', 'admin-audit-token');
  let capturedConfig;
  api.defaults.adapter = async (config) => {
    capturedConfig = config;
    return {
      data: { content: [], totalPages: 0, totalElements: 0 },
      status: 200,
      statusText: 'OK',
      headers: {},
      config,
    };
  };

  const response = await api.get('/admin/audit', {
    params: {
      page: 0,
      size: 20,
      search: 'water',
      action: 'COMPLAINT_STATUS_CHANGED',
      role: 'ADMIN',
    },
  });

  assert.equal(capturedConfig.headers.Authorization, 'Bearer admin-audit-token');
  assert.equal(capturedConfig.params.search, 'water');
  assert.equal(capturedConfig.params.action, 'COMPLAINT_STATUS_CHANGED');
  assert.equal(capturedConfig.params.role, 'ADMIN');
  assert.deepEqual(response.data.content, []);
});

test('admin audit CSV export requests blob response and getApiErrorMessage formats errors accurately', async () => {
  values.set('campusAccessToken', 'admin-audit-token');
  let capturedConfig;
  const csvBlob = new Blob(['Audit ID,Timestamp,Actor Name\n1,2026-10-10,System Admin\n'], { type: 'text/csv' });
  api.defaults.adapter = async (config) => {
    capturedConfig = config;
    return {
      data: csvBlob,
      status: 200,
      statusText: 'OK',
      headers: { 'content-type': 'text/csv' },
      config,
    };
  };

  const response = await api.get('/admin/audit/export.csv', {
    params: { action: 'COMPLAINT_ASSIGNED' },
    responseType: 'blob',
  });

  assert.equal(capturedConfig.headers.Authorization, 'Bearer admin-audit-token');
  assert.equal(capturedConfig.responseType, 'blob');
  assert.equal(capturedConfig.params.action, 'COMPLAINT_ASSIGNED');
  assert.equal(response.data, csvBlob);

  // Test error mappings
  assert.equal(
    getApiErrorMessage({ response: { status: 403, data: { message: 'Forbidden access' } } }, 'Default error'),
    'Forbidden access',
  );
  assert.equal(
    getApiErrorMessage({ code: 'ECONNABORTED' }, 'Default error'),
    'The request timed out. Please try again.',
  );
  assert.equal(
    getApiErrorMessage({ message: 'Network Error' }, 'Default error'),
    'Could not reach the campus service. Check that the backend is running and try again.',
  );
});

