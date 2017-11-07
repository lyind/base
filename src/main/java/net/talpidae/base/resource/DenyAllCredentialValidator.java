package net.talpidae.base.resource;

import net.talpidae.base.util.auth.Credentials;

import java.util.UUID;


/**
 * Credentials validator that knows about no users at all (no one will be able to log in).
 */
public class DenyAllCredentialValidator implements CredentialValidator
{
    @Override
    public UUID validate(Credentials credentials)
    {
        return null;
    }
}
