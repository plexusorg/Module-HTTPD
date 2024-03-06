package dev.plex.authentication;

import com.google.common.collect.Lists;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.ZonedDateTime;
import java.util.LinkedList;
import java.util.List;

/**
 * @author Taah
 * @since 6:37 PM [03-05-2024]
 */

@Data
@Accessors(fluent = true)
public class AuthenticatedUser
{
    private final String ip;
    private final ZonedDateTime lastAuthenticated;
    private final LinkedList<String> roles = Lists.newLinkedList();
    private final UserType userType = UserType.UNKNOWN;
}
