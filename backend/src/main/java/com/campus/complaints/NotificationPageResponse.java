package com.campus.complaints;

import org.springframework.data.domain.Page;

import java.util.List;

public record NotificationPageResponse(
        List<NotificationResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    static NotificationPageResponse from(Page<Notification> page) {
        return new NotificationPageResponse(
                page.getContent().stream().map(NotificationResponse::from).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
