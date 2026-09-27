package com.rideai.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rideai.auth.AuthDtos.AuthResponse;
import com.rideai.auth.AuthDtos.LoginRequest;
import com.rideai.auth.AuthDtos.RegisterRequest;
import com.rideai.auth.AuthDtos.UserResponse;
import com.rideai.common.ApiException;
import com.rideai.drivers.Driver;
import com.rideai.drivers.DriverRepository;

@Service
public class AuthService {

    private final UserRepository users;
    private final DriverRepository drivers;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;

    public AuthService(UserRepository users, DriverRepository drivers,
                       PasswordEncoder passwordEncoder, TokenService tokens) {
        this.users = users;
        this.drivers = drivers;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        String email = req.email().trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("EMAIL_TAKEN", "An account with this email already exists");
        }
        if (req.role() == Role.DRIVER && (isBlank(req.vehicle()) || isBlank(req.plate()))) {
            throw ApiException.badRequest("VEHICLE_REQUIRED", "Drivers need a vehicle and a plate number");
        }

        User user = users.save(new User(email, passwordEncoder.encode(req.password()), req.fullName().trim(), req.role()));
        if (user.getRole() == Role.DRIVER) {
            drivers.save(new Driver(user.getId(), req.vehicle().trim(), req.plate().trim().toUpperCase()));
        }
        return new AuthResponse(tokens.issue(user), UserResponse.from(user));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        User user = users.findByEmailIgnoreCase(req.email().trim())
            .filter(u -> passwordEncoder.matches(req.password(), u.getPasswordHash()))
            // Same message for "no such user" and "wrong password", so attackers can't probe emails.
            .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", "Wrong email or password"));
        return new AuthResponse(tokens.issue(user), UserResponse.from(user));
    }

    @Transactional(readOnly = true)
    public UserResponse me(long userId) {
        return users.findById(userId).map(UserResponse::from).orElseThrow(() -> ApiException.notFound("User"));
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
