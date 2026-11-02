package com.flowlane.project;

public enum ProjectRole {
    VIEWER, CONTRIBUTOR, MANAGER;

    public boolean atLeast(ProjectRole other) {
        return this.ordinal() >= other.ordinal();
    }
}
