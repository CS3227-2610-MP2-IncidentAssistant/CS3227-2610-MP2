package com.company.incidentdesk.startup;

/** Enforces the Java feature version supported by Incident Desk. */
public final class JavaVersionRequirement {
    public static final int REQUIRED_FEATURE_VERSION = 25;

    private JavaVersionRequirement() {
    }

    /**
     * Verifies that the supplied runtime version is supported.
     *
     * @param runtimeVersion runtime version to verify
     * @throws IllegalStateException if the runtime is not Java 25
     */
    public static void verify(Runtime.Version runtimeVersion) {
        int actualFeatureVersion = runtimeVersion.feature();
        if (actualFeatureVersion != REQUIRED_FEATURE_VERSION) {
            throw new IllegalStateException(
                    "Incident Desk requires Java 25, but is running on Java "
                            + actualFeatureVersion + ".");
        }
    }
}
