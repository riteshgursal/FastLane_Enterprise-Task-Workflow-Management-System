package com.flowlane.task;

import java.time.LocalDate;

public enum Recurrence {
    NONE, WEEKLY, MONTHLY;

    public LocalDate next(LocalDate from) {
        return switch (this) {
            case WEEKLY -> from.plusWeeks(1);
            case MONTHLY -> from.plusMonths(1);
            case NONE -> from;
        };
    }
}
