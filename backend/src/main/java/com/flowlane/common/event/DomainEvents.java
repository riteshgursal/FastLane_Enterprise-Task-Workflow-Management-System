package com.flowlane.common.event;

import java.util.List;
import java.util.UUID;

/** Events published by services and consumed in the same transaction by history and notification listeners. */
public final class DomainEvents {

    private DomainEvents() {}

    public record TaskAssigned(UUID workspaceId, UUID taskId, String taskKey, String title, UUID assigneeId, UUID actorId) {}

    public record TaskTransitioned(UUID workspaceId, UUID taskId, String taskKey, String title, String from, String to,
                                   UUID assigneeId, UUID actorId) {}

    public record ApprovalRequested(UUID workspaceId, UUID projectId, UUID taskId, String taskKey, String title,
                                    String to, UUID actorId) {}

    public record Mentioned(UUID workspaceId, UUID taskId, String taskKey, List<UUID> userIds, UUID actorId, String excerpt) {}

    public record Commented(UUID workspaceId, UUID taskId, String taskKey, UUID assigneeId, UUID reporterId, UUID actorId) {}

    public record InvitationCreated(String email, String workspaceName, String link, UUID inviterId) {}

    public record DueReminder(UUID workspaceId, UUID taskId, String taskKey, String title, UUID assigneeId, boolean overdue) {}

    public record UserRegistered(UUID userId, String email, String displayName) {}
}
