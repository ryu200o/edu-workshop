package io.github.ryu200o.eduworkshop.workshop.contract;

import io.github.ryu200o.eduworkshop.shared.application.cqs.api.Query;

import java.util.UUID;

/**
 * Consumer-driven query dispatched by the Workshop module over the shared {@code QueryBus} (ADR 0006)
 * to count the currently {@code REGISTERED} seats of a workshop. The Registration module implements the
 * matching {@code QueryHandler} — keeping the dependency direction natural (Registration is downstream
 * of Workshop) and breaking the former {@code Workshop -> Registration} module edge.
 */
public record CountActiveRegistrationsQuery(UUID workshopId) implements Query<Integer> {
}
