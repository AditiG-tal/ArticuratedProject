package com.articurated.service;

import com.articurated.enums.ReturnStatus;
import com.articurated.exception.InvalidStateTransitionException;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Defines valid state transitions for a Return Request.
 *
 * State Machine:
 *   REQUESTED -> APPROVED
 *   REQUESTED -> REJECTED
 *   APPROVED -> IN_TRANSIT
 *   IN_TRANSIT -> RECEIVED
 *   RECEIVED -> COMPLETED
 */
@Component
public class ReturnStateMachine {

    private static final Map<ReturnStatus, Set<ReturnStatus>> VALID_TRANSITIONS = new EnumMap<>(ReturnStatus.class);

    static {
        VALID_TRANSITIONS.put(ReturnStatus.REQUESTED,
                EnumSet.of(ReturnStatus.APPROVED, ReturnStatus.REJECTED));
        VALID_TRANSITIONS.put(ReturnStatus.APPROVED,
                EnumSet.of(ReturnStatus.IN_TRANSIT));
        VALID_TRANSITIONS.put(ReturnStatus.IN_TRANSIT,
                EnumSet.of(ReturnStatus.RECEIVED));
        VALID_TRANSITIONS.put(ReturnStatus.RECEIVED,
                EnumSet.of(ReturnStatus.COMPLETED));
        VALID_TRANSITIONS.put(ReturnStatus.REJECTED, EnumSet.noneOf(ReturnStatus.class));
        VALID_TRANSITIONS.put(ReturnStatus.COMPLETED, EnumSet.noneOf(ReturnStatus.class));
    }

    public void validateTransition(ReturnStatus current, ReturnStatus next) {
        Set<ReturnStatus> allowed = VALID_TRANSITIONS.getOrDefault(current, EnumSet.noneOf(ReturnStatus.class));
        if (!allowed.contains(next)) {
            throw new InvalidStateTransitionException(
                    String.format("Cannot transition Return from %s to %s. Allowed transitions: %s",
                            current, next, allowed));
        }
    }

    public Set<ReturnStatus> getAllowedTransitions(ReturnStatus current) {
        return VALID_TRANSITIONS.getOrDefault(current, EnumSet.noneOf(ReturnStatus.class));
    }
}
