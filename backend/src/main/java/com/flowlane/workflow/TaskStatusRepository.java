package com.flowlane.workflow;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskStatusRepository extends JpaRepository<TaskStatus, UUID> {

    List<TaskStatus> findByProjectIdOrderByPosition(UUID projectId);

    List<TaskStatus> findByWorkspaceId(UUID workspaceId);
}
