package com.bryant.notifications.queue;

import com.bryant.notifications.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class NotificationMessageRecovererTest {
    @Test
    void exhaustedRetriesMarkNotificationFailedAndRejectToDeadLetterQueue() throws Exception {
        NotificationService service = mock(NotificationService.class);
        UUID id = UUID.randomUUID();
        ObjectMapper mapper = new ObjectMapper();
        Message message = new Message(mapper.writeValueAsBytes(new NotificationMessage(id)), new MessageProperties());
        MessageRecoverer recoverer = new RabbitConfiguration().notificationMessageRecoverer(service, mapper);

        assertThrows(AmqpRejectAndDontRequeueException.class,
                () -> recoverer.recover(message, new IllegalStateException("SMTP unavailable")));

        verify(service).markFailed(eq(id), contains("SMTP unavailable"));
    }
}
