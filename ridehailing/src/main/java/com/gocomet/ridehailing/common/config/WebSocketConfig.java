package com.gocomet.ridehailing.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Clients subscribe to /topic/* for broadcast updates
        config.enableSimpleBroker("/topic");
        // Client messages prefixed with /app go to @MessageMapping methods
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // WebSocket endpoint that clients connect to
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")  // Allow React dev server
                .withSockJS();  // Fallback for browsers without WebSocket
    }
}
