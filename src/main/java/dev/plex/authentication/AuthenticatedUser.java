package dev.plex.authentication;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.Instant;

@Data
@Accessors(fluent = true)
public class AuthenticatedUser
{
    private final int userId;
    private final String username;
    private final boolean staff;
    private final UserType userType;
    private final String accessToken;
    private final Instant accessTokenExpiresAt;
    private final Instant authenticatedAt;
}
