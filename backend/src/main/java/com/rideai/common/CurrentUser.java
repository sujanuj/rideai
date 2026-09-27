package com.rideai.common;

import org.springframework.security.oauth2.jwt.Jwt;

/** Reads the logged-in user's id from the JWT (the token's "sub" claim is the user id). */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static long id(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }
}
