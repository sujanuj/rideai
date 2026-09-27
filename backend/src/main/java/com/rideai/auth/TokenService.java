package com.rideai.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Issues signed JWTs. Validation happens in Spring Security's resource server filter. */
@Service
public class TokenService {

    private final JwtEncoder encoder;
    private final Duration ttl;

    public TokenService(JwtEncoder encoder, @Value("${rideai.jwt.ttl:24h}") Duration ttl) {
        this.encoder = encoder;
        this.ttl = ttl;
    }

    public String issue(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer("rideai")
            .subject(String.valueOf(user.getId()))
            .issuedAt(now)
            .expiresAt(now.plus(ttl))
            .claim("roles", List.of(user.getRole().name()))
            .claim("name", user.getFullName())
            .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
