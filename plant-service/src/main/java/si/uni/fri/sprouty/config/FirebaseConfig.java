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
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.io.IOException;

@Slf4j
@Configuration
public class FirebaseConfig {

    @Value("${firebase.storage-bucket:sprouty-plantapp.firebasestorage.app}")
    private String storageBucket;

    @PostConstruct
    public void initializeFirebase() {
        try {
            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.getApplicationDefault())
                        .setStorageBucket(storageBucket)
                        .build();

                FirebaseApp.initializeApp(options);
                log.info("Firebase Application has been successfully initialized.");
            }
        } catch (IOException e) {
            log.error("Failed to initialize Firebase!", e);
            throw new RuntimeException(e);
        }
    }

    @Bean
    @Primary
    public Firestore getFirestore() {
        return FirestoreClient.getFirestore(FirebaseApp.getInstance(), "sprouty-firestore");
    }


    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}