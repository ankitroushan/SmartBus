package com.ankit.bustracking.service;

import com.ankit.bustracking.dto.AdminRequestDTO;
import com.ankit.bustracking.model.Admin;
import com.ankit.bustracking.repository.AdminRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);
    private static final String PASSWORD_POLICY_REGEX = "^(?=.*[A-Za-z])(?=.*\\d).{8,}$";

    private final AdminRepository adminRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    public AdminService(
            AdminRepository adminRepository,
            BCryptPasswordEncoder passwordEncoder) {

        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
    }

    private void validatePasswordPolicy(String rawPassword) {
        if (rawPassword == null || !rawPassword.matches(PASSWORD_POLICY_REGEX)) {
            throw new IllegalArgumentException(
                    "Password must be at least 8 characters long and contain both letters and digits");
        }
    }

    @Transactional
    public void createAdmin(AdminRequestDTO adminRequestDTO) {
        String adminId = adminRequestDTO.getAdminId() != null ? adminRequestDTO.getAdminId().trim() : "";
        String rawPassword = adminRequestDTO.getPassword();

        validatePasswordPolicy(rawPassword);

        if (adminRepository.findByAdminId(adminId).isPresent()) {
            throw new IllegalStateException("Admin already exists");
        }

        Admin admin = new Admin();
        admin.setAdminId(adminId);
        admin.setPasswordHash(passwordEncoder.encode(rawPassword));
        admin.setMustChangePassword(false);

        adminRepository.save(admin);
        log.info("Admin account created for adminId={}", adminId);
    }

    @Transactional
    public void updateAdminPassword(AdminRequestDTO adminRequestDTO) {
        String adminId = adminRequestDTO.getAdminId() != null ? adminRequestDTO.getAdminId().trim() : "";
        Optional<Admin> admin = adminRepository.findByAdminId(adminId);

        if (admin.isEmpty()) {
            throw new IllegalStateException("Admin not found");
        }

        String oldPassword = adminRequestDTO.getOldPassword();
        if (oldPassword == null || oldPassword.isBlank()) {
            throw new IllegalArgumentException("Current password (oldPassword) is required to change password");
        }

        if (!passwordEncoder.matches(oldPassword, admin.get().getPasswordHash())) {
            log.warn("Rejected password change for adminId={}: current password mismatch", adminId);
            throw new IllegalArgumentException("Current password is incorrect");
        }

        String newRawPassword = adminRequestDTO.getPassword();
        validatePasswordPolicy(newRawPassword);

        if (oldPassword.equals(newRawPassword)
                || passwordEncoder.matches(newRawPassword, admin.get().getPasswordHash())) {
            throw new IllegalArgumentException("New password must be different from the current password");
        }

        admin.get().setPasswordHash(passwordEncoder.encode(newRawPassword));
        admin.get().setMustChangePassword(false);
        adminRepository.save(admin.get());
        log.info("Admin password updated for adminId={}", adminId);
    }
}