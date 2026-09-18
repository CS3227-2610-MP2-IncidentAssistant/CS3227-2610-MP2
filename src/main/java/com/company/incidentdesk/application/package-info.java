/**
 * Application use cases and session-aware orchestration.
 *
 * <p>This layer coordinates domain operations for authenticated users. It
 * obtains actors from the active session, invokes domain rules, and defines
 * transaction boundaries without depending on JavaFX or concrete file-storage
 * implementations.</p>
 */
package com.company.incidentdesk.application;
