package com.bryant.notifications.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateNotificationRequest(
        @NotBlank @Email @Size(max = 320) String recipientEmail,
        @NotBlank @Size(max = 160) String subject,
        @NotBlank @Size(max = 5000) String message
) { }
