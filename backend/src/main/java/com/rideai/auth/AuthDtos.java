package com.rideai.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request and response shapes for /api/auth. Records give us immutable DTOs for free. */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 72, message = "must be 8 to 72 characters") String password,
        @NotBlank @Size(max = 80) String fullName,
        @NotNull Role role,
        // Only for drivers
        @Size(max = 60) String vehicle,
        @Size(max = 15) String plate) {
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
    }

    public record UserResponse(Long id, String email, String fullName, Role role) {

        static UserResponse from(User user) {
            return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRole());
        }
    }

    public record AuthResponse(String token, UserResponse user) {
    }
}
