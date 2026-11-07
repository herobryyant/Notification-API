package com.bryant.notifications.api;

import com.bryant.notifications.domain.NotificationEntity;
import com.bryant.notifications.domain.NotificationStatus;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(UUID id, NotificationStatus status, Instant createdAt, Instant updatedAt) {
    public static NotificationResponse from(NotificationEntity entity) {
        return new NotificationResponse(entity.getId(), entity.getStatus(), entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
