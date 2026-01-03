package si.uni.fri.sprouty.service;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.cloud.FirestoreClient;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import org.springframework.stereotype.Service;
import si.uni.fri.sprouty.dto.NotificationRequest;

@Service
public class FcmService {

    public void sendPush(NotificationRequest request) {
        try {
            // Get the default Firestore instance (standard for most projects)
            Firestore db = FirestoreClient.getFirestore(FirebaseApp.getInstance("sprouty-firestore"));

            // Retrieve token with a safer retrieval method
            DocumentSnapshot userDoc = db.collection("users")
                    .document(request.getUserId())
                    .get()
                    .get(); // In a high-load scenario, consider making this async

            String token = userDoc.getString("fcmToken");

            if (token == null || token.isEmpty()) {
                System.err.println("Aborting push: No FCM token found for user ID: " + request.getUserId());
                return;
            }

            // Build the message
            Message.Builder messageBuilder = Message.builder()
                    .setToken(token)
                    // ALWAYS include the refresh action so the UI stays in sync
                    .putData("action", "REFRESH_PLANTS")
                    .putData("userId", request.getUserId());

            // Add visible notification only if it's an Alert (not a silent sync)
            if (request.getTitle() != null && !request.getTitle().isBlank()) {
                messageBuilder.setNotification(Notification.builder()
                        .setTitle(request.getTitle())
                        .setBody(request.getBody())
                        .build());
            }

            // Send to Firebase
            String response = FirebaseMessaging.getInstance().send(messageBuilder.build());
            System.out.println("Successfully sent FCM to user " + request.getUserId() + ": " + response);

        } catch (Exception e) {
            System.err.println("FCM Exception for User [" + request.getUserId() + "]: " + e.getMessage());
        }
    }
}