package com.songs.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api") // Make sure this matches your VITE_API_URL path!
public class AuthController {

    @PostMapping("/login")
    public ResponseEntity<?> login() {

        // We just return a 200 OK with a nice JSON message.
        return ResponseEntity.ok(Map.of(
            "status", "SUCCESS",
            "message", "Authentication successful!"
        ));
    }
}