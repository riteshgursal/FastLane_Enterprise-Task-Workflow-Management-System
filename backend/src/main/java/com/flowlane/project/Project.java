package com.flowlane.project;

import com.flowlane.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
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
@Table(name = "project")
public class Project extends BaseEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(nullable = false, columnDefinition = "BINARY(16)")
    private UUID workspaceId;

    @Column(nullable = false)
    private String projectKey;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(nullable = false)
    private String color;

    private LocalDate startDate;
    private LocalDate endDate;

    @Column(nullable = false)
    private int nextTaskNumber = 1;

    private Instant archivedAt;

    public int takeTaskNumber() {
        return nextTaskNumber++;
    }
}
