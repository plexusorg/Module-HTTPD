package dev.plex.authentication;

public class AuthenticationException extends Exception
{
    public AuthenticationException(String message)
    {
        super(message);
    }

    public AuthenticationException(String message, Throwable cause)
    {
        super(message, cause);
    }
}
