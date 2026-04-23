package com.pm.content_platform_backend.auth.security;


import com.pm.content_platform_backend.auth.service.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.Principal;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private static final String HEADER_NAME = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String authHeader = request.getHeader(HEADER_NAME);

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        try {
            Claims claims = jwtService.parseToken(token);
            UserPrincipal principal = new UserPrincipal(
                    Long.parseLong(claims.getSubject()),
                    claims.get("username", String.class),
                    claims.get("role", String.class)
            );
            var auth = new UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + principal.role()))
            );
            SecurityContextHolder.getContext().setAuthentication(auth);
        } catch (JwtException ex) {
            // Invalid/expired/malformed token — leave the context unauthenticated
            // and let the security config decide whether the endpoint requires auth.
        }

        filterChain.doFilter(request, response);
    }
}
