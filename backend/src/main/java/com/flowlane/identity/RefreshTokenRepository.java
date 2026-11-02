package com.flowlane.identity;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("update RefreshToken r set r.revokedAt = :now where r.familyId = :familyId and r.revokedAt is null")
    int revokeFamily(@Param("familyId") UUID familyId, @Param("now") Instant now);

    @Modifying
    @Query("update RefreshToken r set r.revokedAt = :now where r.userId = :userId and r.revokedAt is null and r.familyId <> :keepFamily")
    int revokeOtherFamilies(@Param("userId") UUID userId, @Param("keepFamily") UUID keepFamily, @Param("now") Instant now);

    @Modifying
    @Query("update RefreshToken r set r.revokedAt = :now where r.userId = :userId and r.revokedAt is null")
    int revokeAll(@Param("userId") UUID userId, @Param("now") Instant now);

    @Query("select count(distinct r.familyId) from RefreshToken r where r.userId = :userId and r.revokedAt is null and r.expiresAt > :now")
    long countActiveSessions(@Param("userId") UUID userId, @Param("now") Instant now);
}
