package dev.plex.authentication;

import lombok.experimental.Accessors;

import java.time.Instant;

@Accessors(fluent = true)
public record AuthenticatedUser(int userId, String username, boolean staff, UserType userType, String accessToken,
                                Instant accessTokenExpiresAt, Instant authenticatedAt) {
}
