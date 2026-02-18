package com.codeops.vault.exception;

/**
 * Base runtime exception for the CodeOps Vault service.
 *
 * <p>All application-specific exceptions extend this class, enabling centralized
 * handling in the {@link com.codeops.vault.config.GlobalExceptionHandler}.</p>
 */
public class CodeOpsVaultException extends RuntimeException {

    /**
     * Creates a new exception with the specified detail message.
     *
     * @param message the detail message
     */
    public CodeOpsVaultException(String message) {
        super(message);
    }

    /**
     * Creates a new exception with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause   the underlying cause
     */
    public CodeOpsVaultException(String message, Throwable cause) {
        super(message, cause);
    }
}
