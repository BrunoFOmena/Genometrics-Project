package com.ngs.analytics.auth;

import com.ngs.analytics.common.NgsProperties;
import com.ngs.analytics.domain.Role;
import com.ngs.analytics.domain.UserAccount;
import com.ngs.analytics.domain.UserAccountRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "ngs.auth", name = "disabled", havingValue = "true")
// Runs before the web server accepts requests, so a first call right after /actuator/health is UP never gets a 401.
public class DevAuthBootstrap implements SmartInitializingSingleton {

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

    @Override
    public void afterSingletonsInstantiated() {
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
