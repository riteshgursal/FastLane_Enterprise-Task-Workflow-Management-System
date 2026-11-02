package com.flowlane.task;

import com.flowlane.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
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
@Table(name = "task")
public class Task extends BaseEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(nullable = false, columnDefinition = "BINARY(16)")
    private UUID workspaceId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(nullable = false, columnDefinition = "BINARY(16)")
    private UUID projectId;

    @Column(nullable = false)
    private int number;

    @Column(nullable = false)
    private String taskKey;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(nullable = false, columnDefinition = "BINARY(16)")
    private UUID statusId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(columnDefinition = "BINARY(16)")
    private UUID pendingApprovalStatusId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Priority priority = Priority.MEDIUM;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(columnDefinition = "BINARY(16)")
    private UUID assigneeId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(nullable = false, columnDefinition = "BINARY(16)")
    private UUID reporterId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(columnDefinition = "BINARY(16)")
    private UUID parentTaskId;

    private LocalDate startDate;
    private LocalDate dueDate;
    private Integer estimatePoints;
    private String labels;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Recurrence recurrence = Recurrence.NONE;

    @Column(nullable = false)
    private String taskRank;

    private Instant completedAt;
    private Instant deletedAt;

    public List<String> labelList() {
        if (labels == null || labels.isBlank()) return List.of();
        return Arrays.stream(labels.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    public void setLabelList(List<String> list) {
        this.labels = list == null || list.isEmpty() ? null
                : String.join(",", list.stream().map(String::trim).filter(s -> !s.isEmpty()).distinct().limit(10).toList());
    }
}
