package com.bryant.notifications.queue;

import com.bryant.notifications.domain.NotificationEntity;
import com.bryant.notifications.domain.NotificationStatus;
import com.bryant.notifications.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class NotificationWorker {
    private static final Logger log = LoggerFactory.getLogger(NotificationWorker.class);
    private final NotificationService notificationService;
    private final JavaMailSender mailSender;

    public NotificationWorker(NotificationService notificationService, JavaMailSender mailSender) {
        this.notificationService = notificationService;
        this.mailSender = mailSender;
    }

    @RabbitListener(queues = RabbitConfiguration.QUEUE)
    public void deliver(NotificationMessage notificationMessage) {
        NotificationEntity notification = notificationService.getById(notificationMessage.notificationId());
        if (notification.getStatus() == NotificationStatus.DELIVERED) {
            log.info("Skipping already delivered notification {}", notification.getId());
            return;
        }

        SimpleMailMessage email = new SimpleMailMessage();
        email.setTo(notification.getRecipientEmail());
        email.setSubject(notification.getSubject());
        email.setText(notification.getMessage());
        mailSender.send(email);
        notificationService.markDelivered(notification.getId());
        log.info("Delivered notification {}", notification.getId());
    }
}
