package com.songs.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class ApiKeyFilter extends OncePerRequestFilter {

    // This reaches into application.properties and grabs your secret key
    @Value("${api.secret.key}")
    private String expectedApiKey;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // 1. Allow CORS preflight requests (OPTIONS) to pass through untouched. 
        // (React will need this later to verify the connection).
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        // 2. Grab the key the user sent in the request header
        String providedApiKey = request.getHeader("X-API-KEY");

        // 3. Compare it securely
        if (expectedApiKey.equals(providedApiKey)) {
            // The keys match! Let the request through to the Controller
            filterChain.doFilter(request, response);
        } else {
            // Wrong or missing key. Block the request with a 401 Unauthorized status!
            System.err.println("inside else block - Unauthorized access attempt: Invalid or missing API key.");
            response.setHeader("Access-Control-Allow-Origin", "http://localhost:5173"); // or "*"
            response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            response.setHeader("Access-Control-Allow-Headers", "Content-Type, X-API-KEY, Authorization");
            response.setHeader("Access-Control-Allow-Credentials", "true");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Unauthorized: Invalid or missing password. Please try again...");
        }
    }
}