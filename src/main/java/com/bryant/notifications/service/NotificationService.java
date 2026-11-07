package com.bryant.notifications.service;

import com.bryant.notifications.api.CreateNotificationRequest;
import com.bryant.notifications.domain.NotificationEntity;
import com.bryant.notifications.domain.NotificationRepository;
import com.bryant.notifications.domain.NotificationStatus;
import com.bryant.notifications.queue.NotificationMessage;
import com.bryant.notifications.service.NotificationExceptions.IdempotencyConflict;
import com.bryant.notifications.service.NotificationExceptions.NotFound;
import com.bryant.notifications.service.NotificationExceptions.QueueUnavailable;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class NotificationService {
    private final NotificationRepository repository;
    private final RedisRateLimiter rateLimiter;
    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final String routingKey;

    public NotificationService(NotificationRepository repository,
                               RedisRateLimiter rateLimiter,
                               RabbitTemplate rabbitTemplate,
                               @Value("${app.rabbit.exchange}") String exchange,
                               @Value("${app.rabbit.routing-key}") String routingKey) {
        this.repository = repository;
        this.rateLimiter = rateLimiter;
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
        this.routingKey = routingKey;
    }

    public NotificationEntity createAndQueue(String idempotencyKey, CreateNotificationRequest request) {
        String normalizedKey = idempotencyKey.trim();
        if (normalizedKey.length() > 128) {
            throw new IllegalArgumentException("Idempotency-Key must be 128 characters or fewer");
        }

        var previous = repository.findByIdempotencyKey(normalizedKey);
        if (previous.isPresent()) {
            return returnIfSameRequest(previous.get(), request);
        }

        rateLimiter.check(request.recipientEmail());
        NotificationEntity entity = new NotificationEntity(normalizedKey, request.recipientEmail().trim(),
                request.subject().trim(), request.message().trim());
        NotificationEntity saved;
        try {
            saved = repository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException e) {
            return repository.findByIdempotencyKey(normalizedKey)
                    .map(existing -> returnIfSameRequest(existing, request))
                    .orElseThrow(() -> e);
        }

        try {
            rabbitTemplate.convertAndSend(exchange, routingKey, new NotificationMessage(saved.getId()));
        } catch (AmqpException e) {
            markFailed(saved.getId(), "Message broker unavailable");
            throw new QueueUnavailable("Could not queue the notification", e);
        }
        return saved;
    }

    private NotificationEntity returnIfSameRequest(NotificationEntity existing, CreateNotificationRequest request) {
        boolean same = existing.getRecipientEmail().equalsIgnoreCase(request.recipientEmail().trim())
                && existing.getSubject().equals(request.subject().trim())
                && existing.getMessage().equals(request.message().trim());
        if (!same) {
            throw new IdempotencyConflict("This Idempotency-Key was already used with a different request");
        }
        return existing;
    }

    public NotificationEntity getById(UUID id) {
        return repository.findById(id).orElseThrow(() -> new NotFound("Notification not found"));
    }

    @Transactional
    public void markDelivered(UUID id) {
        NotificationEntity entity = getById(id);
        if (entity.getStatus() == NotificationStatus.DELIVERED) return;
        entity.setStatus(NotificationStatus.DELIVERED);
        entity.setErrorMessage(null);
        repository.save(entity);
    }

    @Transactional
    public void markFailed(UUID id, String reason) {
        repository.findById(id).ifPresent(entity -> {
            if (entity.getStatus() != NotificationStatus.DELIVERED) {
                entity.setStatus(NotificationStatus.FAILED);
                entity.setErrorMessage(reason == null ? "Delivery failed" : reason.substring(0, Math.min(reason.length(), 500)));
                repository.save(entity);
            }
        });
    }
}
