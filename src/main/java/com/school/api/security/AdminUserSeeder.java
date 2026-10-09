package com.school.api.security;

import com.school.api.common.domain.Role;
import com.school.api.common.domain.Status;
import com.school.api.user.UserAccount;
import com.school.api.user.UserAccountRepository;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates the first platform admin while the users table is empty, so a fresh
 * deployment has a way to log in. Later users are created through the API or SQL.
 */
@Component
public class AdminUserSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminUserSeeder.class);

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminProperties adminProperties;

    public AdminUserSeeder(
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder,
            AdminProperties adminProperties) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminProperties = adminProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userAccountRepository.count() > 0) {
            return;
        }
        UserAccount admin = new UserAccount();
        admin.setUsername(adminProperties.username());
        admin.setPassword(passwordEncoder.encode(adminProperties.password()));
        admin.setFirstName("Platform");
        admin.setLastName("Admin");
        admin.setStatus(Status.ACTIVE);
        admin.setRoles(Set.of(Role.PLATFORM_ADMIN));
        userAccountRepository.save(admin);
        if ("admin".equals(adminProperties.password())) {
            log.warn("Seeded platform admin '{}' with the DEFAULT password. "
                    + "Override auth.admin.password before using this outside local development.",
                    adminProperties.username());
        } else {
            log.info("Seeded initial platform admin '{}'", adminProperties.username());
        }
    }
}
