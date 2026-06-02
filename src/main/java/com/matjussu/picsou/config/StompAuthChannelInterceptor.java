package com.matjussu.picsou.config;

import com.matjussu.picsou.auth.jwt.JwtTokenProvider;
import com.matjussu.picsou.coloc.ColocMemberRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Sécurité du canal STOMP, hors {@code SecurityFilterChain} HTTP (le canal messaging est séparé).
 *
 * <ul>
 *   <li><b>CONNECT</b> : valide le JWT d'accès lu dans les {@code connectHeaders} STOMP (le
 *       navigateur ne peut pas poser de header sur le handshake WS) et pose le Principal.
 *   <li><b>SUBSCRIBE</b> {@code /topic/coloc/{groupId}} : refuse si le user n'est pas membre du
 *       groupe.
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

  private static final String TOPIC_PREFIX = "/topic/coloc/";

  private final JwtTokenProvider tokenProvider;
  private final ColocMemberRepository members;

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel channel) {
    StompHeaderAccessor accessor =
        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
    if (accessor == null) {
      return message;
    }
    StompCommand command = accessor.getCommand();
    if (StompCommand.CONNECT.equals(command)) {
      authenticate(accessor);
    } else if (StompCommand.SUBSCRIBE.equals(command)) {
      authorizeSubscription(accessor);
    }
    return message;
  }

  private void authenticate(StompHeaderAccessor accessor) {
    String token = stripBearer(accessor.getFirstNativeHeader("Authorization"));
    if (token == null || !tokenProvider.isValidAccessToken(token)) {
      throw new MessagingException("STOMP CONNECT refusé : token d'accès invalide ou absent");
    }
    UUID userId = tokenProvider.extractUserId(token);
    accessor.setUser(new UsernamePasswordAuthenticationToken(userId, null, List.of()));
  }

  private void authorizeSubscription(StompHeaderAccessor accessor) {
    // Whitelist stricte : la seule destination cliente légitime est /topic/coloc/{uuid} d'un groupe
    // dont on est membre. Tout le reste est rejeté — sinon un abonnement wildcard (/topic/coloc/*,
    // /topic/**) matcherait, via l'AntPathMatcher du SimpleBroker, les events de TOUS les groupes
    // (fuite cross-coloc). extractGroupId renvoie null pour toute destination non strictement
    // /topic/coloc/<uuid valide>.
    UUID groupId = extractGroupId(accessor.getDestination());
    UUID userId = currentUserId(accessor);
    if (groupId == null
        || userId == null
        || !members.existsByColocGroupIdAndUserId(groupId, userId)) {
      throw new MessagingException(
          "SUBSCRIBE refusé : destination non autorisée " + accessor.getDestination());
    }
  }

  private UUID currentUserId(StompHeaderAccessor accessor) {
    if (accessor.getUser() instanceof UsernamePasswordAuthenticationToken auth
        && auth.getPrincipal() instanceof UUID userId) {
      return userId;
    }
    return null;
  }

  private UUID extractGroupId(String destination) {
    if (destination == null || !destination.startsWith(TOPIC_PREFIX)) {
      return null;
    }
    try {
      return UUID.fromString(destination.substring(TOPIC_PREFIX.length()));
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private String stripBearer(String header) {
    if (header == null) {
      return null;
    }
    return header.startsWith("Bearer ") ? header.substring(7) : header;
  }
}
