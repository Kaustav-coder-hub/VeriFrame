package com.mediaprovenance.blockchain;

public class TransientBlockchainException extends RuntimeException {

    public TransientBlockchainException(String message, Throwable cause) {
        super(message, cause);
    }
}