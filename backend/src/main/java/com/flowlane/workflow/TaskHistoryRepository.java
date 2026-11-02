package com.flowlane.workflow;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskHistoryRepository extends JpaRepository<TaskHistory, UUID> {

    List<TaskHistory> findByTaskIdOrderByCreatedAtDesc(UUID taskId);

    @Query("select h from TaskHistory h where h.workspaceId = :ws and h.projectId in :projectIds order by h.createdAt desc")
    List<TaskHistory> recent(@Param("ws") UUID workspaceId, @Param("projectIds") Collection<UUID> projectIds, Pageable page);
}
