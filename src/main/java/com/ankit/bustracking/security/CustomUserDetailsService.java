package com.ankit.bustracking.security;

import com.ankit.bustracking.model.Admin;
import com.ankit.bustracking.model.Driver;
import com.ankit.bustracking.repository.AdminRepository;
import com.ankit.bustracking.repository.DriverRepository;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final AdminRepository adminRepository;
    private final DriverRepository driverRepository;
    private final LoginAttemptService loginAttemptService;

    public CustomUserDetailsService(
            AdminRepository adminRepository,
            DriverRepository driverRepository,
            LoginAttemptService loginAttemptService) {

        this.adminRepository = adminRepository;
        this.driverRepository = driverRepository;
        this.loginAttemptService = loginAttemptService;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new UsernameNotFoundException("Invalid username or password");
        }

        String trimmed = username.trim();

        if (loginAttemptService.isBlocked(trimmed, null)) {
            throw new LockedException("Too many failed login attempts. Try again later.");
        }

        // Admin ID: exactly 5 digits
        if (trimmed.matches("\\d{5}")) {
            Optional<Admin> admin = adminRepository.findByAdminId(trimmed);
            if (admin.isEmpty()) {
                throw new UsernameNotFoundException("Invalid username or password");
            }
            return new CustomAdminDetails(admin.get());
        }

        // Driver ID: email (case-insensitive fallback)
        Optional<Driver> driver = driverRepository.getByDriverId(trimmed);
        if (driver.isEmpty()) {
            driver = driverRepository.findByDriverIdIgnoreCase(trimmed);
        }
        if (driver.isEmpty()) {
            throw new UsernameNotFoundException("Invalid username or password");
        }
        return new CustomUserDetails(driver.get());
    }
}