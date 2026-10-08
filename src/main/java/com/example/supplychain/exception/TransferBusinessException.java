package com.example.supplychain.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class TransferBusinessException extends RuntimeException {

    public TransferBusinessException(String message) {
        super(message);
    }
}