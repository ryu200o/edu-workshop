/**
 * Shared security abstractions consumed by every module's inbound adapters. Holds only
 * framework-agnostic principal types — the IAM module's filter populates them; business modules read
 * them. No module imports another module's internals at the adapter layer (ADR 0010).
 */
package io.github.ryu200o.eduworkshop.shared.security;
