/**
 * Local-file persistence implementations and storage boundaries.
 *
 * <p>This layer implements repositories and safe storage operations behind
 * interfaces consumed by the application layer. It owns serialization,
 * schema-version handling, atomic replacement, recovery, and configurable
 * runtime-data paths; it must not contain business or presentation logic.</p>
 */
package com.company.incidentdesk.persistence;
