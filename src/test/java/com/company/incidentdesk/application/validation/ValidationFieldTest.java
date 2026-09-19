package com.company.incidentdesk.application.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Tests stable validation field identifiers. */
class ValidationFieldTest {
    @Test
    void retainsFieldIdentifier() {
        assertEquals("incident.title", new ValidationField("incident.title").value());
    }

    @Test
    void rejectsNullOrBlankIdentifier() {
        assertThrows(NullPointerException.class, () -> new ValidationField(null));
        assertThrows(IllegalArgumentException.class, () -> new ValidationField(" \n "));
    }
}
