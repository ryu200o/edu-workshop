package io.github.ryu200o.eduworkshop.attendance.internal.domain.model;

/**
 * Role of the actor performing an attendance decision, as enforced by the State Matrix
 * (ADR 0019 §9). Role authorization is an Application concern (authenticated principal → 403 on
 * violation, ADR 0019 §8); the domain records the role on each ledger entry.
 */
public enum ActorRole {
    TRAINER,
    STUDENT,
    AUDITOR,
    SYSTEM
}