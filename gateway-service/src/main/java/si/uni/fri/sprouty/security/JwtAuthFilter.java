package si.uni.fri.sprouty.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    // Public endpoints that dont require authentication
    private final List<String> publicEndpoints = List.of(
            "/users/login",
            "/users/register",
            "/sensors",
            "/swagger-ui",
            "/v3/api-docs",
            "/users/v3/api-docs",
            "/plants/v3/api-docs",
            "/sensors/v3/api-docs",
            "/notifications/v3/api-docs",
            "/health",
            "/notifications/health",
            "/sensors/health",
            "/users/health",
            "/plants/health"
    );

    @Value("${jwt.secret:}")
    private String secretKey;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // Allow public endpoints
        if (publicEndpoints.stream().anyMatch(path::startsWith)) {
            return chain.filter(exchange);
        }

        // Allow CORS preflight
        if (exchange.getRequest().getMethod().name().equals("OPTIONS")) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return unauthorized(exchange);
        }

        String token = authHeader.substring(7);
        try {
            if (secretKey == null || secretKey.isBlank()) {
                return unauthorized(exchange);
            }

            byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
            var signingKey = Keys.hmacShaKeyFor(keyBytes);
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            // Forward the subject (UID) as a header
            String subject = claims.getSubject();
            if (subject != null && !subject.isBlank()) {
                exchange = exchange.mutate()
                        .request(r -> r.header("X-User-Id", subject))
                        .build();
            }
        } catch (JwtException | IllegalArgumentException e) {
            return unauthorized(exchange);
        }

        return chain.filter(exchange);
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.TEXT_PLAIN);
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
