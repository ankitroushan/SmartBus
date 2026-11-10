package com.ankit.bustracking.controller;

import com.ankit.bustracking.model.Admin;
import com.ankit.bustracking.repository.AdminRepository;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/me")
public class AuthController {

    private final AdminRepository adminRepository;

    public AuthController(AdminRepository adminRepository) {
        this.adminRepository = adminRepository;
    }

    @GetMapping
    public Map<String, Object> getCurrentUser(Authentication authentication, CsrfToken csrfToken) {
        if (csrfToken != null) {
            csrfToken.getToken();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            result.put("authenticated", false);
            result.put("username", null);
            result.put("role", null);
            result.put("mustChangePassword", false);
            return result;
        }

        String role = null;
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            role = authority.getAuthority();
            break;
        }

        boolean mustChangePassword = false;
        if ("ROLE_ADMIN".equals(role)) {
            Optional<Admin> admin = adminRepository.findByAdminId(authentication.getName());
            if (admin.isPresent() && Boolean.TRUE.equals(admin.get().getMustChangePassword())) {
                mustChangePassword = true;
            }
        }

        result.put("authenticated", true);
        result.put("username", authentication.getName());
        result.put("role", role);
        result.put("mustChangePassword", mustChangePassword);
        return result;
    }
}
