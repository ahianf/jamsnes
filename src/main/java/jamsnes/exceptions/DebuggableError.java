package jamsnes.exceptions;

public class DebuggableError extends RuntimeException {
    public DebuggableError(String message) {
        super(message);
    }

    public DebuggableError(String message, Throwable cause) {
        super(message, cause);
    }
}
