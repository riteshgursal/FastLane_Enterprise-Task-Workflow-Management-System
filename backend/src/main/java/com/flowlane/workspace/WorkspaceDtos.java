package com.flowlane.workspace;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class WorkspaceDtos {

    private WorkspaceDtos() {}

    public record CreateWorkspaceRequest(@NotBlank @Size(max = 80) String name) {}

    public record UpdateWorkspaceRequest(@NotBlank @Size(max = 80) String name) {}

    public record WorkspaceDto(UUID id, String name, String slug, WorkspaceRole myRole, long projectCount, long memberCount) {}

    public record MemberDto(UUID userId, String displayName, String initials, String email, WorkspaceRole role,
                            long openTasks, Instant joinedAt) {}

    public record ChangeRoleRequest(@NotNull WorkspaceRole role) {}

    public record InviteRequest(@NotBlank @Email String email, @NotNull WorkspaceRole role) {}

    public record InvitationDto(UUID id, String email, WorkspaceRole role, Instant expiresAt, Instant createdAt) {}

    public record InvitationPreview(String workspaceName, String email, WorkspaceRole role, boolean valid) {}
}
