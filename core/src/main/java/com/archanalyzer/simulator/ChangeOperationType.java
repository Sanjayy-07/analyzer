package com.archanalyzer.simulator;

/**
 * Structured operation types supported by the What-If Change Simulator.
 */
public enum ChangeOperationType {
    ADD_FEATURE,
    MODIFY_FEATURE,
    REMOVE_FEATURE,
    ADD_MODULE,
    ADD_COMPONENT,
    MODIFY_API,
    REMOVE_API,
    MODIFY_DATABASE_ENTITY,
    REFACTOR_COMPONENT,
    INTRODUCE_INTERFACE,
    ADD_DEPENDENCY,
    REMOVE_DEPENDENCY,
    REDIRECT_DEPENDENCY,
    OTHER
}
