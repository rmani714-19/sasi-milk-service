package com.sasimilkservice.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validates the {@code Authorization: Bearer <jwt>} header and populates the
 * {@code SecurityContext} with an {@link AppUserPrincipal} whose authorities are
 * the resolved permission codes. The {@code ALL} sentinel is expanded to every
 * concrete permission here so {@code @PreAuthorize("hasAuthority('MANAGE_RATES')")}
 * works for ADMIN without special-casing.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                Claims claims = jwtService.parse(header.substring(BEARER.length())).getPayload();
                long userId = Long.parseLong(claims.getSubject());
                String username = claims.get("username", String.class);

                @SuppressWarnings("unchecked")
                List<String> raw = claims.get("perms", List.class);
                Set<String> perms = new HashSet<>(raw == null ? List.of() : raw);
                if (perms.contains(Permission.ALL_CODE)) {
                    perms.addAll(Permission.allConcrete()); // keep ALL too, so hasAuthority('ALL') works
                }

                var authorities = perms.stream()
                        .map(SimpleGrantedAuthority::new)
                        .toList();
                var principal = new AppUserPrincipal(userId, username, Set.copyOf(perms));
                var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException | NumberFormatException ex) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
