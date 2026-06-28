package jamsnes.exceptions;

public class InvalidRom extends RuntimeException {
    public InvalidRom(String message) {
        super(message);
    }

    public InvalidRom(String message, Throwable cause) {
        super(message, cause);
    }
}
