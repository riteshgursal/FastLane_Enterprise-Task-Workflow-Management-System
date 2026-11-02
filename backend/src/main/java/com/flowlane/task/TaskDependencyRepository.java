package com.flowlane.task;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskDependencyRepository extends JpaRepository<TaskDependency, UUID> {

    List<TaskDependency> findByTaskId(UUID taskId);

    List<TaskDependency> findByBlockedById(UUID blockedById);

    Optional<TaskDependency> findByTaskIdAndBlockedById(UUID taskId, UUID blockedById);
}
