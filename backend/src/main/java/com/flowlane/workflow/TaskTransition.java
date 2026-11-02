package com.flowlane.workflow;

import com.flowlane.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "task_transition")
public class TaskTransition extends BaseEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(nullable = false, columnDefinition = "BINARY(16)")
    private UUID workspaceId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(nullable = false, columnDefinition = "BINARY(16)")
    private UUID projectId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(nullable = false, columnDefinition = "BINARY(16)")
    private UUID fromStatusId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(nullable = false, columnDefinition = "BINARY(16)")
    private UUID toStatusId;

    private boolean requiresApproval;

    public TaskTransition(UUID workspaceId, UUID projectId, UUID fromStatusId, UUID toStatusId, boolean requiresApproval) {
        this.workspaceId = workspaceId;
        this.projectId = projectId;
        this.fromStatusId = fromStatusId;
        this.toStatusId = toStatusId;
        this.requiresApproval = requiresApproval;
    }
}
