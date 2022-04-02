package dev.plex.request;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface MappingHeaders
{
    String[] headers();
}
