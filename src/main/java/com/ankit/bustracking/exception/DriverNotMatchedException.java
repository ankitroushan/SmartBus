package com.ankit.bustracking.exception;

public class DriverNotMatchedException extends RuntimeException {
    public DriverNotMatchedException(String message) {
        super(message);
    }
}
