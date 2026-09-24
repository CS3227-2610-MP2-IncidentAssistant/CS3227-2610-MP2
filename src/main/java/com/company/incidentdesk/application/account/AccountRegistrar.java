package com.company.incidentdesk.application.account;

import com.company.incidentdesk.domain.account.Role;

/** Registration use case exposed to presentation code. */
@FunctionalInterface
public interface AccountRegistrar {
    RegistrationResult register(String loginName, char[] password, Role role);
}
