package com.croh.common;

public class AccountBlacklistedException extends RuntimeException {

    public AccountBlacklistedException(String message) {
        super(message);
    }
}
