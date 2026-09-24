package com.company.incidentdesk.application.authorization;

import java.util.Objects;
import java.util.Optional;

import com.company.incidentdesk.application.session.SessionProvider;
import com.company.incidentdesk.domain.account.Account;
import com.company.incidentdesk.domain.account.AccountId;
import com.company.incidentdesk.domain.account.Role;

/** Authorizes registration, promotion, account-management, and administrative operations. */
public final class AccountAuthorizationPolicy {
    private final SessionProvider sessionProvider;

    /**
     * Creates an account policy backed by the current session.
     *
     * @param sessionProvider active-session source
     */
    public AccountAuthorizationPolicy(SessionProvider sessionProvider) {
        this.sessionProvider = Objects.requireNonNull(sessionProvider, "sessionProvider");
    }

    /** Authorizes public registration only for reporter accounts. */
    public AuthorizationDecision authorizeRegistration(Role requestedRole) {
        return AuthorizationDecision.from(
                Objects.requireNonNull(requestedRole, "requestedRole") == Role.REPORTER);
    }

    /** Authorizes a reporter to request their own promotion. */
    public AuthorizationDecision authorizePromotionRequest(AccountId requesterId) {
        Objects.requireNonNull(requesterId, "requesterId");
        return decide(actor -> actor.role() == Role.REPORTER && actor.id().equals(requesterId));
    }

    /** Authorizes deciding a pending responder-promotion request. */
    public AuthorizationDecision authorizePromotionDecision() {
        return authorizeAdministrator();
    }

    /** Authorizes changing a responder's category access. */
    public AuthorizationDecision authorizeCategoryAccessChange() {
        return authorizeAdministrator();
    }

    /** Authorizes viewing the administrator account directory. */
    public AuthorizationDecision authorizeAccountDirectory() {
        return authorizeAdministrator();
    }

    /** Authorizes configuring category SLO targets. */
    public AuthorizationDecision authorizeSloConfiguration() {
        return authorizeAdministrator();
    }

    /** Authorizes viewing the application audit log. */
    public AuthorizationDecision authorizeAuditLog() {
        return authorizeAdministrator();
    }

    /** Authorizes deleting an account or initiating its password reset. */
    public AuthorizationDecision authorizeDeleteOrResetAccount() {
        return authorizeAdministrator();
    }

    private AuthorizationDecision authorizeAdministrator() {
        return decide(actor -> actor.role() == Role.ADMINISTRATOR);
    }

    private AuthorizationDecision decide(java.util.function.Predicate<Account> rule) {
        Optional<Account> currentAccount = sessionProvider.currentAccount();
        return AuthorizationDecision.from(currentAccount.filter(Account::isEnabled).filter(rule).isPresent());
    }
}
