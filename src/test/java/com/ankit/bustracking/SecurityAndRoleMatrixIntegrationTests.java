package com.ankit.bustracking;

import com.ankit.bustracking.config.SecurityConfig;
import com.ankit.bustracking.config.WebSocketConfig;
import com.ankit.bustracking.controller.AdminController;
import com.ankit.bustracking.controller.StopController;
import com.ankit.bustracking.dto.StopRequestDTO;
import com.ankit.bustracking.dto.StopResponseDTO;
import com.ankit.bustracking.enums.AccountStatus;
import com.ankit.bustracking.enums.TripStatus;
import com.ankit.bustracking.model.Admin;
import com.ankit.bustracking.model.Driver;
import com.ankit.bustracking.model.Trip;
import com.ankit.bustracking.repository.AdminRepository;
import com.ankit.bustracking.repository.DriverRepository;
import com.ankit.bustracking.repository.TripRepository;
import com.ankit.bustracking.security.CustomUserDetailsService;
import com.ankit.bustracking.security.LoginAttemptService;
import com.ankit.bustracking.service.AdminService;
import com.ankit.bustracking.service.StopService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SecurityAndRoleMatrixIntegrationTests {

    @Mock
    private AdminRepository adminRepository;

    @Mock
    private DriverRepository driverRepository;

    @Mock
    private StopService stopService;

    @Mock
    private AdminService adminService;

    @Mock
    private TripRepository tripRepository;

    @Test
    void adminAndDriverLoginAreAssignedCorrectRoles() {

        Admin admin = new Admin();
        admin.setAdminId("10001");
        admin.setPasswordHash("admin-hash");

        when(adminRepository.findByAdminId("10001"))
                .thenReturn(Optional.of(admin));

        Driver driver = new Driver();
        driver.setDriverId("driver@smartbus.in");
        driver.setPinHash("driver-pin-hash");
        driver.setAccountStatus(AccountStatus.ACTIVE);

        when(driverRepository.getByDriverId("driver@smartbus.in"))
                .thenReturn(Optional.of(driver));

        CustomUserDetailsService userDetailsService =
                new CustomUserDetailsService(
                        adminRepository,
                        driverRepository,
                        new LoginAttemptService()
                );

        UserDetails adminDetails =
                userDetailsService.loadUserByUsername("10001");

        UserDetails driverDetails =
                userDetailsService.loadUserByUsername("driver@smartbus.in");

        assertEquals(
                "ROLE_ADMIN",
                adminDetails.getAuthorities().iterator().next().getAuthority()
        );

        assertEquals(
                "ROLE_DRIVER",
                driverDetails.getAuthorities().iterator().next().getAuthority()
        );

        assertTrue(driverDetails.isEnabled());
    }

    @Test
    void loginHandlersRedirectUsersAndLockAfterFiveFailedAttempts() throws Exception {

        LoginAttemptService attempts = new LoginAttemptService();

        SecurityConfig securityConfig =
                new SecurityConfig(attempts, adminRepository);

        AuthenticationSuccessHandler successHandler =
                securityConfig.authenticationSuccessHandler();

        AuthenticationFailureHandler failureHandler =
                securityConfig.authenticationFailureHandler();

        MockHttpServletRequest adminRequest =
                new MockHttpServletRequest();

        MockHttpServletResponse adminResponse =
                new MockHttpServletResponse();

        UsernamePasswordAuthenticationToken adminAuth =
                new UsernamePasswordAuthenticationToken(
                        "10001",
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
                );

        successHandler.onAuthenticationSuccess(
                adminRequest,
                adminResponse,
                adminAuth
        );

        assertEquals(
                "/admin/dashboard.html",
                adminResponse.getRedirectedUrl()
        );

        MockHttpServletRequest driverRequest =
                new MockHttpServletRequest();

        MockHttpServletResponse driverResponse =
                new MockHttpServletResponse();

        UsernamePasswordAuthenticationToken driverAuth =
                new UsernamePasswordAuthenticationToken(
                        "driver@smartbus.in",
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_DRIVER"))
                );

        successHandler.onAuthenticationSuccess(
                driverRequest,
                driverResponse,
                driverAuth
        );

        assertEquals(
                "/driver/dashboard.html",
                driverResponse.getRedirectedUrl()
        );

        MockHttpServletRequest disabledRequest =
                new MockHttpServletRequest();

        disabledRequest.setParameter(
                "username",
                "inactive@smartbus.in"
        );

        MockHttpServletResponse disabledResponse =
                new MockHttpServletResponse();

        failureHandler.onAuthenticationFailure(
                disabledRequest,
                disabledResponse,
                new DisabledException("Account disabled")
        );

        assertEquals(
                "/login.html?disabled",
                disabledResponse.getRedirectedUrl()
        );

        String lastRedirect = null;

        for (int i = 0; i < LoginAttemptService.MAX_FAILED_ATTEMPTS; i++) {

            MockHttpServletRequest request =
                    new MockHttpServletRequest();

            request.setParameter("username", "10001");
            request.setRemoteAddr("127.0.0.1");

            MockHttpServletResponse response =
                    new MockHttpServletResponse();

            failureHandler.onAuthenticationFailure(
                    request,
                    response,
                    new BadCredentialsException("Wrong password")
            );

            lastRedirect = response.getRedirectedUrl();
        }

        assertEquals("/login.html?locked", lastRedirect);
    }

    @Test
    void firstLoginPasswordChangeBlocksAdminActionsUntilPasswordIsChanged() throws Exception {

        Admin admin = new Admin();
        admin.setAdminId("10001");
        admin.setMustChangePassword(true);

        when(adminRepository.findByAdminId("10001"))
                .thenReturn(Optional.of(admin));

        SecurityConfig securityConfig =
                new SecurityConfig(
                        new LoginAttemptService(),
                        adminRepository
                );

        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new StopController(stopService),
                        new AdminController(adminService)
                )
                .addFilters(
                        securityConfig.firstLoginPasswordChangeEnforcementFilter()
                )
                .build();

        UsernamePasswordAuthenticationToken adminPrincipal =
                new UsernamePasswordAuthenticationToken(
                        "10001",
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
                );

        String stopJson =
                "{\"stopName\":\"Clock Tower\",\"latitude\":24.49,\"longitude\":86.69}";

        mockMvc.perform(
                        post("/api/stops")
                                .principal(adminPrincipal)
                                .contentType("application/json")
                                .content(stopJson)
                )
                .andExpect(status().isForbidden());

        mockMvc.perform(
                        put("/admin/register")
                                .principal(adminPrincipal)
                                .contentType("application/json")
                                .content(
                                        "{\"adminId\":\"10001\",\"oldPassword\":\"OldPass123\",\"password\":\"NewPass456\"}"
                                )
                )
                .andExpect(status().isOk());

        admin.setMustChangePassword(false);

        when(stopService.createStop(any(StopRequestDTO.class)))
                .thenReturn(
                        new StopResponseDTO(
                                2L,
                                "Clock Tower",
                                24.49,
                                86.69
                        )
                );

        mockMvc.perform(
                        post("/api/stops")
                                .principal(adminPrincipal)
                                .contentType("application/json")
                                .content(stopJson)
                )
                .andExpect(status().isOk());

        verify(adminService).updateAdminPassword(any());
        verify(stopService).createStop(any(StopRequestDTO.class));
    }

    @Test
    void websocketAllowsTrackingForRunningTripsAndRejectsCompletedTrips() {

        WebSocketConfig webSocketConfig =
                new WebSocketConfig(tripRepository);

        var interceptor =
                webSocketConfig.createTopicSubscriptionInterceptor();

        MessageChannel channel = mock(MessageChannel.class);

        Trip runningTrip = new Trip();
        runningTrip.setTripId("running-trip");
        runningTrip.setStatus(TripStatus.RUNNING);

        when(tripRepository.findByTripId("running-trip"))
                .thenReturn(Optional.of(runningTrip));

        Message<byte[]> runningSubscription =
                createSubscription("/topic/trips/running-trip");

        assertNotNull(
                interceptor.preSend(runningSubscription, channel)
        );

        Trip completedTrip = new Trip();
        completedTrip.setTripId("completed-trip");
        completedTrip.setStatus(TripStatus.COMPLETED);

        when(tripRepository.findByTripId("completed-trip"))
                .thenReturn(Optional.of(completedTrip));

        Message<byte[]> completedSubscription =
                createSubscription("/topic/trips/completed-trip");

        assertThrows(
                AccessDeniedException.class,
                () -> interceptor.preSend(completedSubscription, channel)
        );
    }

    private Message<byte[]> createSubscription(String destination) {

        StompHeaderAccessor headers =
                StompHeaderAccessor.create(StompCommand.SUBSCRIBE);

        headers.setDestination(destination);

        return MessageBuilder.createMessage(
                new byte[0],
                headers.getMessageHeaders()
        );
    }
}
