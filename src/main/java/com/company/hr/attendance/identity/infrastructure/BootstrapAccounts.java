package com.company.hr.attendance.identity.infrastructure;

import com.company.hr.attendance.identity.domain.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "app.bootstrap.enabled", havingValue = "true")
public class BootstrapAccounts implements ApplicationRunner {
    private final AccountRepository accounts;
    private final PasswordEncoder passwords;
    private final Environment environment;

    public BootstrapAccounts(AccountRepository accounts, PasswordEncoder passwords, Environment environment) {
        this.accounts = accounts;
        this.passwords = passwords;
        this.environment = environment;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (var role : Role.values()) {
            var prefix = "app.bootstrap." + role.name().toLowerCase(Locale.ROOT);
            var email = environment.getProperty(prefix + "-email", "").strip().toLowerCase(Locale.ROOT);
            var password = environment.getProperty(prefix + "-password", "");
            if (email.isEmpty() && password.isEmpty()) continue;
            if (!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+") || email.length() > 254
                || password.length() < 12 || password.getBytes(StandardCharsets.UTF_8).length > 72) {
                throw new IllegalArgumentException("Bootstrap " + role + " requires a valid email and password of 12 characters to 72 UTF-8 bytes");
            }
            if (accounts.findByEmail(email).isEmpty()) {
                accounts.save(new Account(email, passwords.encode(password), switch (role) {
                    case ADMIN -> "Quản trị";
                    case MANAGER -> "Quản lý";
                    case EMPLOYEE -> "Nhân viên";
                }, role));
            }
        }
    }
}
