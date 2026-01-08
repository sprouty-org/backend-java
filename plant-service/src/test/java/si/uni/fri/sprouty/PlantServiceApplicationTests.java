package si.uni.fri.sprouty;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import com.google.cloud.firestore.Firestore;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestTemplate;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "openai.api.key=mock-key",
        "plantnet.api.key=mock-key"
})
class PlantServiceApplicationTests {
    @MockBean private Firestore firestore;
    @MockBean private RestTemplate restTemplate;

    @Test
    void contextLoads() {}
}