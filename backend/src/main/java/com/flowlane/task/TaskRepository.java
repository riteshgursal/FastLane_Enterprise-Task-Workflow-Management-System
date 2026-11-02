package com.flowlane.task;

import com.flowlane.workflow.StatusCategory;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskRepository extends JpaRepository<Task, UUID> {

    Optional<Task> findByIdAndDeletedAtIsNull(UUID id);

    Optional<Task> findByWorkspaceIdAndTaskKeyAndDeletedAtIsNull(UUID workspaceId, String taskKey);

    List<Task> findByProjectIdAndDeletedAtIsNullOrderByTaskRank(UUID projectId);

    List<Task> findByParentTaskIdAndDeletedAtIsNullOrderByNumber(UUID parentTaskId);

    Optional<Task> findFirstByProjectIdAndStatusIdAndDeletedAtIsNullOrderByTaskRankDesc(UUID projectId, UUID statusId);

    long countByStatusIdAndDeletedAtIsNull(UUID statusId);

    @Query("select t from Task t where t.projectId = :projectId and t.deletedAt is null"
            + " and (:statusId is null or t.statusId = :statusId)"
            + " and (:assigneeId is null or t.assigneeId = :assigneeId)"
            + " and (:priority is null or t.priority = :priority)"
            + " and (:dueBefore is null or t.dueDate <= :dueBefore)"
            + " and (:q is null or lower(t.title) like :q or lower(t.taskKey) like :q)")
    Page<Task> search(@Param("projectId") UUID projectId, @Param("statusId") UUID statusId,
                      @Param("assigneeId") UUID assigneeId, @Param("priority") Priority priority,
                      @Param("dueBefore") LocalDate dueBefore, @Param("q") String q, Pageable pageable);

    @Query("select t from Task t where t.workspaceId = :ws and t.assigneeId = :userId and t.deletedAt is null"
            + " and t.projectId in :projectIds")
    Page<Task> assignedTo(@Param("ws") UUID workspaceId, @Param("userId") UUID userId,
                          @Param("projectIds") Collection<UUID> projectIds, Pageable pageable);

    @Query("select t from Task t where t.workspaceId = :ws and t.projectId in :projectIds and t.deletedAt is null"
            + " and t.dueDate between :from and :to order by t.dueDate, t.taskKey")
    List<Task> dueBetween(@Param("ws") UUID workspaceId, @Param("projectIds") Collection<UUID> projectIds,
                          @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select t from Task t where t.workspaceId = :ws and t.projectId in :projectIds and t.deletedAt is null"
            + " and (lower(t.title) like :q or lower(t.taskKey) like :q) order by t.updatedAt desc")
    List<Task> quickSearch(@Param("ws") UUID workspaceId, @Param("projectIds") Collection<UUID> projectIds,
                           @Param("q") String q, Pageable pageable);

    @Query("select count(t) from Task t where t.projectId = :projectId and t.deletedAt is null")
    long countByProject(@Param("projectId") UUID projectId);

    @Query("select count(t) from Task t, TaskStatus s where s.id = t.statusId and t.projectId = :projectId"
            + " and t.deletedAt is null and s.category = com.flowlane.workflow.StatusCategory.DONE")
    long countDoneByProject(@Param("projectId") UUID projectId);

    @Query("select count(t) from Task t, TaskStatus s where s.id = t.statusId and t.projectId = :projectId"
            + " and t.deletedAt is null and t.dueDate < :today"
            + " and s.category in (com.flowlane.workflow.StatusCategory.TODO, com.flowlane.workflow.StatusCategory.IN_PROGRESS)")
    long countOverdueByProject(@Param("projectId") UUID projectId, @Param("today") LocalDate today);

    @Query("select t.assigneeId, count(t) from Task t, TaskStatus s where s.id = t.statusId and t.workspaceId = :ws"
            + " and t.deletedAt is null and t.assigneeId is not null"
            + " and s.category in (com.flowlane.workflow.StatusCategory.TODO, com.flowlane.workflow.StatusCategory.IN_PROGRESS)"
            + " group by t.assigneeId")
    List<Object[]> countOpenByAssignee(@Param("ws") UUID workspaceId);

    @Query("select s.category, count(t) from Task t, TaskStatus s where s.id = t.statusId"
            + " and t.projectId in :projectIds and t.deletedAt is null group by s.category")
    List<Object[]> countByCategory(@Param("projectIds") Collection<UUID> projectIds);

    @Query("select t.completedAt from Task t where t.projectId in :projectIds and t.deletedAt is null"
            + " and t.completedAt >= :since")
    List<Instant> completedSince(@Param("projectIds") Collection<UUID> projectIds, @Param("since") Instant since);

    @Query("select t from Task t, TaskStatus s where s.id = t.statusId and t.deletedAt is null"
            + " and t.assigneeId is not null and t.dueDate = :date and s.category in :open")
    List<Task> openDueOn(@Param("date") LocalDate date, @Param("open") Collection<StatusCategory> open);

    @Query("select t from Task t, TaskStatus s where s.id = t.statusId and t.deletedAt is null"
            + " and t.assigneeId is not null and t.dueDate < :date and s.category in :open")
    List<Task> openOverdue(@Param("date") LocalDate date, @Param("open") Collection<StatusCategory> open);

    @Query("select t from Task t where t.pendingApprovalStatusId is not null and t.deletedAt is null"
            + " and t.projectId in :projectIds")
    List<Task> pendingApproval(@Param("projectIds") Collection<UUID> projectIds);
}
