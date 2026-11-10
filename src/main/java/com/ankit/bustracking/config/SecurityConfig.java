package com.ankit.bustracking.config;

import com.ankit.bustracking.model.Admin;
import com.ankit.bustracking.repository.AdminRepository;
import com.ankit.bustracking.security.LoginAttemptService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;

@Configuration
public class SecurityConfig {

    private final LoginAttemptService loginAttemptService;
    private final AdminRepository adminRepository;

    public SecurityConfig(LoginAttemptService loginAttemptService, AdminRepository adminRepository) {
        this.loginAttemptService = loginAttemptService;
        this.adminRepository = adminRepository;
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationSuccessHandler authenticationSuccessHandler() {
        return (request, response, authentication) -> {
            loginAttemptService.recordSuccess(authentication.getName(), getClientIp(request));
            for (GrantedAuthority authority : authentication.getAuthorities()) {
                if ("ROLE_ADMIN".equals(authority.getAuthority())) {
                    response.sendRedirect("/admin/dashboard.html");
                    return;
                }
                if ("ROLE_DRIVER".equals(authority.getAuthority())) {
                    response.sendRedirect("/driver/dashboard.html");
                    return;
                }
            }
            response.sendRedirect("/index.html");
        };
    }

    @Bean
    public AuthenticationFailureHandler authenticationFailureHandler() {
        return (request, response, exception) -> {
            String username = request.getParameter("username");
            String clientIp = getClientIp(request);

            if (exception instanceof LockedException || loginAttemptService.isBlocked(username, clientIp)) {
                response.sendRedirect("/login.html?locked");
                return;
            }

            loginAttemptService.recordFailure(username, clientIp);

            if (loginAttemptService.isBlocked(username, clientIp)) {
                response.sendRedirect("/login.html?locked");
                return;
            }

            if (exception instanceof DisabledException) {
                response.sendRedirect("/login.html?disabled");
                return;
            }

            response.sendRedirect("/login.html?error");
        };
    }

    /**
     * Enforces first-login password change on the backend:
     * When an authenticated ROLE_ADMIN has mustChangePassword=true, blocks all administrative
     * API calls except GET /api/me and PUT /admin/register until the password is changed.
     */
    public OncePerRequestFilter firstLoginPasswordChangeEnforcementFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(
                    HttpServletRequest request,
                    HttpServletResponse response,
                    FilterChain filterChain) throws ServletException, IOException {

                String uri = request.getRequestURI();
                String method = request.getMethod();

                boolean isProtectedAdminApi = uri != null
                        && (uri.startsWith("/api/") || uri.startsWith("/admin/register"))
                        && !("GET".equalsIgnoreCase(method) && "/api/me".equals(uri))
                        && !("PUT".equalsIgnoreCase(method) && "/admin/register".equals(uri));

                if (isProtectedAdminApi) {
                    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                    if (auth == null && request.getUserPrincipal() instanceof Authentication principalAuth) {
                        auth = principalAuth;
                    }
                    if (auth != null
                            && auth.isAuthenticated()
                            && !(auth instanceof AnonymousAuthenticationToken)
                            && hasAdminRole(auth)) {
                        Optional<Admin> admin = adminRepository.findByAdminId(auth.getName());
                        if (admin.isPresent() && Boolean.TRUE.equals(admin.get().getMustChangePassword())) {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType("application/json");
                            response.getWriter().write(String.format(
                                    "{\"timestamp\":\"%s\",\"status\":403,\"error\":\"Forbidden\",\"message\":\"Password change required on first login before performing administrative actions\",\"path\":\"%s\"}",
                                    Instant.now().toString(),
                                    uri
                            ));
                            return;
                        }
                    }
                }

                filterChain.doFilter(request, response);
            }
        };
    }

    private boolean hasAdminRole(Authentication auth) {
        for (GrantedAuthority authority : auth.getAuthorities()) {
            if ("ROLE_ADMIN".equals(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity) throws Exception {
        CsrfTokenRequestAttributeHandler csrfRequestHandler = new CsrfTokenRequestAttributeHandler();
        csrfRequestHandler.setCsrfRequestAttributeName("_csrf");

        httpSecurity
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(csrfRequestHandler)
                        .ignoringRequestMatchers("/ws/**")
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/",
                                "/index.html",
                                "/login.html",
                                "/login",
                                "/ws/**",
                                "/actuator/health",
                                "/favicon.ico",
                                "/css/**",
                                "/js/**",
                                "/assets/**"
                        ).permitAll()

                        .requestMatchers(HttpMethod.GET, "/api/me").permitAll()

                        .requestMatchers("/admin/register").hasRole("ADMIN")
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/driver/**").hasRole("DRIVER")

                        .requestMatchers("/api/drivers/**").hasRole("ADMIN")

                        .requestMatchers(HttpMethod.GET, "/api/buses/**").permitAll()
                        .requestMatchers("/api/buses/**").hasRole("ADMIN")

                        .requestMatchers(HttpMethod.GET, "/api/route/**").permitAll()
                        .requestMatchers("/api/route/**").hasRole("ADMIN")

                        .requestMatchers(HttpMethod.GET, "/api/stops/admin").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/stops/**").permitAll()
                        .requestMatchers("/api/stops/**").hasRole("ADMIN")

                        .requestMatchers(HttpMethod.GET, "/api/trips/search").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/trips/*/location").permitAll()

                        .requestMatchers(HttpMethod.POST, "/api/trips/location").hasRole("DRIVER")
                        .requestMatchers(HttpMethod.GET, "/api/trips/my-trips").hasRole("DRIVER")
                        .requestMatchers(HttpMethod.PUT, "/api/trips/*/start").hasRole("DRIVER")
                        .requestMatchers(HttpMethod.PUT, "/api/trips/*/complete").hasRole("DRIVER")

                        .requestMatchers(HttpMethod.PUT, "/api/trips/*/cancel").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/trips/*/force-complete").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/trips/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/trips/**").hasRole("ADMIN")

                        .anyRequest().authenticated()
                )
                .addFilterAfter(firstLoginPasswordChangeEnforcementFilter(), AuthorizationFilter.class)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            String uri = request.getRequestURI();
                            if (uri != null && (uri.startsWith("/api/") || uri.startsWith("/admin/register"))) {
                                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                                response.setContentType("application/json");
                                response.getWriter().write(String.format(
                                        "{\"timestamp\":\"%s\",\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Authentication required\",\"path\":\"%s\"}",
                                        Instant.now().toString(),
                                        uri
                                ));
                            } else {
                                response.sendRedirect("/login.html");
                            }
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            String uri = request.getRequestURI();
                            if (uri != null && (uri.startsWith("/api/") || uri.startsWith("/admin/register"))) {
                                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                                response.setContentType("application/json");
                                response.getWriter().write(String.format(
                                        "{\"timestamp\":\"%s\",\"status\":403,\"error\":\"Forbidden\",\"message\":\"Access denied. Insufficient role permissions or invalid CSRF token.\",\"path\":\"%s\"}",
                                        Instant.now().toString(),
                                        uri
                                ));
                            } else {
                                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                                if (auth != null && auth.isAuthenticated()) {
                                    for (GrantedAuthority authority : auth.getAuthorities()) {
                                        if ("ROLE_ADMIN".equals(authority.getAuthority())) {
                                            response.sendRedirect("/admin/dashboard.html");
                                            return;
                                        }
                                        if ("ROLE_DRIVER".equals(authority.getAuthority())) {
                                            response.sendRedirect("/driver/dashboard.html");
                                            return;
                                        }
                                    }
                                }
                                response.sendRedirect("/login.html?denied");
                            }
                        })
                )
                .formLogin(form -> form
                        .loginPage("/login.html")
                        .loginProcessingUrl("/login")
                        .successHandler(authenticationSuccessHandler())
                        .failureHandler(authenticationFailureHandler())
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login.html?logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID", "XSRF-TOKEN")
                        .permitAll()
                )
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                );

        return httpSecurity.build();
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
