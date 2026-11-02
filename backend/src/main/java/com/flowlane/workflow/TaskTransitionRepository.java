package com.flowlane.workflow;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskTransitionRepository extends JpaRepository<TaskTransition, UUID> {

    List<TaskTransition> findByProjectId(UUID projectId);

    Optional<TaskTransition> findByFromStatusIdAndToStatusId(UUID fromStatusId, UUID toStatusId);

    void deleteByProjectId(UUID projectId);
}
