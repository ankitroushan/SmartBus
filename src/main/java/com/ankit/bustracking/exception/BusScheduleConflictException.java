package com.ankit.bustracking.exception;

public class BusScheduleConflictException extends RuntimeException {
    public BusScheduleConflictException(String message) {
        super(message);
    }
}
