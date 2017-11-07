package net.talpidae.base.resource;

import net.talpidae.base.util.auth.Credentials;

import java.util.UUID;


public interface CredentialValidator
{
    /**
     * Checks the specified credentials and clears them.
     *
     * @param credentials The credentials to check.
     * @return The login's valid ID if the Credentials are valid (username and password match an existing user), null if not
     */
    UUID validate(Credentials credentials);
}