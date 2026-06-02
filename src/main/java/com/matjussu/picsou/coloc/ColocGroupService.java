package com.matjussu.picsou.coloc;

import com.matjussu.picsou.coloc.dto.AddMemberRequest;
import com.matjussu.picsou.coloc.dto.CreateGroupRequest;
import com.matjussu.picsou.coloc.dto.GroupDetailResponse;
import com.matjussu.picsou.coloc.dto.GroupResponse;
import com.matjussu.picsou.coloc.dto.MemberResponse;
import com.matjussu.picsou.user.User;
import com.matjussu.picsou.user.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ColocGroupService {

  private final ColocGroupRepository groups;
  private final ColocMemberRepository members;
  private final UserRepository users;

  /** Crée le groupe et inscrit le créateur comme membre {@code admin}. */
  @Transactional
  public GroupResponse createGroup(UUID userId, CreateGroupRequest req) {
    ColocGroup group =
        groups.save(ColocGroup.builder().name(req.name()).createdByUserId(userId).build());
    members.save(
        ColocMember.builder()
            .colocGroupId(group.getId())
            .userId(userId)
            .role(ColocRole.admin)
            .build());
    return new GroupResponse(group.getId(), group.getName(), 1, ColocRole.admin);
  }

  /** Les groupes dont le user courant est membre (avec son rôle et le nombre de membres). */
  public List<GroupResponse> listMyGroups(UUID userId) {
    return members.findByUserId(userId).stream()
        .map(
            m -> {
              ColocGroup g = groups.findById(m.getColocGroupId()).orElseThrow();
              int count = members.findByColocGroupId(g.getId()).size();
              return new GroupResponse(g.getId(), g.getName(), count, m.getRole());
            })
        .toList();
  }

  /**
   * Détail (membres + rôles). 404 si le user courant n'est pas membre (on ne fuit pas l'existence).
   */
  public GroupDetailResponse getDetail(UUID userId, UUID groupId) {
    requireMember(groupId, userId);
    ColocGroup group = groups.findById(groupId).orElseThrow(this::notFound);
    List<ColocMember> groupMembers = members.findByColocGroupId(groupId);
    Map<UUID, User> usersById =
        users.findAllById(groupMembers.stream().map(ColocMember::getUserId).toList()).stream()
            .collect(Collectors.toMap(User::getId, Function.identity()));
    List<MemberResponse> memberDtos =
        groupMembers.stream()
            .map(
                m -> {
                  User u = usersById.get(m.getUserId());
                  return new MemberResponse(
                      m.getUserId(),
                      u != null ? u.getEmail() : null,
                      u != null ? u.getFirstName() : null,
                      m.getRole());
                })
            .toList();
    return new GroupDetailResponse(group.getId(), group.getName(), memberDtos);
  }

  /** Ajoute un user existant (par email) au groupe. Admin only ; garde-fou anti-doublon (409). */
  @Transactional
  public MemberResponse addMember(UUID userId, UUID groupId, AddMemberRequest req) {
    requireAdmin(groupId, userId);
    User target =
        users
            .findByEmail(req.email())
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur inconnu"));
    if (members.existsByColocGroupIdAndUserId(groupId, target.getId())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Déjà membre du groupe");
    }
    members.save(
        ColocMember.builder()
            .colocGroupId(groupId)
            .userId(target.getId())
            .role(ColocRole.member)
            .build());
    return new MemberResponse(
        target.getId(), target.getEmail(), target.getFirstName(), ColocRole.member);
  }

  // ── Garde-fous d'autorisation ──

  private ColocMember requireMember(UUID groupId, UUID userId) {
    return members.findByColocGroupIdAndUserId(groupId, userId).orElseThrow(this::notFound);
  }

  private void requireAdmin(UUID groupId, UUID userId) {
    ColocMember me = requireMember(groupId, userId);
    if (me.getRole() != ColocRole.admin) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Action réservée à l'admin du groupe");
    }
  }

  private ResponseStatusException notFound() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Groupe inconnu");
  }
}
