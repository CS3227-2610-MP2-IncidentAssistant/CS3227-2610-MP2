package com.company.incidentdesk.application.authorization;

import java.util.Objects;
import java.util.Optional;

import com.company.incidentdesk.application.session.SessionProvider;
import com.company.incidentdesk.domain.account.Account;
import com.company.incidentdesk.domain.account.AccountId;
import com.company.incidentdesk.domain.account.Role;

/** Authorizes access to personal responder performance and company statistics. */
public final class StatisticsAuthorizationPolicy {
    private final SessionProvider sessionProvider;

    /**
     * Creates a statistics policy backed by the current session.
     *
     * @param sessionProvider active-session source
     */
    public StatisticsAuthorizationPolicy(SessionProvider sessionProvider) {
        this.sessionProvider = Objects.requireNonNull(sessionProvider, "sessionProvider");
    }

    /** Authorizes a responder's performance view for the requested responder. */
    public AuthorizationDecision authorizeResponderPerformance(AccountId responderId) {
        Objects.requireNonNull(responderId, "responderId");
        return decide(actor -> canViewResponderPerformance(actor, responderId));
    }

    /** Authorizes aggregate company and cross-account statistics. */
    public AuthorizationDecision authorizeCompanyStatistics() {
        return decide(actor -> actor.role() == Role.ADMINISTRATOR);
    }

    private AuthorizationDecision decide(java.util.function.Predicate<Account> rule) {
        Optional<Account> currentAccount = sessionProvider.currentAccount();
        return AuthorizationDecision.from(currentAccount.filter(Account::isEnabled).filter(rule).isPresent());
    }

    private static boolean canViewResponderPerformance(Account actor, AccountId responderId) {
        boolean isAdministrator = actor.role() == Role.ADMINISTRATOR;
        boolean isOwnResponderPerformance = actor.role() == Role.RESPONDER
                && actor.id().equals(responderId);
        return isAdministrator || isOwnResponderPerformance;
    }
}
