package com.company.incidentdesk.application.account;

/** Changes the active account's password without exposing credential storage. */
@FunctionalInterface
public interface PasswordChanger {
    PasswordChangeResult changePassword(char[] currentPassword, char[] newPassword, char[] confirmation);
}
