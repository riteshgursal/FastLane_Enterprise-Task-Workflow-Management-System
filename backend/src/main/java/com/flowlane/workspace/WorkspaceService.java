package com.flowlane.workspace;

import com.flowlane.common.config.FlowlaneProperties;
import com.flowlane.common.event.DomainEvents.InvitationCreated;
import com.flowlane.common.exception.ApiException;
import com.flowlane.common.exception.ErrorCode;
import com.flowlane.common.security.AccessGuard;
import com.flowlane.common.security.Tokens;
import com.flowlane.identity.User;
import com.flowlane.identity.UserRepository;
import com.flowlane.project.ProjectRepository;
import com.flowlane.task.TaskRepository;
import com.flowlane.workspace.WorkspaceDtos.InvitationDto;
import com.flowlane.workspace.WorkspaceDtos.InvitationPreview;
import com.flowlane.workspace.WorkspaceDtos.InviteRequest;
import com.flowlane.workspace.WorkspaceDtos.MemberDto;
import com.flowlane.workspace.WorkspaceDtos.WorkspaceDto;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkspaceService {

    private static final Duration INVITE_TTL = Duration.ofDays(7);

    private final WorkspaceRepository workspaces;
    private final WorkspaceMemberRepository members;
    private final WorkspaceInvitationRepository invitations;
    private final UserRepository users;
    private final ProjectRepository projects;
    private final TaskRepository tasks;
    private final AccessGuard guard;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final String frontendOrigin;

    public WorkspaceService(WorkspaceRepository workspaces, WorkspaceMemberRepository members,
                            WorkspaceInvitationRepository invitations, UserRepository users, ProjectRepository projects,
                            TaskRepository tasks, AccessGuard guard, ApplicationEventPublisher events, Clock clock,
                            FlowlaneProperties props) {
        this.workspaces = workspaces;
        this.members = members;
        this.invitations = invitations;
        this.users = users;
        this.projects = projects;
        this.tasks = tasks;
        this.guard = guard;
        this.events = events;
        this.clock = clock;
        this.frontendOrigin = props.frontendOrigin();
    }

    @Transactional(readOnly = true)
    public List<WorkspaceDto> mine(UUID userId) {
        Map<UUID, WorkspaceRole> roles = members.findByUserId(userId).stream()
                .collect(Collectors.toMap(WorkspaceMember::getWorkspaceId, WorkspaceMember::getRole));
        return workspaces.findActiveForUser(userId).stream().map(w -> toDto(w, roles.get(w.getId()))).toList();
    }

    @Transactional
    public WorkspaceDto create(UUID userId, String name) {
        Workspace w = workspaces.save(new Workspace(name.trim(), uniqueSlug(name), userId));
        members.save(new WorkspaceMember(w.getId(), userId, WorkspaceRole.OWNER));
        return toDto(w, WorkspaceRole.OWNER);
    }

    @Transactional
    public WorkspaceDto rename(UUID workspaceId, String name) {
        WorkspaceMember me = guard.requireWorkspaceRole(workspaceId, WorkspaceRole.ADMIN);
        Workspace w = guard.workspace(workspaceId);
        w.setName(name.trim());
        return toDto(w, me.getRole());
    }

    @Transactional
    public void archive(UUID workspaceId) {
        guard.requireWorkspaceRole(workspaceId, WorkspaceRole.OWNER);
        guard.workspace(workspaceId).setArchivedAt(clock.instant());
    }

    @Transactional(readOnly = true)
    public List<MemberDto> members(UUID workspaceId) {
        guard.requireWorkspaceMember(workspaceId);
        List<WorkspaceMember> list = members.findByWorkspaceId(workspaceId);
        Map<UUID, User> byId = users.findAllById(list.stream().map(WorkspaceMember::getUserId).toList()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<UUID, Long> open = tasks.countOpenByAssignee(workspaceId).stream()
                .collect(Collectors.toMap(r -> (UUID) r[0], r -> (Long) r[1]));
        return list.stream()
                .filter(m -> byId.containsKey(m.getUserId()))
                .map(m -> {
                    User u = byId.get(m.getUserId());
                    return new MemberDto(u.getId(), u.getDisplayName(), u.initials(), u.getEmail(), m.getRole(),
                            open.getOrDefault(u.getId(), 0L), m.getCreatedAt());
                })
                .sorted(Comparator.comparing(MemberDto::role).reversed().thenComparing(MemberDto::displayName))
                .toList();
    }

    @Transactional
    public void changeRole(UUID workspaceId, UUID userId, WorkspaceRole role) {
        WorkspaceMember me = guard.requireWorkspaceRole(workspaceId, WorkspaceRole.ADMIN);
        WorkspaceMember target = members.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> ApiException.notFound("Member"));
        boolean touchesOwner = role == WorkspaceRole.OWNER || target.getRole() == WorkspaceRole.OWNER;
        if (touchesOwner && me.getRole() != WorkspaceRole.OWNER) throw ApiException.forbidden();
        if (target.getRole() == WorkspaceRole.OWNER && role != WorkspaceRole.OWNER
                && members.countByWorkspaceIdAndRole(workspaceId, WorkspaceRole.OWNER) <= 1) {
            throw new ApiException(ErrorCode.LAST_OWNER);
        }
        target.setRole(role);
    }

    @Transactional
    public void removeMember(UUID workspaceId, UUID userId) {
        UUID me = com.flowlane.common.security.CurrentUser.id();
        if (!me.equals(userId)) guard.requireWorkspaceRole(workspaceId, WorkspaceRole.ADMIN);
        WorkspaceMember target = members.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> ApiException.notFound("Member"));
        if (target.getRole() == WorkspaceRole.OWNER
                && members.countByWorkspaceIdAndRole(workspaceId, WorkspaceRole.OWNER) <= 1) {
            throw new ApiException(ErrorCode.LAST_OWNER);
        }
        guard.removeFromAllProjects(workspaceId, userId);
        members.delete(target);
    }

    @Transactional
    public InvitationDto invite(UUID workspaceId, InviteRequest req) {
        WorkspaceMember me = guard.requireWorkspaceRole(workspaceId, WorkspaceRole.ADMIN);
        if (req.role() == WorkspaceRole.OWNER) throw ApiException.forbidden();
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        users.findByEmailIgnoreCase(email).ifPresent(u -> {
            if (members.findByWorkspaceIdAndUserId(workspaceId, u.getId()).isPresent()) {
                throw new ApiException(ErrorCode.ALREADY_MEMBER);
            }
        });
        String raw = Tokens.random();
        WorkspaceInvitation inv = new WorkspaceInvitation();
        inv.setWorkspaceId(workspaceId);
        inv.setEmail(email);
        inv.setRole(req.role());
        inv.setTokenHash(Tokens.sha256(raw));
        inv.setInvitedBy(me.getUserId());
        inv.setExpiresAt(clock.instant().plus(INVITE_TTL));
        invitations.save(inv);
        events.publishEvent(new InvitationCreated(email, guard.workspace(workspaceId).getName(),
                frontendOrigin + "/invite/" + raw, me.getUserId()));
        return toDto(inv);
    }

    @Transactional(readOnly = true)
    public List<InvitationDto> pendingInvitations(UUID workspaceId) {
        guard.requireWorkspaceMember(workspaceId);
        Instant now = clock.instant();
        return invitations.findByWorkspaceIdAndAcceptedAtIsNullAndRevokedAtIsNull(workspaceId).stream()
                .filter(i -> i.isPending(now)).map(this::toDto).toList();
    }

    @Transactional
    public InvitationDto resend(UUID workspaceId, UUID invitationId) {
        guard.requireWorkspaceRole(workspaceId, WorkspaceRole.ADMIN);
        WorkspaceInvitation old = invitations.findById(invitationId)
                .filter(i -> i.getWorkspaceId().equals(workspaceId))
                .orElseThrow(() -> ApiException.notFound("Invitation"));
        old.setRevokedAt(clock.instant());
        return invite(workspaceId, new InviteRequest(old.getEmail(), old.getRole()));
    }

    @Transactional
    public void revoke(UUID workspaceId, UUID invitationId) {
        guard.requireWorkspaceRole(workspaceId, WorkspaceRole.ADMIN);
        invitations.findById(invitationId)
                .filter(i -> i.getWorkspaceId().equals(workspaceId))
                .orElseThrow(() -> ApiException.notFound("Invitation"))
                .setRevokedAt(clock.instant());
    }

    @Transactional(readOnly = true)
    public InvitationPreview preview(String rawToken) {
        return invitations.findByTokenHash(Tokens.sha256(rawToken))
                .map(i -> new InvitationPreview(workspaces.findById(i.getWorkspaceId()).map(Workspace::getName).orElse(""),
                        i.getEmail(), i.getRole(), i.isPending(clock.instant())))
                .orElse(new InvitationPreview("", "", WorkspaceRole.MEMBER, false));
    }

    @Transactional
    public WorkspaceDto accept(UUID userId, String rawToken) {
        WorkspaceInvitation inv = invitations.findByTokenHash(Tokens.sha256(rawToken))
                .filter(i -> i.isPending(clock.instant()))
                .orElseThrow(() -> new ApiException(ErrorCode.INVITATION_INVALID));
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        if (!user.getEmail().equalsIgnoreCase(inv.getEmail())) {
            throw new ApiException(ErrorCode.INVITATION_INVALID, "This invitation was sent to a different email address.");
        }
        if (members.findByWorkspaceIdAndUserId(inv.getWorkspaceId(), userId).isEmpty()) {
            members.save(new WorkspaceMember(inv.getWorkspaceId(), userId, inv.getRole()));
        }
        inv.setAcceptedAt(clock.instant());
        return toDto(guard.workspaceUnchecked(inv.getWorkspaceId()), inv.getRole());
    }

    private WorkspaceDto toDto(Workspace w, WorkspaceRole role) {
        return new WorkspaceDto(w.getId(), w.getName(), w.getSlug(), role,
                projects.countByWorkspaceIdAndArchivedAtIsNull(w.getId()), members.findByWorkspaceId(w.getId()).size());
    }

    private InvitationDto toDto(WorkspaceInvitation i) {
        return new InvitationDto(i.getId(), i.getEmail(), i.getRole(), i.getExpiresAt(), i.getCreatedAt());
    }

    private String uniqueSlug(String name) {
        String base = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("[^\\p{ASCII}]", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (base.isEmpty()) base = "workspace";
        if (base.length() > 60) base = base.substring(0, 60);
        String slug = base;
        int n = 2;
        while (workspaces.existsBySlug(slug)) {
            slug = base + "-" + n++;
        }
        return slug;
    }
}
