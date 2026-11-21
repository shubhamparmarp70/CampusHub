package com.campushub.security;
/** Principal placed in the SecurityContext. Controllers read the caller id from here, never from request bodies. */
public record AuthUser(Long id, String email) {}
