package com.leonardogalvao.pontocontrole.auth;

import com.leonardogalvao.pontocontrole.users.UserAccount;
import com.leonardogalvao.pontocontrole.users.UserRole;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.List;
import java.util.UUID;

public final class AuthenticatedUserDetails extends User {

    private final UUID id;
    private final UserRole role;
    private final boolean mustChangePassword;

    public AuthenticatedUserDetails(UserAccount account) {
        super(
                account.getEmail(),
                account.getPasswordHash(),
                List.of(new SimpleGrantedAuthority("LOGIN")));
        this.id = account.getId();
        this.role = account.getRole();
        this.mustChangePassword = account.isMustChangePassword();
    }

    public UUID id() {
        return id;
    }

    public UserRole role() {
        return role;
    }

    public boolean mustChangePassword() {
        return mustChangePassword;
    }
}
