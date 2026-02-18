package com.codeops.vault.exception;

/**
 * Thrown when a request fails business-rule validation in the Vault.
 *
 * <p>Mapped to HTTP 400 by the
 * {@link com.codeops.vault.config.GlobalExceptionHandler}.</p>
 */
public class ValidationException extends CodeOpsVaultException {

    /**
     * Creates a validation exception with the specified detail message.
     *
     * @param message the detail message describing the validation failure
     */
    public ValidationException(String message) {
        super(message);
    }
}
