package com.company.incidentdesk.startup;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Tests Java runtime version enforcement. */
class JavaVersionRequirementTest {
    @Test
    void acceptsJava25() {
        assertDoesNotThrow(() -> JavaVersionRequirement.verify(Runtime.Version.parse("25.0.1")));
    }

    @Test
    void rejectsEarlierJavaVersionsWithAnActionableMessage() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> JavaVersionRequirement.verify(Runtime.Version.parse("24.0.2")));

        assertTrue(exception.getMessage().contains("requires Java 25"));
        assertTrue(exception.getMessage().contains("Java 24"));
    }

    @Test
    void rejectsLaterJavaVersionsToKeepRuntimeBehaviorReproducible() {
        assertThrows(
                IllegalStateException.class,
                () -> JavaVersionRequirement.verify(Runtime.Version.parse("26")));
    }
}
