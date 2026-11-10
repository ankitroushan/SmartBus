package com.ankit.bustracking.exception;

public class BusNotFoundException extends RuntimeException {

    public BusNotFoundException() {
        super("Bus does not exist");
    }
}