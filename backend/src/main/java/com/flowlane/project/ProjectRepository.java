package com.flowlane.project;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

    List<Project> findByWorkspaceIdAndArchivedAtIsNullOrderByName(UUID workspaceId);

    long countByWorkspaceIdAndArchivedAtIsNull(UUID workspaceId);

    boolean existsByWorkspaceIdAndProjectKey(UUID workspaceId, String projectKey);

    /** Row lock so two tasks created at once never get the same number. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Project p where p.id = :id")
    Optional<Project> lockById(@Param("id") UUID id);
}
