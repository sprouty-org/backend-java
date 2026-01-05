package si.uni.fri.sprouty.service;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.cloud.FirestoreClient;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import si.uni.fri.sprouty.dto.NotificationRequest;

@Service
@RequiredArgsConstructor
public class FcmService {

    private final FirebaseMessaging fcm;
    private static final String FIRESTORE_DB_NAME = "sprouty-firestore";

    private Firestore getDb() {
        return FirestoreClient.getFirestore(FirebaseApp.getInstance(), FIRESTORE_DB_NAME);
    }

    public void sendPush(NotificationRequest request) {
        Firestore db = getDb();
        try {
            DocumentSnapshot userDoc = db.collection("users")
                    .document(request.getUserId())
                    .get()
                    .get();

            if (!userDoc.exists()) {
                System.err.println("User document not found in Firestore for ID: " + request.getUserId());
                return;
            }

            String token = userDoc.getString("fcmToken");

            if (token == null || token.isEmpty()) {
                System.err.println("Aborting push: No FCM token found for user: " + request.getUserId());
                return;
            }

            // Build the message with both Notification (for the popup) and Data (for the app logic)
            Message message = Message.builder()
                    .setToken(token)
                    .setNotification(Notification.builder()
                            .setTitle(request.getTitle())
                            .setBody(request.getBody())
                            .build())
                    .putData("action", "REFRESH_PLANTS")
                    .putData("userId", request.getUserId())
                    .build();

            String response = fcm.send(message);
            System.out.println("Successfully sent FCM: " + response);

        } catch (Exception e) {
            System.err.println("FCM Exception: " + e.getMessage());
        }
    }
}