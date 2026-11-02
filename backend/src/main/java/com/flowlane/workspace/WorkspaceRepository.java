package com.flowlane.workspace;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkspaceRepository extends JpaRepository<Workspace, UUID> {

    boolean existsBySlug(String slug);

    @Query("select w from Workspace w where w.archivedAt is null and w.id in "
            + "(select m.workspaceId from WorkspaceMember m where m.userId = :userId) order by w.name")
    List<Workspace> findActiveForUser(@Param("userId") UUID userId);
}
