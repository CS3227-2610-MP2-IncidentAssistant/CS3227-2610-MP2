package com.company.incidentdesk.persistence.file;

import java.nio.file.Path;
import java.util.Objects;

/** Resolves the writable application-data directory outside packaged resources. */
public final class ApplicationDataDirectory {
    public static final String SYSTEM_PROPERTY = "incidentdesk.dataDir";
    public static final String ENVIRONMENT_VARIABLE = "INCIDENT_DESK_DATA_DIR";

    private ApplicationDataDirectory() {
    }

    public static Path resolve() {
        String configured = System.getProperty(SYSTEM_PROPERTY);
        if (configured == null || configured.isBlank()) {
            configured = System.getenv(ENVIRONMENT_VARIABLE);
        }
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured).toAbsolutePath().normalize();
        }
        return Path.of(Objects.requireNonNull(System.getProperty("user.home"), "user.home"),
                ".incident-desk").toAbsolutePath().normalize();
    }
}
