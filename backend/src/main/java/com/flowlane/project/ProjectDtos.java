package com.flowlane.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public final class ProjectDtos {

    private ProjectDtos() {}

    public record CreateProjectRequest(
            @NotBlank @Size(max = 80) String name,
            @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9]{1,9}", message = "2 to 10 capital letters or digits") String key,
            @Size(max = 2000) String description,
            @Pattern(regexp = "#[0-9A-Fa-f]{6}") String color,
            LocalDate startDate,
            LocalDate endDate) {}

    public record UpdateProjectRequest(
            @Size(min = 1, max = 80) String name,
            @Size(max = 2000) String description,
            @Pattern(regexp = "#[0-9A-Fa-f]{6}") String color,
            LocalDate startDate,
            LocalDate endDate) {}

    public record ProjectDto(UUID id, UUID workspaceId, String key, String name, String description, String color,
                             LocalDate startDate, LocalDate endDate, ProjectRole myRole, long totalTasks, long doneTasks,
                             long overdueTasks) {}

    public record ProjectMemberDto(UUID userId, String displayName, String initials, String email, ProjectRole role) {}

    public record AddProjectMemberRequest(@NotNull UUID userId, @NotNull ProjectRole role) {}

    public record ChangeProjectRoleRequest(@NotNull ProjectRole role) {}
}
