package si.uni.fri.sprouty;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("test")
// This provides the missing piece the JwtAuthFilter is looking for
@TestPropertySource(properties = {
        "jwt.secret=my-super-secret-test-key-that-is-at-least-32-characters-long"
})
class GatewayServiceApplicationTests {

    @Test
    void contextLoads() {
        // The context will now load because the placeholder is resolved
    }
}