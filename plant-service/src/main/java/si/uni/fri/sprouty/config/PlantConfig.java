package si.uni.fri.sprouty.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.RestTemplate;
import java.io.IOException;

@Slf4j
@Configuration
@Profile("!test")
public class PlantConfig {

    @Value("${firebase.storage-bucket:sprouty-plantapp.firebasestorage.app}")
    private String storageBucket;

    @Bean
    public FirebaseApp firebaseApp() throws IOException {
        if (FirebaseApp.getApps().isEmpty()) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.getApplicationDefault())
                    .setStorageBucket(storageBucket)
                    .build();
            return FirebaseApp.initializeApp(options);
        }
        return FirebaseApp.getInstance();
    }

    @Bean
    @Primary
    public Firestore getFirestore(FirebaseApp app) {
        return FirestoreClient.getFirestore(app, "sprouty-firestore");
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}