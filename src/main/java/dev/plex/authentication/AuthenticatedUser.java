package dev.plex.authentication;

import com.google.common.collect.Lists;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.ZonedDateTime;
import java.util.LinkedList;

@Data
@Accessors(fluent = true)
public class AuthenticatedUser
{
    private final String ip;
    private final ZonedDateTime lastAuthenticated;
    private final LinkedList<String> roles = Lists.newLinkedList();
    private final UserType userType = UserType.UNKNOWN;
}
