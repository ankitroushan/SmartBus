package com.ankit.bustracking.exception;

public class BusNotActiveException extends RuntimeException {
    public BusNotActiveException(String message) {
        super(message);
    }
}
