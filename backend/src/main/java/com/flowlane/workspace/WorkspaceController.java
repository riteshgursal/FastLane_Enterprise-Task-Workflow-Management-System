package com.flowlane.workspace;

import com.flowlane.common.security.CurrentUser;
import com.flowlane.workspace.WorkspaceDtos.ChangeRoleRequest;
import com.flowlane.workspace.WorkspaceDtos.CreateWorkspaceRequest;
import com.flowlane.workspace.WorkspaceDtos.InvitationDto;
import com.flowlane.workspace.WorkspaceDtos.InvitationPreview;
import com.flowlane.workspace.WorkspaceDtos.InviteRequest;
import com.flowlane.workspace.WorkspaceDtos.MemberDto;
import com.flowlane.workspace.WorkspaceDtos.UpdateWorkspaceRequest;
import com.flowlane.workspace.WorkspaceDtos.WorkspaceDto;
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
public class WorkspaceController {

    private final WorkspaceService service;

    public WorkspaceController(WorkspaceService service) {
        this.service = service;
    }

    @GetMapping("/workspaces")
    public List<WorkspaceDto> list() {
        return service.mine(CurrentUser.id());
    }

    @PostMapping("/workspaces")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkspaceDto create(@Valid @RequestBody CreateWorkspaceRequest req) {
        return service.create(CurrentUser.id(), req.name());
    }

    @PatchMapping("/workspaces/{id}")
    public WorkspaceDto rename(@PathVariable UUID id, @Valid @RequestBody UpdateWorkspaceRequest req) {
        return service.rename(id, req.name());
    }

    @PostMapping("/workspaces/{id}/archive")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(@PathVariable UUID id) {
        service.archive(id);
    }

    @GetMapping("/workspaces/{id}/members")
    public List<MemberDto> members(@PathVariable UUID id) {
        return service.members(id);
    }

    @PatchMapping("/workspaces/{id}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changeRole(@PathVariable UUID id, @PathVariable UUID userId, @Valid @RequestBody ChangeRoleRequest req) {
        service.changeRole(id, userId, req.role());
    }

    @DeleteMapping("/workspaces/{id}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable UUID id, @PathVariable UUID userId) {
        service.removeMember(id, userId);
    }

    @GetMapping("/workspaces/{id}/invitations")
    public List<InvitationDto> invitations(@PathVariable UUID id) {
        return service.pendingInvitations(id);
    }

    @PostMapping("/workspaces/{id}/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    public InvitationDto invite(@PathVariable UUID id, @Valid @RequestBody InviteRequest req) {
        return service.invite(id, req);
    }

    @PostMapping("/workspaces/{id}/invitations/{invitationId}/resend")
    public InvitationDto resend(@PathVariable UUID id, @PathVariable UUID invitationId) {
        return service.resend(id, invitationId);
    }

    @DeleteMapping("/workspaces/{id}/invitations/{invitationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable UUID id, @PathVariable UUID invitationId) {
        service.revoke(id, invitationId);
    }

    @GetMapping("/invitations/{token}")
    public InvitationPreview preview(@PathVariable String token) {
        return service.preview(token);
    }

    @PostMapping("/invitations/{token}/accept")
    public WorkspaceDto accept(@PathVariable String token) {
        return service.accept(CurrentUser.id(), token);
    }
}
