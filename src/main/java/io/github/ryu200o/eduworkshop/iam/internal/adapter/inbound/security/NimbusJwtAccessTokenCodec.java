package io.github.ryu200o.eduworkshop.iam.internal.adapter.inbound.security;

import io.github.ryu200o.eduworkshop.iam.internal.application.exception.InvalidTokenException;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.inbound.parameter.IamSecurityParameters;
import io.github.ryu200o.eduworkshop.iam.internal.application.port.outbound.AccessTokenCodec;
import io.github.ryu200o.eduworkshop.shared.security.AuthenticatedPrincipal;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * HS256 JWT codec backed by Nimbus (plan §2.2, §5). Claims: {@code sub}=opaque userId,
 * {@code email}, {@code roles} (global RBAC names), {@code mcp}, plus {@code iat}/{@code exp}.
 * The decoder validates signature and expiry on every call, so an expired or tampered token surfaces
 * as {@link InvalidTokenException}. The shared secret is a base64-encoded key of at least 32 bytes
 * from {@code app.iam.jwt.secret}.
 */
@Component
class NimbusJwtAccessTokenCodec implements AccessTokenCodec {

    private final NimbusJwtEncoder encoder;
    private final NimbusJwtDecoder decoder;

    NimbusJwtAccessTokenCodec(IamSecurityParameters parameters) {
        byte[] keyBytes = Base64.getDecoder().decode(parameters.jwtSecret());
        SecretKey key = new SecretKeySpec(keyBytes, "HmacSHA256");
        this.encoder = NimbusJwtEncoder.withSecretKey(key).algorithm(MacAlgorithm.HS256).build();
        this.decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Override
    public String encode(AccessTokenClaims claims) {
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        JwtClaimsSet jwtClaims = JwtClaimsSet.builder()
                .subject(claims.userId().toString())
                .issuedAt(claims.issuedAt())
                .expiresAt(claims.expiresAt())
                .claim("email", claims.email())
                .claim("roles", claims.roles())
                .claim("mcp", claims.mustChangePassword())
                .build();
        Jwt jwt = encoder.encode(JwtEncoderParameters.from(header, jwtClaims));
        return jwt.getTokenValue();
    }

    @Override
    public AuthenticatedPrincipal decode(String token) {
        try {
            Jwt jwt = decoder.decode(token);
            UUID userId = UUID.fromString(jwt.getSubject());
            String email = jwt.getClaimAsString("email");
            List<String> roles = jwt.getClaimAsStringList("roles");
            boolean mcp = Boolean.TRUE.equals(jwt.getClaimAsBoolean("mcp"));
            return new AuthenticatedPrincipal(
                    userId,
                    email,
                    roles == null ? java.util.Set.of() : new java.util.LinkedHashSet<>(roles),
                    mcp
            );
        } catch (org.springframework.security.oauth2.jwt.JwtException | IllegalArgumentException ex) {
            throw new InvalidTokenException();
        }
    }
}
