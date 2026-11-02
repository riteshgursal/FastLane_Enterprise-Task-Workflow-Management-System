package com.flowlane.workspace;

public enum WorkspaceRole {
    MEMBER, ADMIN, OWNER;

    public boolean atLeast(WorkspaceRole other) {
        return this.ordinal() >= other.ordinal();
    }
}
