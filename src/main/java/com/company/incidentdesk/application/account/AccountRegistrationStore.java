package com.company.incidentdesk.application.account;

import java.util.Optional;
import com.company.incidentdesk.domain.account.AccountId;

/** Atomic registration persistence and credential lookup boundary. */
public interface AccountRegistrationStore {
    void register(AccountRegistration registration);
    Optional<PasswordCredential> findCredential(AccountId accountId);
}
