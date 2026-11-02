package com.flowlane.workflow;

import java.util.EnumSet;
import java.util.Set;

/** Fixed categories every project status maps to, so analytics compare across projects. */
public enum StatusCategory {
    TODO, IN_PROGRESS, DONE, CANCELLED;

    public static final Set<StatusCategory> OPEN = EnumSet.of(TODO, IN_PROGRESS);

    public boolean isClosed() {
        return this == DONE || this == CANCELLED;
    }
}
