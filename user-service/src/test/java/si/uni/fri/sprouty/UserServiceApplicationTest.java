package si.uni.fri.sprouty;

import com.google.cloud.firestore.Firestore;
import com.google.firebase.auth.FirebaseAuth;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestTemplate;

@SpringBootTest
@ActiveProfiles("test")
class UserServiceApplicationTests {

    @MockBean
    private FirebaseAuth firebaseAuth;

    @MockBean
    private Firestore firestore;

    @MockBean
    private RestTemplate restTemplate;

    @Test
    void contextLoads() {
    }
}