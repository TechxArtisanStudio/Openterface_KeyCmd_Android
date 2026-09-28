package com.openterface.keymod.agent.core;

/**
 * Agent state-machine states.
 *
 * <pre>
 * IDLE ──submit()──→ THINKING ──planReady──→ WAITING_APPROVE
 *                                               │
 *                                     ┌─────────┴─────────┐
 *                                     ↓                   ↓
 *                               approveAndRun()         cancel()
 *                                     ↓                   ↓
 *                               EXECUTING              IDLE
 *                                     │
 *                               ┌─────┴─────┐
 *                               ↓           ↓
 *                            IDLE       RETRYING ──→ EXECUTING
 *                          (finished)      │
 *                                     maxRetries?
 *                                          ↓
 *                                        IDLE (failed)
 * </pre>
 */
public enum AgentState {

    /** Waiting for user input */
    IDLE,

    /** LLM is generating a plan */
    THINKING,

    /** Plan generated, waiting for user approval */
    WAITING_APPROVE,

    /** Plan steps are being executed */
    EXECUTING,

    /** Execution failed, retrying */
    RETRYING,

    /** An error occurred */
    ERROR
}
