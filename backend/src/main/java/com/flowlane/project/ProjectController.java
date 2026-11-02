package com.flowlane.project;

import com.flowlane.project.ProjectDtos.AddProjectMemberRequest;
import com.flowlane.project.ProjectDtos.ChangeProjectRoleRequest;
import com.flowlane.project.ProjectDtos.CreateProjectRequest;
import com.flowlane.project.ProjectDtos.ProjectDto;
import com.flowlane.project.ProjectDtos.ProjectMemberDto;
import com.flowlane.project.ProjectDtos.UpdateProjectRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ProjectController {

    private final ProjectService service;

    public ProjectController(ProjectService service) {
        this.service = service;
    }

    @GetMapping("/workspaces/{workspaceId}/projects")
    public List<ProjectDto> list(@PathVariable UUID workspaceId) {
        return service.list(workspaceId);
    }

    @PostMapping("/workspaces/{workspaceId}/projects")
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectDto create(@PathVariable UUID workspaceId, @Valid @RequestBody CreateProjectRequest req) {
        return service.create(workspaceId, req);
    }

    @GetMapping("/projects/{id}")
    public ProjectDto get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PatchMapping("/projects/{id}")
    public ProjectDto update(@PathVariable UUID id, @Valid @RequestBody UpdateProjectRequest req) {
        return service.update(id, req);
    }

    @PostMapping("/projects/{id}/archive")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(@PathVariable UUID id) {
        service.archive(id);
    }

    @GetMapping("/projects/{id}/members")
    public List<ProjectMemberDto> members(@PathVariable UUID id) {
        return service.members(id);
    }

    @PostMapping("/projects/{id}/members")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addMember(@PathVariable UUID id, @Valid @RequestBody AddProjectMemberRequest req) {
        service.addMember(id, req.userId(), req.role());
    }

    @PatchMapping("/projects/{id}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changeRole(@PathVariable UUID id, @PathVariable UUID userId, @Valid @RequestBody ChangeProjectRoleRequest req) {
        service.addMember(id, userId, req.role());
    }

    @DeleteMapping("/projects/{id}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@PathVariable UUID id, @PathVariable UUID userId) {
        service.removeMember(id, userId);
    }
}
