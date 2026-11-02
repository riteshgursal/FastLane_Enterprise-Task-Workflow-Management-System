package com.flowlane.identity;

import com.flowlane.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "app_user")
public class User extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SystemRole systemRole = SystemRole.USER;

    @Column(nullable = false)
    private String timezone = "UTC";

    @Column(nullable = false)
    private String theme = "system";

    private boolean enabled = true;
    private int failedLogins;
    private Instant lockedUntil;

    private boolean notifyAssigned = true;
    private boolean notifyMentions = true;
    private boolean notifyDue = true;
    private boolean notifyApprovals = true;
    private boolean dailyDigest;

    public User(String email, String passwordHash, String displayName) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
    }

    public String initials() {
        String[] parts = displayName.trim().split("\\s+");
        String a = parts[0].substring(0, 1);
        String b = parts.length > 1 ? parts[parts.length - 1].substring(0, 1) : (parts[0].length() > 1 ? parts[0].substring(1, 2) : "");
        return (a + b).toUpperCase();
    }
}
