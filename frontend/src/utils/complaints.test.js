import assert from 'node:assert/strict';
import test from 'node:test';
import {
  complaintStatuses,
  filterAdminComplaints,
  filterAndSortComplaints,
  summarizeAdminComplaints,
  summarizeComplaints,
  summarizeStaffComplaints,
} from './complaints.js';

const complaints = [
  { complaintId: 'CMP-001', title: 'Broken light', status: 'SUBMITTED', submittedAt: '2026-05-01T10:00:00' },
  { complaintId: 'CMP-002', title: 'Water leak', status: 'IN_PROGRESS', submittedAt: '2026-05-03T10:00:00' },
  { complaintId: 'CMP-003', title: 'Library noise', status: 'CLOSED', submittedAt: '2026-05-02T10:00:00' },
  { complaintId: 'CMP-004', title: 'Rejected request', status: 'REJECTED', submittedAt: '2026-05-04T10:00:00' },
];

test('complaint filters search ID or title, filter status, and sort newest first', () => {
  assert.deepEqual(
    filterAndSortComplaints(complaints, { query: 'cmp-00', status: 'IN_PROGRESS' }).map(({ complaintId }) => complaintId),
    ['CMP-002'],
  );
  assert.deepEqual(
    filterAndSortComplaints(complaints, { query: 'library' }).map(({ complaintId }) => complaintId),
    ['CMP-003'],
  );
  assert.deepEqual(
    filterAndSortComplaints(complaints).map(({ complaintId }) => complaintId),
    ['CMP-004', 'CMP-002', 'CMP-003', 'CMP-001'],
  );
  assert.deepEqual(filterAndSortComplaints([], { query: 'CMP' }), []);
});

test('complaint summaries count real, mutually exclusive workflow statuses', () => {
  const everyStatus = complaintStatuses.map((status) => ({ status }));
  assert.deepEqual(summarizeComplaints(everyStatus), {
    total: 7,
    pending: 2,
    inProgress: 2,
    resolved: 2,
  });
  assert.deepEqual(summarizeComplaints([]), {
    total: 0,
    pending: 0,
    inProgress: 0,
    resolved: 0,
  });
  assert.deepEqual(complaintStatuses, [
    'SUBMITTED', 'UNDER_REVIEW', 'ASSIGNED', 'IN_PROGRESS', 'RESOLVED', 'CLOSED', 'REJECTED',
  ]);
});

test('admin filters operate independently on category, priority, and department', () => {
  const records = complaints.map((complaint, index) => ({
    ...complaint,
    category: index < 2 ? 'Electricity' : 'Facilities',
    priority: index === 0 ? 'HIGH' : 'LOW',
    department: index < 3 ? 'Engineering' : null,
  }));
  assert.deepEqual(filterAdminComplaints(records, { category: 'Electricity' }).map((item) => item.complaintId),
    ['CMP-002', 'CMP-001']);
  assert.deepEqual(filterAdminComplaints(records, { priority: 'HIGH' }).map((item) => item.complaintId),
    ['CMP-001']);
  assert.deepEqual(filterAdminComplaints(records, { department: 'Engineering' }).map((item) => item.complaintId),
    ['CMP-002', 'CMP-003', 'CMP-001']);
  assert.deepEqual(filterAdminComplaints(records, {
    query: 'not matching', category: 'Electricity', priority: 'LOW', department: 'Engineering',
  }), []);
});

test('admin overview counts exactly the real total, submitted, active, and resolved statuses', () => {
  const everyStatus = complaintStatuses.map((status) => ({ status }));
  assert.deepEqual(summarizeAdminComplaints(everyStatus), {
    total: 7,
    submitted: 1,
    inProgress: 2,
    resolved: 2,
  });

  test('staff overview counts only assigned work and reports each complaint once', () => {
    const statuses = ['SUBMITTED', 'UNDER_REVIEW', 'ASSIGNED', 'IN_PROGRESS', 'RESOLVED', 'CLOSED', 'REJECTED'];
    assert.deepEqual(summarizeStaffComplaints(statuses.map((status) => ({ status }))), {
      total: 7,
      awaitingWork: 3,
      inProgress: 1,
      resolved: 1,
    });
    assert.deepEqual(summarizeStaffComplaints([]), {
      total: 0,
      awaitingWork: 0,
      inProgress: 0,
      resolved: 0,
    });
  });
  assert.deepEqual(summarizeAdminComplaints([]), {
    total: 0,
    submitted: 0,
    inProgress: 0,
    resolved: 0,
  });
});
