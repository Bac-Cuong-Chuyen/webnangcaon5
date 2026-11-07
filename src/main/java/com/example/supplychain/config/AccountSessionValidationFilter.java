package com.example.supplychain.config;

import java.io.IOException;
import java.io.Serializable;
import java.util.Objects;
import com.example.supplychain.entity.UserAccount;
import com.example.supplychain.entity.Role;
import com.example.supplychain.repository.UserAccountRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Revalidate persisted login sessions before authorization, including after account deletion. */
public class AccountSessionValidationFilter extends OncePerRequestFilter {
    public static final String SNAPSHOT_ATTRIBUTE = AccountSessionValidationFilter.class.getName();
    private final UserAccountRepository accounts;

    public AccountSessionValidationFilter(UserAccountRepository accounts) {
        this.accounts = accounts;
    }

    public record Snapshot(Long id, long version, String username, String password, Role role) implements Serializable {
        public static Snapshot of(UserAccount account) {
            return new Snapshot(account.getId(), account.getVersion(), account.getUsername(), account.getPassword(), account.getRole());
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        var session = request.getSession(false);
        // Only application logins attach this server-side marker. Anonymous sessions have none.
        if (session != null && session.getAttribute(SNAPSHOT_ATTRIBUTE) instanceof Snapshot snapshot) {
            boolean valid = accounts.findById(snapshot.id()).map(Snapshot::of)
                        .filter(current -> Objects.equals(current, snapshot)).isPresent();
            if (!valid) {
                session.invalidate();
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
