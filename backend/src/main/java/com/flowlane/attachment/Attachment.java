package com.flowlane.attachment;

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
@Table(name = "task_attachment")
public class Attachment extends BaseEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(nullable = false, columnDefinition = "BINARY(16)")
    private UUID workspaceId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(nullable = false, columnDefinition = "BINARY(16)")
    private UUID taskId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(nullable = false, columnDefinition = "BINARY(16)")
    private UUID uploaderId;

    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false)
    private String contentType;

    private long sizeBytes;

    @Column(nullable = false)
    private String storageKey;
}
