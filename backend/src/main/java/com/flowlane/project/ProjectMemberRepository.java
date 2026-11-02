package com.flowlane.project;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, UUID> {

    Optional<ProjectMember> findByProjectIdAndUserId(UUID projectId, UUID userId);

    List<ProjectMember> findByProjectId(UUID projectId);

    List<ProjectMember> findByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);

    List<ProjectMember> findByProjectIdAndRole(UUID projectId, ProjectRole role);
}
