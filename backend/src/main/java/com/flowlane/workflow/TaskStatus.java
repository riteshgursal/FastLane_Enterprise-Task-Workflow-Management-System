package com.flowlane.workflow;

import com.flowlane.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "task_status")
public class TaskStatus extends BaseEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(nullable = false, columnDefinition = "BINARY(16)")
    private UUID workspaceId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(nullable = false, columnDefinition = "BINARY(16)")
    private UUID projectId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusCategory category;

    @Column(nullable = false)
    private String color;

    @Column(nullable = false)
    private int position;

    @Column(name = "is_initial", nullable = false)
    private boolean initial;

    public TaskStatus(UUID workspaceId, UUID projectId, String name, StatusCategory category, String color, int position, boolean initial) {
        this.workspaceId = workspaceId;
        this.projectId = projectId;
        this.name = name;
        this.category = category;
        this.color = color;
        this.position = position;
        this.initial = initial;
    }
}
