package com.example.purchase.notification.service;

import com.example.purchase.notification.domain.Notification;

import java.time.LocalDateTime;

public record NotificationRow(Long id, String message, boolean read, LocalDateTime createdAt) {

    public static NotificationRow of(Notification n) {
        return new NotificationRow(n.getId(), n.getMessage(), n.isRead(), n.getCreatedAt());
    }
}
