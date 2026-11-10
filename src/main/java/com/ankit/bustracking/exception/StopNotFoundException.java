package com.ankit.bustracking.exception;

public class StopNotFoundException extends RuntimeException {

    public StopNotFoundException() {
        super("Stop does not exist");
    }
}
