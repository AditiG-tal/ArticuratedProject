package com.articurated.service;

import com.articurated.enums.OrderStatus;
import com.articurated.exception.InvalidStateTransitionException;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Defines valid state transitions for an Order.
 *
 * State Machine:
 *   PENDING_PAYMENT -> PAID
 *   PAID -> PROCESSING_IN_WAREHOUSE
 *   PROCESSING_IN_WAREHOUSE -> SHIPPED
 *   SHIPPED -> DELIVERED
 *   PENDING_PAYMENT -> CANCELLED
 *   PAID -> CANCELLED
 */
@Component
public class OrderStateMachine {

    private static final Map<OrderStatus, Set<OrderStatus>> VALID_TRANSITIONS = new EnumMap<>(OrderStatus.class);

    static {
        VALID_TRANSITIONS.put(OrderStatus.PENDING_PAYMENT,
                EnumSet.of(OrderStatus.PAID, OrderStatus.CANCELLED));
        VALID_TRANSITIONS.put(OrderStatus.PAID,
                EnumSet.of(OrderStatus.PROCESSING_IN_WAREHOUSE, OrderStatus.CANCELLED));
        VALID_TRANSITIONS.put(OrderStatus.PROCESSING_IN_WAREHOUSE,
                EnumSet.of(OrderStatus.SHIPPED));
        VALID_TRANSITIONS.put(OrderStatus.SHIPPED,
                EnumSet.of(OrderStatus.DELIVERED));
        VALID_TRANSITIONS.put(OrderStatus.DELIVERED, EnumSet.noneOf(OrderStatus.class));
        VALID_TRANSITIONS.put(OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class));
    }

    public void validateTransition(OrderStatus current, OrderStatus next) {
        Set<OrderStatus> allowed = VALID_TRANSITIONS.getOrDefault(current, EnumSet.noneOf(OrderStatus.class));
        if (!allowed.contains(next)) {
            throw new InvalidStateTransitionException(
                    String.format("Cannot transition Order from %s to %s. Allowed transitions: %s",
                            current, next, allowed));
        }
    }

    public Set<OrderStatus> getAllowedTransitions(OrderStatus current) {
        return VALID_TRANSITIONS.getOrDefault(current, EnumSet.noneOf(OrderStatus.class));
    }
}
