package com.flowlane.identity;

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
@Table(name = "refresh_token")
public class RefreshToken extends BaseEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(nullable = false, columnDefinition = "BINARY(16)")
    private UUID userId;

    @Column(nullable = false, unique = true)
    private String tokenHash;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(nullable = false, columnDefinition = "BINARY(16)")
    private UUID familyId;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant revokedAt;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(columnDefinition = "BINARY(16)")
    private UUID replacedBy;

    public RefreshToken(UUID userId, String tokenHash, UUID familyId, Instant expiresAt) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.familyId = familyId;
        this.expiresAt = expiresAt;
    }

    public boolean isActive(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }
}
