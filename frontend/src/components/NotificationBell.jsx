import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import AppIcon from './AppIcon';
import { useAuth } from '../context/AuthContext';
import api from '../services/api';

let unreadCountRequest;

const complaintPathForRole = {
  ADMIN: '/admin/complaints',
  STAFF: '/staff/complaints',
  FACULTY: '/faculty/complaints',
  STUDENT: '/student/complaints',
};

function formatDate(value) {
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' })
    .format(new Date(value));
}

function NotificationBell({ role }) {
  const { user, loading: authLoading } = useAuth();
  const navigate = useNavigate();
  const rootRef = useRef(null);
  const [open, setOpen] = useState(false);
  const [notifications, setNotifications] = useState([]);
  const [unreadCount, setUnreadCount] = useState(0);
  const [page, setPage] = useState(0);
  const [hasMore, setHasMore] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [updatingId, setUpdatingId] = useState(null);
  const [markingAll, setMarkingAll] = useState(false);
  const pollingRef = useRef(false);
  const highestLoadedPageRef = useRef(0);
  const notificationPageRequestsRef = useRef(new Map());
  const accessToken = window.sessionStorage.getItem('campusAccessToken');
  const authenticated = !authLoading && user?.role === role && Boolean(accessToken);

  const refreshUnreadCount = async (token) => {
    if (!token || window.sessionStorage.getItem('campusAccessToken') !== token) return null;
    if (!unreadCountRequest || unreadCountRequest.token !== token) {
      const promise = api.get('/notifications/unread-count').then(({ data }) => data.count);
      unreadCountRequest = { token, promise };
      promise.then(
        () => {
          if (unreadCountRequest?.promise === promise) unreadCountRequest = null;
        },
        () => {
          if (unreadCountRequest?.promise === promise) unreadCountRequest = null;
        },
      );
    }
    return unreadCountRequest.promise;
  };

  const requestPage = (requestedPage) => {
    const requests = notificationPageRequestsRef.current;
    if (!requests.has(requestedPage)) {
      const promise = api.get('/notifications', { params: { page: requestedPage, size: 10 } })
        .then(({ data }) => data);
      requests.set(requestedPage, promise);
      promise.then(
        () => {
          if (requests.get(requestedPage) === promise) requests.delete(requestedPage);
        },
        () => {
          if (requests.get(requestedPage) === promise) requests.delete(requestedPage);
        },
      );
    }
    return requests.get(requestedPage);
  };

  const loadPage = async (requestedPage, append = false) => {
    if (!authenticated || window.sessionStorage.getItem('campusAccessToken') !== accessToken) return;
    setLoading(true);
    setError('');
    try {
      const [data, countResult] = await Promise.all([
        requestPage(requestedPage),
        refreshUnreadCount(accessToken),
      ]);
      setNotifications((current) => append ? [...current, ...data.content] : data.content);
      setPage(data.page);
      highestLoadedPageRef.current = append ? requestedPage : 0;
      setHasMore(data.page + 1 < data.totalPages);
      if (countResult !== null && window.sessionStorage.getItem('campusAccessToken') === accessToken) {
        setUnreadCount(countResult);
      }
    } catch (requestError) {
      setError(requestError.response?.data?.message || 'Notifications could not be loaded. Please retry.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (!authenticated) return undefined;
    let active = true;
    highestLoadedPageRef.current = 0;
    setNotifications([]);
    setPage(0);
    setHasMore(false);
    setLoading(true);
    setError('');
    const poll = async () => {
      if (pollingRef.current || window.sessionStorage.getItem('campusAccessToken') !== accessToken) return;
      pollingRef.current = true;
      try {
        const [data, count] = await Promise.all([
          requestPage(0),
          refreshUnreadCount(accessToken),
        ]);
        if (!active || count === null
          || window.sessionStorage.getItem('campusAccessToken') !== accessToken) return;
        setNotifications((current) => {
          const latestIds = new Set(data.content.map((notification) => notification.id));
          return [...data.content, ...current.filter((notification) => !latestIds.has(notification.id))];
        });
        setUnreadCount(count);
        setHasMore(data.totalPages > highestLoadedPageRef.current + 1);
        setError('');
      } catch (requestError) {
        if (active && window.sessionStorage.getItem('campusAccessToken') === accessToken) {
          setError(requestError.response?.data?.message || 'Notifications could not be loaded. Please retry.');
        }
      } finally {
        pollingRef.current = false;
        if (active) setLoading(false);
      }
    };
    poll();
    const intervalId = window.setInterval(poll, 5000);
    return () => {
      active = false;
      window.clearInterval(intervalId);
    };
  }, [authenticated, accessToken, user?.id]);

  useEffect(() => {
    if (!open) return undefined;
    const closeOnOutsideClick = (event) => {
      if (!rootRef.current?.contains(event.target)) setOpen(false);
    };
    const closeOnEscape = (event) => {
      if (event.key === 'Escape') setOpen(false);
    };
    document.addEventListener('mousedown', closeOnOutsideClick);
    document.addEventListener('keydown', closeOnEscape);
    return () => {
      document.removeEventListener('mousedown', closeOnOutsideClick);
      document.removeEventListener('keydown', closeOnEscape);
    };
  }, [open]);

  const toggleOpen = () => {
    const nextOpen = !open;
    setOpen(nextOpen);
    if (nextOpen) loadPage(0);
  };

  const markRead = async (notification) => {
    if (!authenticated || window.sessionStorage.getItem('campusAccessToken') !== accessToken) return false;
    if (notification.read) return true;
    if (updatingId !== null) return false;
    setUpdatingId(notification.id);
    setError('');
    try {
      const { data } = await api.patch(`/notifications/${notification.id}/read`);
      setNotifications((current) => current.map((item) => item.id === data.id ? data : item));
      setUnreadCount((count) => Math.max(0, count - 1));
      return true;
    } catch (requestError) {
      setError(requestError.response?.data?.message || 'This notification could not be marked as read.');
      return false;
    } finally {
      setUpdatingId(null);
    }
  };

  const openNotification = async (notification) => {
    if (!await markRead(notification)) return;
    setOpen(false);
    if (notification.complaintId) {
      navigate(`${complaintPathForRole[role]}/${notification.complaintId}`);
    }
  };

  const markAllRead = async () => {
    if (!authenticated || window.sessionStorage.getItem('campusAccessToken') !== accessToken) return;
    setMarkingAll(true);
    setError('');
    try {
      const { data } = await api.patch('/notifications/read-all');
      setNotifications((current) => current.map((notification) => ({
        ...notification,
        read: true,
        readAt: notification.readAt || data.readAt,
      })));
      setUnreadCount(0);
    } catch (requestError) {
      setError(requestError.response?.data?.message || 'Notifications could not be marked as read.');
    } finally {
      setMarkingAll(false);
    }
  };

  if (authLoading || !authenticated) return null;

  return (
    <div className="notification-bell" ref={rootRef}>
      <button className="notification-bell-button" type="button" aria-label="Notifications"
        aria-expanded={open} aria-controls="notification-panel" onClick={toggleOpen}>
        {loading && unreadCount === 0
          ? <span className="notification-count-loading" role="status">Loading</span>
          : <AppIcon name="bell" size={19} />}
        {unreadCount > 0 && <span className="notification-count">{unreadCount > 99 ? '99+' : unreadCount}</span>}
      </button>
      {open && (
        <section className="notification-panel" id="notification-panel" aria-label="Notifications">
          <div className="notification-panel-heading">
            <div><h2>Notifications</h2><span>{unreadCount} unread</span></div>
            <button type="button" className="notification-mark-all" onClick={markAllRead}
              disabled={unreadCount === 0 || markingAll}>
              {markingAll ? 'Updating…' : 'Mark all as read'}
            </button>
          </div>
          {error && <div className="notification-error" role="alert">{error}
            <button type="button" onClick={() => loadPage(0)}>Retry</button>
          </div>}
          {loading && notifications.length === 0 && <p className="notification-state" role="status">Loading notifications…</p>}
          {!loading && !error && notifications.length === 0 && (
            <p className="notification-state">You're all caught up.</p>
          )}
          <div className="notification-list">
            {notifications.map((notification) => (
              <article className={`notification-item${notification.read ? ' notification-read' : ' notification-unread'}`}
                key={notification.id}>
                <button className="notification-open" type="button" onClick={() => openNotification(notification)}>
                  <span className="notification-title">{notification.title}</span>
                  <span className="notification-message">{notification.message}</span>
                  <time dateTime={notification.createdAt}>{formatDate(notification.createdAt)}</time>
                  {notification.complaintCode && <span className="notification-complaint">{notification.complaintCode}</span>}
                </button>
                {!notification.read && (
                  <button className="notification-mark-one" type="button"
                    onClick={() => markRead(notification)} disabled={updatingId === notification.id}>
                    {updatingId === notification.id ? 'Saving…' : 'Mark read'}
                  </button>
                )}
                {notification.read && <span className="notification-read-label">Read</span>}
              </article>
            ))}
          </div>
          {hasMore && <button className="notification-load-more" type="button" disabled={loading}
            onClick={() => loadPage(page + 1, true)}>
            {loading ? 'Loading…' : 'Load more'}
          </button>}
        </section>
      )}
    </div>
  );
}

export default NotificationBell;
