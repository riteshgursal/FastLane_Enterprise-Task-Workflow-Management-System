package com.flowlane.common.exception;

import org.springframework.http.HttpStatus;

/** Stable machine-readable error codes returned in the {@code code} field of every problem response. */
public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Some fields are invalid"),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Sign in to continue"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Email or password is incorrect"),
    ACCOUNT_LOCKED(HttpStatus.LOCKED, "Account temporarily locked"),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "Session expired"),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Too many attempts"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "You don't have permission to do that"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Not found"),
    EMAIL_TAKEN(HttpStatus.CONFLICT, "That email is already registered"),
    PROJECT_KEY_TAKEN(HttpStatus.CONFLICT, "That project key is already used in this workspace"),
    VERSION_CONFLICT(HttpStatus.CONFLICT, "Someone else changed this first"),
    ALREADY_MEMBER(HttpStatus.CONFLICT, "Already a member"),
    INVITATION_INVALID(HttpStatus.valueOf(422), "Invitation is invalid or expired"),
    INVALID_TRANSITION(HttpStatus.valueOf(422), "That status change isn't allowed"),
    APPROVAL_PENDING(HttpStatus.CONFLICT, "Waiting for approval"),
    NO_PENDING_APPROVAL(HttpStatus.valueOf(422), "Nothing to approve"),
    OPEN_SUBTASKS(HttpStatus.valueOf(422), "Finish the open subtasks first"),
    BLOCKED_BY_DEPENDENCY(HttpStatus.valueOf(422), "A blocking task isn't done yet"),
    STATUS_IN_USE(HttpStatus.valueOf(422), "Move the tasks out of this status first"),
    INVALID_WORKFLOW(HttpStatus.valueOf(422), "Workflow configuration is invalid"),
    LAST_OWNER(HttpStatus.valueOf(422), "A workspace needs at least one owner"),
    FILE_REJECTED(HttpStatus.valueOf(422), "That file type or size isn't allowed"),
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "Bad request"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong");

    private final HttpStatus status;
    private final String title;

    ErrorCode(HttpStatus status, String title) {
        this.status = status;
        this.title = title;
    }

    public HttpStatus status() {
        return status;
    }

    public String title() {
        return title;
    }
}
