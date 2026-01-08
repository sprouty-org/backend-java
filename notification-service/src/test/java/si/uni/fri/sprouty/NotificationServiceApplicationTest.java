package si.uni.fri.sprouty;

import com.google.firebase.messaging.FirebaseMessaging;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import com.google.cloud.firestore.Firestore;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestTemplate;

@SpringBootTest
@ActiveProfiles("test")
class NotificationServiceApplicationTests {
    @MockBean
    private Firestore firestore;

    @MockBean
    private FirebaseMessaging firebaseMessaging;

    @Test
    void contextLoads() {
    }
}