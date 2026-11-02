package com.flowlane.workspace;

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
@Table(name = "workspace")
public class Workspace extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(nullable = false, columnDefinition = "BINARY(16)")
    private UUID ownerId;

    private Instant archivedAt;

    public Workspace(String name, String slug, UUID ownerId) {
        this.name = name;
        this.slug = slug;
        this.ownerId = ownerId;
    }
}
