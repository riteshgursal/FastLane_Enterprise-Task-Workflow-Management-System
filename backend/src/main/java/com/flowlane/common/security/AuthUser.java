package com.flowlane.common.security;

import java.util.UUID;

/** The authenticated principal stored in the security context. */
public record AuthUser(UUID id) {}
