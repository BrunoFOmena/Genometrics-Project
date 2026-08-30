package com.ngs.analytics.auth;

import com.ngs.analytics.common.NgsProperties;
import com.ngs.analytics.domain.Role;
import com.ngs.analytics.domain.UserAccount;
import com.ngs.analytics.domain.UserAccountRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "ngs.auth", name = "disabled", havingValue = "true")
public class DevAuthBootstrap {

    private final NgsProperties properties;
    private final UserAccountRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DevAuthBootstrap(
            NgsProperties properties,
            UserAccountRepository userRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureDevUser() {
        String email = properties.getAuth().getDevEmail();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            return;
        }
        UserAccount user = new UserAccount();
        user.setEmail(email);
        user.setDisplayName("Dev User");
        user.setPasswordHash(passwordEncoder.encode("dev"));
        user.setRole(Role.USER);
        userRepository.save(user);
    }
}
