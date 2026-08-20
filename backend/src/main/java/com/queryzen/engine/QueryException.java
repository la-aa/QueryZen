package com.queryzen.engine;

public class QueryException extends RuntimeException {
    public QueryException(String message, Throwable cause) {
        super(message, cause);
    }
}