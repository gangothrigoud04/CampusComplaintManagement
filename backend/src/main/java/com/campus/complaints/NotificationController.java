package com.campus.complaints;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public NotificationPageResponse list(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        return notificationService.list(principal.id(), page, size);
    }

    @GetMapping("/unread-count")
    public UnreadCountResponse unreadCount(@AuthenticationPrincipal AuthenticatedUser principal) {
        return new UnreadCountResponse(notificationService.unreadCount(principal.id()));
    }

    @PatchMapping("/{notificationId}/read")
    public NotificationResponse markRead(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long notificationId) {
        return notificationService.markRead(principal.id(), notificationId);
    }

    @PatchMapping("/read-all")
    public MarkAllReadResponse markAllRead(@AuthenticationPrincipal AuthenticatedUser principal) {
        LocalDateTime readAt = LocalDateTime.now();
        int updated = notificationService.markAllRead(principal.id(), readAt);
        return new MarkAllReadResponse(updated, updated == 0 ? null : readAt);
    }

    public record UnreadCountResponse(long count) {
    }

    public record MarkAllReadResponse(int updated, LocalDateTime readAt) {
    }
}
