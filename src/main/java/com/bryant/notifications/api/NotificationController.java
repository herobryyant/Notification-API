package com.bryant.notifications.api;

import com.bryant.notifications.domain.NotificationEntity;
import com.bryant.notifications.service.NotificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@Validated
public class NotificationController {
    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping
    public ResponseEntity<NotificationResponse> create(
            @RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey,
            @Valid @RequestBody CreateNotificationRequest request) {
        NotificationEntity notification = notificationService.createAndQueue(idempotencyKey, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(NotificationResponse.from(notification));
    }

    @GetMapping("/{id}")
    public NotificationResponse getStatus(@PathVariable UUID id) {
        return NotificationResponse.from(notificationService.getById(id));
    }
}
