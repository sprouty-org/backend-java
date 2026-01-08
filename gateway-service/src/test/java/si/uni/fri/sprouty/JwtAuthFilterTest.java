package si.uni.fri.sprouty;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import si.uni.fri.sprouty.security.JwtAuthFilter;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

import static org.mockito.Mockito.*;

class JwtAuthFilterTest {

    private JwtAuthFilter filter;
    private SecretKey key;

    @BeforeEach
    void setUp() throws Exception {
        filter = new JwtAuthFilter();
        var field = JwtAuthFilter.class.getDeclaredField("secretKey");
        field.setAccessible(true);
        String secret = "my-super-secret-test-key-that-is-at-least-32-characters-long";
        field.set(filter, secret);
        filter.init();

        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void shouldPassPublicEndpointWithoutToken() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/users/login").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
    }

    @Test
    void shouldAddHeaderForValidToken() {
        String token = Jwts.builder()
                .subject("user123")
                .signWith(key)
                .compact();

        MockServerHttpRequest request = MockServerHttpRequest.get("/plants/my-garden")
                .header("Authorization", "Bearer " + token)
                .build();

        ServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain).filter(argThat(ex ->
                "user123".equals(ex.getRequest().getHeaders().getFirst("X-User-Id"))
        ));
    }
}