package com.articurated;

import com.articurated.enums.OrderStatus;
import com.articurated.exception.InvalidStateTransitionException;
import com.articurated.service.OrderStateMachine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderStateMachineTest {

    private OrderStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        stateMachine = new OrderStateMachine();
    }

    @ParameterizedTest
    @DisplayName("Valid order transitions should not throw")
    @CsvSource({
            "PENDING_PAYMENT, PAID",
            "PENDING_PAYMENT, CANCELLED",
            "PAID, PROCESSING_IN_WAREHOUSE",
            "PAID, CANCELLED",
            "PROCESSING_IN_WAREHOUSE, SHIPPED",
            "SHIPPED, DELIVERED"
    })
    void validTransitions(OrderStatus from, OrderStatus to) {
        // Should not throw
        stateMachine.validateTransition(from, to);
    }

    @ParameterizedTest
    @DisplayName("Invalid order transitions should throw InvalidStateTransitionException")
    @CsvSource({
            "PENDING_PAYMENT, SHIPPED",
            "PENDING_PAYMENT, DELIVERED",
            "PAID, SHIPPED",
            "PROCESSING_IN_WAREHOUSE, PAID",
            "PROCESSING_IN_WAREHOUSE, CANCELLED",
            "DELIVERED, CANCELLED",
            "CANCELLED, PAID"
    })
    void invalidTransitions(OrderStatus from, OrderStatus to) {
        assertThatThrownBy(() -> stateMachine.validateTransition(from, to))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("Cannot transition Order from " + from);
    }

    @Test
    @DisplayName("getAllowedTransitions returns correct set for PENDING_PAYMENT")
    void allowedTransitionsForPendingPayment() {
        var allowed = stateMachine.getAllowedTransitions(OrderStatus.PENDING_PAYMENT);
        assertThat(allowed).containsExactlyInAnyOrder(OrderStatus.PAID, OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("getAllowedTransitions returns empty set for terminal states")
    void allowedTransitionsForTerminalStates() {
        assertThat(stateMachine.getAllowedTransitions(OrderStatus.DELIVERED)).isEmpty();
        assertThat(stateMachine.getAllowedTransitions(OrderStatus.CANCELLED)).isEmpty();
    }
}
