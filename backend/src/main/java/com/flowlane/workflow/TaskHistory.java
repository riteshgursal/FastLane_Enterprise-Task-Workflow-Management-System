package com.flowlane.workflow;

import com.flowlane.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Append-only audit row. Never updated or deleted by the application. */
@Getter
@NoArgsConstructor
@Entity
@Immutable
@Table(name = "task_history")
public class TaskHistory extends BaseEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(nullable = false, columnDefinition = "BINARY(16)")
    private UUID workspaceId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(nullable = false, columnDefinition = "BINARY(16)")
    private UUID projectId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(nullable = false, columnDefinition = "BINARY(16)")
    private UUID taskId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(nullable = false, columnDefinition = "BINARY(16)")
    private UUID actorId;

    @Column(nullable = false)
    private String action;

    @Column(nullable = false)
    private String detail;

    public TaskHistory(UUID workspaceId, UUID projectId, UUID taskId, UUID actorId, String action, String detail) {
        this.workspaceId = workspaceId;
        this.projectId = projectId;
        this.taskId = taskId;
        this.actorId = actorId;
        this.action = action;
        this.detail = detail.length() > 500 ? detail.substring(0, 500) : detail;
    }
}
