package com.gocomet.ridehailing.common.exception;

public class InvalidStateTransitionException extends RuntimeException {
    public InvalidStateTransitionException(String message) {
        super(message);
    }

    public InvalidStateTransitionException(String entity, String currentState, String targetState) {
        super(String.format("Cannot transition %s from %s to %s", entity, currentState, targetState));
    }
}
