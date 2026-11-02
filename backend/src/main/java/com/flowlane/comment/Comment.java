package com.flowlane.comment;

import com.flowlane.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
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
@Table(name = "task_comment")
public class Comment extends BaseEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(nullable = false, columnDefinition = "BINARY(16)")
    private UUID workspaceId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(nullable = false, columnDefinition = "BINARY(16)")
    private UUID taskId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(nullable = false, columnDefinition = "BINARY(16)")
    private UUID authorId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    private Instant editedAt;
    private Instant deletedAt;
}
