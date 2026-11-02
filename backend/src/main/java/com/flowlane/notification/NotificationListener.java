package com.flowlane.notification;

import com.flowlane.common.config.FlowlaneProperties;
import com.flowlane.common.event.DomainEvents.ApprovalRequested;
import com.flowlane.common.event.DomainEvents.Commented;
import com.flowlane.common.event.DomainEvents.DueReminder;
import com.flowlane.common.event.DomainEvents.InvitationCreated;
import com.flowlane.common.event.DomainEvents.Mentioned;
import com.flowlane.common.event.DomainEvents.TaskAssigned;
import com.flowlane.common.event.DomainEvents.TaskTransitioned;
import com.flowlane.common.event.DomainEvents.UserRegistered;
import com.flowlane.identity.User;
import com.flowlane.identity.UserRepository;
import com.flowlane.project.ProjectMember;
import com.flowlane.project.ProjectMemberRepository;
import com.flowlane.project.ProjectRole;
import com.flowlane.workspace.WorkspaceMember;
import com.flowlane.workspace.WorkspaceMemberRepository;
import com.flowlane.workspace.WorkspaceRole;
import java.time.Clock;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Turns domain events into in-app notifications and queued emails. Runs inside the publishing
 * transaction, so a rolled-back change never notifies anyone.
 */
@Component
public class NotificationListener {

    private final NotificationRepository notifications;
    private final OutboxRepository outbox;
    private final UserRepository users;
    private final ProjectMemberRepository projectMembers;
    private final WorkspaceMemberRepository workspaceMembers;
    private final Clock clock;
    private final String origin;

    public NotificationListener(NotificationRepository notifications, OutboxRepository outbox, UserRepository users,
                                ProjectMemberRepository projectMembers, WorkspaceMemberRepository workspaceMembers,
                                Clock clock, FlowlaneProperties props) {
        this.notifications = notifications;
        this.outbox = outbox;
        this.users = users;
        this.projectMembers = projectMembers;
        this.workspaceMembers = workspaceMembers;
        this.clock = clock;
        this.origin = props.frontendOrigin();
    }

    @EventListener
    public void onAssigned(TaskAssigned e) {
        if (e.assigneeId().equals(e.actorId())) return;
        String actor = name(e.actorId());
        deliver(e.workspaceId(), e.assigneeId(), e.actorId(), NotificationType.ASSIGNED, e.taskId(),
                actor + " assigned you " + e.taskKey() + " " + e.title(), User::isNotifyAssigned,
                e.taskKey() + " was assigned to you", taskLink(e.taskId()));
    }

    @EventListener
    public void onTransitioned(TaskTransitioned e) {
        if (e.assigneeId() == null || e.assigneeId().equals(e.actorId())) return;
        deliver(e.workspaceId(), e.assigneeId(), e.actorId(), NotificationType.STATUS, e.taskId(),
                name(e.actorId()) + " moved " + e.taskKey() + " to " + e.to(), u -> false, null, null);
    }

    @EventListener
    public void onApprovalRequested(ApprovalRequested e) {
        Set<UUID> managers = new LinkedHashSet<>();
        projectMembers.findByProjectIdAndRole(e.projectId(), ProjectRole.MANAGER).stream().map(ProjectMember::getUserId).forEach(managers::add);
        workspaceMembers.findByWorkspaceId(e.workspaceId()).stream()
                .filter(m -> m.getRole().atLeast(WorkspaceRole.ADMIN)).map(WorkspaceMember::getUserId).forEach(managers::add);
        managers.remove(e.actorId());
        for (UUID m : managers) {
            deliver(e.workspaceId(), m, e.actorId(), NotificationType.APPROVAL, e.taskId(),
                    e.taskKey() + " is waiting for your approval to move to " + e.to(), User::isNotifyApprovals,
                    "Approval needed: " + e.taskKey(), taskLink(e.taskId()));
        }
    }

    @EventListener
    public void onMentioned(Mentioned e) {
        String actor = name(e.actorId());
        for (UUID u : e.userIds()) {
            deliver(e.workspaceId(), u, e.actorId(), NotificationType.MENTION, e.taskId(),
                    actor + " mentioned you on " + e.taskKey() + ": “" + e.excerpt() + "”", User::isNotifyMentions,
                    actor + " mentioned you on " + e.taskKey(), taskLink(e.taskId()));
        }
    }

    @EventListener
    public void onCommented(Commented e) {
        Set<UUID> to = new LinkedHashSet<>();
        if (e.assigneeId() != null) to.add(e.assigneeId());
        to.add(e.reporterId());
        to.remove(e.actorId());
        String actor = name(e.actorId());
        for (UUID u : to) {
            deliver(e.workspaceId(), u, e.actorId(), NotificationType.COMMENT, e.taskId(),
                    actor + " commented on " + e.taskKey(), u2 -> false, null, null);
        }
    }

    @EventListener
    public void onDue(DueReminder e) {
        NotificationType type = e.overdue() ? NotificationType.OVERDUE : NotificationType.DUE;
        String msg = e.overdue() ? e.taskKey() + " " + e.title() + " is overdue" : e.taskKey() + " " + e.title() + " is due tomorrow";
        deliver(e.workspaceId(), e.assigneeId(), null, type, e.taskId(), msg, User::isNotifyDue, msg, taskLink(e.taskId()));
    }

    @EventListener
    public void onInvitation(InvitationCreated e) {
        queueEmail("INVITATION", e.email(), name(e.inviterId()) + " invited you to " + e.workspaceName() + " on Flowlane",
                "Join " + e.workspaceName() + " on Flowlane:\n\n" + e.link() + "\n\nThis link expires in 7 days.");
    }

    @EventListener
    public void onUserRegistered(UserRegistered e) {
        queueEmail("WELCOME", e.email(), "Welcome to Flowlane!",
                "Hi " + e.displayName() + ",\n\nWelcome to Flowlane! Your account has been created successfully.\n\nSign in to get started: " + origin + "/login\n\nEvery task, in its lane.");
    }

    private void deliver(UUID workspaceId, UUID recipientId, UUID actorId, NotificationType type, UUID taskId, String message,
                        Predicate<User> wantsEmail, String emailSubject, String emailLink) {
        Notification n = new Notification();
        n.setWorkspaceId(workspaceId);
        n.setRecipientId(recipientId);
        n.setActorId(actorId);
        n.setType(type);
        n.setTaskId(taskId);
        n.setMessage(message.length() > 500 ? message.substring(0, 497) + "…" : message);
        notifications.save(n);
        if (emailSubject == null) return;
        users.findById(recipientId).filter(User::isEnabled).filter(wantsEmail).ifPresent(u ->
                queueEmail(type.name(), u.getEmail(), emailSubject, message + "\n\nOpen in Flowlane: " + emailLink));
    }

    private void queueEmail(String type, String to, String subject, String body) {
        OutboxEvent o = new OutboxEvent();
        o.setType(type);
        o.setRecipientEmail(to);
        o.setSubject(subject.length() > 200 ? subject.substring(0, 200) : subject);
        o.setBody(body);
        o.setNextAttemptAt(clock.instant());
        outbox.save(o);
    }

    private String name(UUID userId) {
        if (userId == null) return "Flowlane";
        return users.findById(userId).map(User::getDisplayName).filter(Objects::nonNull).orElse("Someone");
    }

    private String taskLink(UUID taskId) {
        return origin + "/tasks/" + taskId;
    }
}
