package com.example.payment.service;

import com.example.payment.exception.InvalidStateTransitionException;
import com.example.payment.model.PaymentState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Payment State Machine Service - Enforces valid state transitions.
 *
 * <p>This service implements the core state machine logic for payment processing,
 * ensuring that only valid state transitions are allowed and providing audit
 * logging for all state changes.</p>
 *
 * <h2>Design Decisions (AI Architectural Dialogue):</h2>
 * <p><strong>Q:</strong> Why not use Spring Statemachine library?</p>
 * <p><strong>A:</strong> For our use case with straightforward linear flows, a custom
 * implementation provides better control, easier debugging, and no external dependency.
 * Spring Statemachine adds value for complex workflows with multiple regions,
 * hierarchical states, or distributed state management - none of which we need here.</p>
 *
 * <h2>State Transition Matrix:</h2>
 * <pre>
 * From State           | Allowed Transitions
 * ---------------------|--------------------------------------------
 * CREATED              | PENDING
 * PENDING              | AUTHORIZED, CAPTURED, DECLINED, ERROR, HELD_FOR_REVIEW
 * AUTHORIZED           | CAPTURED, VOIDED, ERROR
 * CAPTURED             | REFUNDED, PARTIALLY_REFUNDED
 * PARTIALLY_REFUNDED   | REFUNDED, PARTIALLY_REFUNDED
 * ERROR                | PENDING (retry)
 * HELD_FOR_REVIEW      | AUTHORIZED, DECLINED
 * DECLINED             | (terminal - no transitions)
 * VOIDED               | (terminal - no transitions)
 * REFUNDED             | (terminal - no transitions)
 * </pre>
 */
@Service
public class PaymentStateMachine {

    private static final Logger log = LoggerFactory.getLogger(PaymentStateMachine.class);

    /**
     * State transition matrix defining allowed transitions.
     * Key: source state, Value: set of allowed target states
     */
    private static final Map<PaymentState, Set<PaymentState>> TRANSITIONS;

    static {
        Map<PaymentState, Set<PaymentState>> transitions = new EnumMap<>(PaymentState.class);

        // CREATED can only move to PENDING (payment initiated)
        transitions.put(PaymentState.CREATED, EnumSet.of(
            PaymentState.PENDING
        ));

        // PENDING can resolve to multiple outcomes based on gateway response
        transitions.put(PaymentState.PENDING, EnumSet.of(
            PaymentState.AUTHORIZED,      // Auth-only approved
            PaymentState.CAPTURED,        // Purchase approved
            PaymentState.DECLINED,        // Transaction declined
            PaymentState.ERROR,           // Gateway error
            PaymentState.HELD_FOR_REVIEW  // Fraud review
        ));

        // AUTHORIZED can be captured, voided, or encounter error
        transitions.put(PaymentState.AUTHORIZED, EnumSet.of(
            PaymentState.CAPTURED,
            PaymentState.VOIDED,
            PaymentState.ERROR
        ));

        // CAPTURED can be fully or partially refunded
        transitions.put(PaymentState.CAPTURED, EnumSet.of(
            PaymentState.REFUNDED,
            PaymentState.PARTIALLY_REFUNDED
        ));

        // PARTIALLY_REFUNDED can receive more refunds
        transitions.put(PaymentState.PARTIALLY_REFUNDED, EnumSet.of(
            PaymentState.REFUNDED,
            PaymentState.PARTIALLY_REFUNDED
        ));

        // ERROR can be retried (goes back to PENDING)
        transitions.put(PaymentState.ERROR, EnumSet.of(
            PaymentState.PENDING
        ));

        // HELD_FOR_REVIEW resolves to AUTHORIZED or DECLINED
        transitions.put(PaymentState.HELD_FOR_REVIEW, EnumSet.of(
            PaymentState.AUTHORIZED,
            PaymentState.DECLINED
        ));

        // Terminal states - no outgoing transitions
        transitions.put(PaymentState.DECLINED, EnumSet.noneOf(PaymentState.class));
        transitions.put(PaymentState.VOIDED, EnumSet.noneOf(PaymentState.class));
        transitions.put(PaymentState.REFUNDED, EnumSet.noneOf(PaymentState.class));

        TRANSITIONS = Collections.unmodifiableMap(transitions);
    }

    /**
     * Check if a state transition is valid.
     *
     * @param from The current state
     * @param to The target state
     * @return true if the transition is allowed
     */
    public boolean canTransition(PaymentState from, PaymentState to) {
        if (from == null || to == null) {
            return false;
        }
        Set<PaymentState> allowedTargets = TRANSITIONS.get(from);
        return allowedTargets != null && allowedTargets.contains(to);
    }

    /**
     * Validate and perform a state transition.
     *
     * @param from The current state
     * @param to The target state
     * @param orderId The order ID (for logging)
     * @throws InvalidStateTransitionException if the transition is not allowed
     */
    public void validateTransition(PaymentState from, PaymentState to, Long orderId) {
        if (!canTransition(from, to)) {
            log.warn("Invalid state transition attempted: {} -> {} for order {}",
                    from, to, orderId);
            throw new InvalidStateTransitionException(from, to, orderId);
        }
        log.info("State transition: {} -> {} for order {}", from, to, orderId);
    }

    /**
     * Get all allowed transitions from a given state.
     *
     * @param from The source state
     * @return Set of allowed target states (never null, may be empty)
     */
    public Set<PaymentState> getAllowedTransitions(PaymentState from) {
        if (from == null) {
            return Collections.emptySet();
        }
        Set<PaymentState> allowed = TRANSITIONS.get(from);
        return allowed != null ? Collections.unmodifiableSet(allowed) : Collections.emptySet();
    }

    /**
     * Check if a state is terminal (no further transitions allowed).
     *
     * @param state The state to check
     * @return true if the state is terminal
     */
    public boolean isTerminal(PaymentState state) {
        return state != null && state.isTerminal();
    }

    /**
     * Determine the appropriate PaymentState based on transaction type and success.
     *
     * @param transactionType The type of transaction (purchase, authorize, capture, etc.)
     * @param success Whether the transaction was successful
     * @param errorRetriable Whether any error is potentially retriable
     * @return The appropriate PaymentState
     */
    public PaymentState determineStateForTransaction(String transactionType,
                                                     boolean success,
                                                     boolean errorRetriable) {
        if (success) {
            switch (transactionType.toLowerCase()) {
                case "purchase":
                case "capture":
                    return PaymentState.CAPTURED;
                case "authorize":
                    return PaymentState.AUTHORIZED;
                case "void":
                    return PaymentState.VOIDED;
                case "refund":
                    return PaymentState.REFUNDED;
                default:
                    return PaymentState.CAPTURED;
            }
        } else {
            return errorRetriable ? PaymentState.ERROR : PaymentState.DECLINED;
        }
    }

    /**
     * Get a human-readable description of why a transition is not allowed.
     *
     * @param from The current state
     * @param to The target state
     * @return A description of why the transition is invalid
     */
    public String getTransitionErrorMessage(PaymentState from, PaymentState to) {
        if (from == null) {
            return "Current state is null";
        }
        if (to == null) {
            return "Target state is null";
        }
        if (from.isTerminal()) {
            return String.format("Cannot transition from terminal state %s", from.getCode());
        }
        Set<PaymentState> allowed = getAllowedTransitions(from);
        if (allowed.isEmpty()) {
            return String.format("No transitions allowed from state %s", from.getCode());
        }
        return String.format("Transition from %s to %s is not allowed. Allowed transitions: %s",
                from.getCode(), to.getCode(), allowed);
    }
}

