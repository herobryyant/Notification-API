package com.bryant.notifications.queue;

import com.bryant.notifications.domain.NotificationEntity;
import com.bryant.notifications.domain.NotificationStatus;
import com.bryant.notifications.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationWorkerTest {
    @Test
    void successfulEmailDeliveryUpdatesStatus() {
        NotificationService service = mock(NotificationService.class);
        JavaMailSender mailSender = mock(JavaMailSender.class);
        NotificationEntity entity = new NotificationEntity("key", "person@example.com", "Welcome", "Hello");
        when(service.getById(entity.getId())).thenReturn(entity);
        NotificationWorker worker = new NotificationWorker(service, mailSender);

        worker.deliver(new NotificationMessage(entity.getId()));

        verify(mailSender).send(any(SimpleMailMessage.class));
        verify(service).markDelivered(entity.getId());
    }

    @Test
    void smtpFailureBubblesUpSoRabbitCanRetry() {
        NotificationService service = mock(NotificationService.class);
        JavaMailSender mailSender = mock(JavaMailSender.class);
        NotificationEntity entity = new NotificationEntity("key", "person@example.com", "Welcome", "Hello");
        when(service.getById(entity.getId())).thenReturn(entity);
        org.mockito.Mockito.doThrow(new MailSendException("SMTP unavailable"))
                .when(mailSender).send(any(SimpleMailMessage.class));
        NotificationWorker worker = new NotificationWorker(service, mailSender);

        assertThrows(MailSendException.class, () -> worker.deliver(new NotificationMessage(entity.getId())));
        verify(service, never()).markDelivered(entity.getId());
    }

    @Test
    void alreadyDeliveredNotificationIsNotSentAgain() {
        NotificationService service = mock(NotificationService.class);
        JavaMailSender mailSender = mock(JavaMailSender.class);
        NotificationEntity entity = new NotificationEntity("key", "person@example.com", "Welcome", "Hello");
        entity.setStatus(NotificationStatus.DELIVERED);
        when(service.getById(entity.getId())).thenReturn(entity);
        NotificationWorker worker = new NotificationWorker(service, mailSender);

        worker.deliver(new NotificationMessage(entity.getId()));

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }
}
