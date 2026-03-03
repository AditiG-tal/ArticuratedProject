package com.articurated;

import com.articurated.enums.ReturnStatus;
import com.articurated.exception.InvalidStateTransitionException;
import com.articurated.service.ReturnStateMachine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReturnStateMachineTest {

    private ReturnStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        stateMachine = new ReturnStateMachine();
    }

    @ParameterizedTest
    @DisplayName("Valid return transitions should not throw")
    @CsvSource({
            "REQUESTED, APPROVED",
            "REQUESTED, REJECTED",
            "APPROVED, IN_TRANSIT",
            "IN_TRANSIT, RECEIVED",
            "RECEIVED, COMPLETED"
    })
    void validTransitions(ReturnStatus from, ReturnStatus to) {
        stateMachine.validateTransition(from, to);
    }

    @ParameterizedTest
    @DisplayName("Invalid return transitions should throw")
    @CsvSource({
            "REQUESTED, COMPLETED",
            "REQUESTED, IN_TRANSIT",
            "APPROVED, RECEIVED",
            "REJECTED, APPROVED",
            "COMPLETED, REQUESTED"
    })
    void invalidTransitions(ReturnStatus from, ReturnStatus to) {
        assertThatThrownBy(() -> stateMachine.validateTransition(from, to))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    @DisplayName("REJECTED is a terminal state")
    void rejectedIsTerminal() {
        assertThat(stateMachine.getAllowedTransitions(ReturnStatus.REJECTED)).isEmpty();
    }

    @Test
    @DisplayName("COMPLETED is a terminal state")
    void completedIsTerminal() {
        assertThat(stateMachine.getAllowedTransitions(ReturnStatus.COMPLETED)).isEmpty();
    }
}
