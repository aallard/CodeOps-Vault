package com.codeops.vault.exception;

/**
 * Thrown when a user lacks permission to perform a Vault operation.
 *
 * <p>Mapped to HTTP 403 by the
 * {@link com.codeops.vault.config.GlobalExceptionHandler}.</p>
 */
public class AuthorizationException extends CodeOpsVaultException {

    /**
     * Creates an authorization exception with the specified detail message.
     *
     * @param message the detail message describing the authorization failure
     */
    public AuthorizationException(String message) {
        super(message);
    }
}
