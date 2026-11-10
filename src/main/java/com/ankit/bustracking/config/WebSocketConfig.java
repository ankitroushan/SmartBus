package com.ankit.bustracking.config;

import com.ankit.bustracking.enums.TripStatus;
import com.ankit.bustracking.model.Trip;
import com.ankit.bustracking.repository.TripRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final String TRIP_TOPIC_PREFIX = "/topic/trips/";

    private final TripRepository tripRepository;

    @Value("${app.websocket.allowed-origins:http://localhost:8080,http://127.0.0.1:8080,https://debunk-spindle-agile.ngrok-free.dev}")
    private String allowedOrigins = "http://localhost:8080,http://127.0.0.1:8080,https://debunk-spindle-agile.ngrok-free.dev";

    public WebSocketConfig(TripRepository tripRepository) {
        this.tripRepository = tripRepository;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        String[] rawOrigins = (allowedOrigins != null ? allowedOrigins : "").split(",");
        List<String> validOrigins = new ArrayList<>();
        for (String origin : rawOrigins) {
            String trimmed = origin.trim();
            if (trimmed.isEmpty() || "*".equals(trimmed)) {
                throw new IllegalStateException("Wildcard '*' or empty WebSocket origin is not permitted");
            }
            validOrigins.add(trimmed);
        }
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(validOrigins.toArray(new String[0]));
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(createTopicSubscriptionInterceptor());
    }

    /**
     * Validates STOMP SUBSCRIBE and SEND frames:
     * - Allows TO SUBSCRIBE only for /topic/trips/{tripId} where the trip exists and is SCHEDULED, RUNNING, or OVERDUE.
     * - Rejects COMPLETED, CANCELLED, or unknown trips, and blocks all direct client SEND frames.
     */
    public ChannelInterceptor createTopicSubscriptionInterceptor() {
        return new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor =
                        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null) {
                    return message;
                }

                StompCommand command = accessor.getCommand();
                String destination = accessor.getDestination();

                if (StompCommand.SUBSCRIBE.equals(command)) {
                    if (destination == null || !destination.startsWith(TRIP_TOPIC_PREFIX)) {
                        throw new AccessDeniedException("Subscription is only permitted for /topic/trips/{tripId}");
                    }
                    String tripId = destination.substring(TRIP_TOPIC_PREFIX.length()).trim();
                    if (tripId.isEmpty() || tripId.contains("/")) {
                        throw new AccessDeniedException("Invalid trip topic destination");
                    }
                    Optional<Trip> trip = tripRepository.findByTripId(tripId);
                    if (trip.isEmpty()) {
                        throw new AccessDeniedException("Trip is not available for public tracking");
                    }
                    TripStatus status = trip.get().getStatus();
                    if (status == TripStatus.CANCELLED || status == TripStatus.COMPLETED) {
                        throw new AccessDeniedException("Completed or cancelled trip is not available for live tracking");
                    }
                }

                if (StompCommand.SEND.equals(command)) {
                    throw new AccessDeniedException("Direct client publishing to broker topics is not allowed");
                }

                return message;
            }
        };
    }
}
