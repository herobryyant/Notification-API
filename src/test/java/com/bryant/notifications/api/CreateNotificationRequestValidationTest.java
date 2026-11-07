package com.bryant.notifications.api;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreateNotificationRequestValidationTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void rejectsInvalidEmailAndBlankFields() {
        var request = new CreateNotificationRequest("not-an-email", " ", " ");

        var violations = validator.validate(request);

        assertFalse(violations.isEmpty());
    }

    @Test
    void acceptsWellFormedNotification() {
        var request = new CreateNotificationRequest("person@example.com", "Welcome", "Hello there");

        assertTrue(validator.validate(request).isEmpty());
    }
}
