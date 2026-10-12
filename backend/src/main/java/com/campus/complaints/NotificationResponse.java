package com.campus.complaints;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long id,
        String title,
        String message,
        String type,
        Long complaintId,
        String complaintCode,
        LocalDateTime createdAt,
        LocalDateTime readAt,
        boolean read) {

    static NotificationResponse from(Notification notification) {
        Complaint complaint = notification.getComplaint();
        return new NotificationResponse(
                notification.getId(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getType(),
                complaint == null ? null : complaint.getId(),
                complaint == null ? null : complaint.getComplaintCode(),
                notification.getCreatedAt(),
                notification.getReadAt(),
                notification.getReadAt() != null);
    }
}
