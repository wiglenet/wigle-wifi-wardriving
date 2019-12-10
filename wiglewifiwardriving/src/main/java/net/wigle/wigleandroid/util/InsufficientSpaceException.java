package net.wigle.wigleandroid.util;

public class InsufficientSpaceException extends Exception {
    public InsufficientSpaceException() {
        super();
    }

    public InsufficientSpaceException(String message) {
        super(message);
    }

    public InsufficientSpaceException(String message, Throwable cause) {
        super(message, cause);
    }
}
