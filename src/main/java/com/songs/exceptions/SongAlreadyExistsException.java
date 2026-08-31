package com.songs.exceptions;

public class SongAlreadyExistsException extends RuntimeException {
    
    public SongAlreadyExistsException(String message) {
        super(message);
    }
}