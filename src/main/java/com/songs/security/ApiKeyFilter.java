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
        if (expectedApiKey != null && expectedApiKey.equals(providedApiKey)) {
            // The keys match! Let the request through
            filterChain.doFilter(request, response);
        } else {
            // Wrong or missing key - Set dynamic CORS header so browser displays 401 instead of CORS error
            String origin = request.getHeader("Origin");
            if (origin != null) {
                response.setHeader("Access-Control-Allow-Origin", origin);
                response.setHeader("Access-Control-Allow-Credentials", "true");
            }
            response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            response.setHeader("Access-Control-Allow-Headers", "Content-Type, X-API-KEY, Authorization");
            
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Unauthorized: Invalid or missing password. Please try again...");
        }
    }
}