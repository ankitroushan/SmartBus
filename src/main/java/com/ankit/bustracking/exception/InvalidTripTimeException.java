package com.ankit.bustracking.exception;

public class InvalidTripTimeException extends RuntimeException {
    public InvalidTripTimeException(String message) {
        super(message);
    }
}
