package com.flowlane.common.web;

import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Builds a safe Pageable: page size capped at 100 and only whitelisted sort fields. */
public final class Pages {

    public static final int MAX_SIZE = 100;

    private Pages() {}

    public static Pageable of(int page, int size, String sort, Set<String> allowed, String defaultSort) {
        int safeSize = Math.min(Math.max(size, 1), MAX_SIZE);
        String field = defaultSort;
        Sort.Direction direction = Sort.Direction.ASC;
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",");
            if (allowed.contains(parts[0])) {
                field = parts[0];
            }
            if (parts.length > 1 && parts[1].equalsIgnoreCase("desc")) {
                direction = Sort.Direction.DESC;
            }
        }
        return PageRequest.of(Math.max(page, 0), safeSize, Sort.by(direction, field));
    }
}
