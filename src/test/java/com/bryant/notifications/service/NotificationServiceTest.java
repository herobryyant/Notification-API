package com.bryant.notifications.service;

import com.bryant.notifications.api.CreateNotificationRequest;
import com.bryant.notifications.domain.NotificationEntity;
import com.bryant.notifications.domain.NotificationRepository;
import com.bryant.notifications.domain.NotificationStatus;
import com.bryant.notifications.queue.NotificationMessage;
import com.bryant.notifications.service.NotificationExceptions.IdempotencyConflict;
import com.bryant.notifications.service.NotificationExceptions.QueueUnavailable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {
    @Mock NotificationRepository repository;
    @Mock RedisRateLimiter rateLimiter;
    @Mock org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate;

    private NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(repository, rateLimiter, rabbitTemplate,
                "notifications.exchange", "notifications.send");
    }

    @Test
    void sameIdempotencyKeyAndPayloadReturnsOriginalWithoutRepublishing() {
        var existing = new NotificationEntity("demo-key", "person@example.com", "Hello", "Body");
        when(repository.findByIdempotencyKey("demo-key")).thenReturn(Optional.of(existing));

        var result = service.createAndQueue("demo-key", request("person@example.com", "Hello", "Body"));

        assertSame(existing, result);
        verifyNoInteractions(rateLimiter, rabbitTemplate);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void reusingIdempotencyKeyWithDifferentPayloadReturnsConflict() {
        var existing = new NotificationEntity("demo-key", "person@example.com", "Hello", "Body");
        when(repository.findByIdempotencyKey("demo-key")).thenReturn(Optional.of(existing));

        assertThrows(IdempotencyConflict.class,
                () -> service.createAndQueue("demo-key", request("person@example.com", "Changed", "Body")));

        verifyNoInteractions(rateLimiter, rabbitTemplate);
    }

    @Test
    void newRequestIsSavedAndQueued() {
        when(repository.findByIdempotencyKey("new-key")).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(NotificationEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.createAndQueue("new-key", request("person@example.com", "Hello", "Body"));

        assertEquals(NotificationStatus.QUEUED, result.getStatus());
        verify(rateLimiter).check("person@example.com");
        verify(rabbitTemplate).convertAndSend(eq("notifications.exchange"), eq("notifications.send"),
                eq(new NotificationMessage(result.getId())));
    }

    @Test
    void brokerFailureMarksSavedNotificationFailedAndReturnsServiceUnavailableError() {
        NotificationEntity savedEntity = new NotificationEntity("new-key", "person@example.com", "Hello", "Body");
        when(repository.findByIdempotencyKey("new-key")).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(NotificationEntity.class))).thenReturn(savedEntity);
        when(repository.findById(savedEntity.getId())).thenReturn(Optional.of(savedEntity));
        doThrow(new AmqpException("broker unavailable"))
                .when(rabbitTemplate).convertAndSend(eq("notifications.exchange"), eq("notifications.send"),
                        any(NotificationMessage.class));

        assertThrows(QueueUnavailable.class,
                () -> service.createAndQueue("new-key", request("person@example.com", "Hello", "Body")));

        assertEquals(NotificationStatus.FAILED, savedEntity.getStatus());
        verify(repository).save(savedEntity);
    }

    private CreateNotificationRequest request(String email, String subject, String message) {
        return new CreateNotificationRequest(email, subject, message);
    }
}
