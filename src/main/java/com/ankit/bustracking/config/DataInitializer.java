package com.ankit.bustracking.config;

import com.ankit.bustracking.model.Admin;
import com.ankit.bustracking.repository.AdminRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@Configuration
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Value("${app.initial-admin.id:10001}")
    private String initialAdminId;

    @Value("${app.initial-admin.password:}")
    private String initialAdminPassword;

    @Bean
    public CommandLineRunner initDefaultAdmin(
            AdminRepository adminRepository,
            BCryptPasswordEncoder passwordEncoder) {

        return args -> {
            if (adminRepository.count() > 0) {
                return;
            }

            if (initialAdminPassword == null || initialAdminPassword.trim().isEmpty()) {
                log.warn("No Admin account exists in database and APP_INITIAL_ADMIN_PASSWORD is not configured. "
                        + "Set APP_INITIAL_ADMIN_PASSWORD in the environment to provision initial Admin ID '{}'.",
                        initialAdminId != null ? initialAdminId.trim() : "10001");
                return;
            }

            String adminId = (initialAdminId != null && !initialAdminId.trim().isEmpty())
                    ? initialAdminId.trim()
                    : "10001";

            Admin defaultAdmin = new Admin();
            defaultAdmin.setAdminId(adminId);
            defaultAdmin.setPasswordHash(passwordEncoder.encode(initialAdminPassword.trim()));
            defaultAdmin.setMustChangePassword(true);
            adminRepository.save(defaultAdmin);

            log.info("Provisioned initial Admin account '{}' from environment configuration. Password change is required on first login.",
                    defaultAdmin.getAdminId());
        };
    }
}
