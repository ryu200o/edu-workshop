package io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.auth;

/**
 * Security Ingress use case for issuing access/refresh sessions (login and refresh-token rotation).
 *
 * <p>Per ADR 0021 these are <em>Security Token Minting Operations</em>, not Domain State Mutations:
 * they do not go through the shared {@code CommandBus} (which is strictly void) but are invoked
 * directly by the inbound HTTP adapter.</p>
 *
 * <p>The implementation ({@code AuthTokenService}) stays package-private in the application handler
 * layer; inbound adapters depend only on this port.</p>
 */
public interface AuthTokenUseCase {

    AuthTokenResponse login(String email, String password);

    AuthTokenResponse refresh(String refreshToken);
}