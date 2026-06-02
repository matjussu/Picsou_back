package com.matjussu.picsou.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket/STOMP temps réel pour la coloc. Canal <b>server-push only</b> : les clients ne SEND
 * pas, le serveur diffuse sur {@code /topic/coloc/{groupId}} après commit. Sécurité dans {@link
 * StompAuthChannelInterceptor}.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

  private final StompAuthChannelInterceptor authInterceptor;

  @Value("${app.cors.origins}")
  private String corsOrigins;

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    // Pas de SockJS : le front utilise un client STOMP natif (@stomp/stompjs).
    registry.addEndpoint("/ws").setAllowedOriginPatterns(corsOrigins.split(","));
  }

  @Override
  public void configureMessageBroker(MessageBrokerRegistry registry) {
    registry.enableSimpleBroker("/topic");
    registry.setApplicationDestinationPrefixes("/app");
  }

  @Override
  public void configureClientInboundChannel(ChannelRegistration registration) {
    registration.interceptors(authInterceptor);
  }
}
