package ru.auth.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ru.auth.util.JwtUtil;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.lang.NonNull;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.security.web.util.matcher.RegexRequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;


@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    private final UserDetailsService userDetailsService;

    private final RequestMatcher skipMatcher = new OrRequestMatcher(
            new RegexRequestMatcher("^/.*$", "OPTIONS"),
            new RegexRequestMatcher("^/auth/login$", "POST"),
            new RegexRequestMatcher("^/auth/signup$", "POST"),
            new RegexRequestMatcher("^/auth/refresh$", "POST"),
            new RegexRequestMatcher("^/auth/logout$", "POST"),
            new RegexRequestMatcher("^/v3/api-docs.*$", "GET"),
            new RegexRequestMatcher("^/swagger-ui.*$", "GET"),
            new RegexRequestMatcher("^/\\.well-known/jwks\\.json$", "GET")
    );

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain chain)
            throws ServletException, IOException {
        final String method = request.getMethod();
        final String path = request.getRequestURI();
        log.debug("JwtAuthenticationFilter: incoming request {} {}", method, path);
        final String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        String username = null;
        String jwt = null;

        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            jwt = authorizationHeader.substring(7);
            username = jwtUtil.extractUsername(jwt);
            log.debug("JwtAuthenticationFilter: bearer token detected, username={}", username);
        } else {
            log.debug("JwtAuthenticationFilter: no bearer token on request");
        }

        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            boolean valid = jwtUtil.validateToken(jwt, userDetails);
            log.debug("JwtAuthenticationFilter: token validation result for {} => {}", username, valid);
            if (valid) {
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.debug("JwtAuthenticationFilter: authentication set for {}", username);
            }
        }
        chain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) throws ServletException {
        boolean skip = skipMatcher.matches(request);
        if (skip) {
            log.debug("JwtAuthenticationFilter: skipping for {} {}", request.getMethod(), request.getRequestURI());
        }
        return skip;
    }
}
