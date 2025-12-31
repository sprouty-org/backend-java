package si.uni.fri.sprouty.service;

import com.google.firebase.FirebaseApp;
import com.google.firebase.cloud.FirestoreClient;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import org.springframework.stereotype.Service;
import si.uni.fri.sprouty.model.NotificationRequest;

@Service
public class FcmService {
    public void sendPush(NotificationRequest request) {
        try {
            String token = FirestoreClient.getFirestore(FirebaseApp.getInstance(), "sprouty-firestore")
                    .collection("users").document(request.getUserId())
                    .get().get().getString("fcmToken");

            if (token == null) {
                System.out.println("No FCM token found for user: " + request.getUserId());
                return;
            }

            Message.Builder messageBuilder = Message.builder()
                    .setToken(token)
                    // This "data" payload triggers triggerBackgroundSync() in Android
                    .putData("action", "REFRESH_PLANTS");

            // Only add a visible notification if the request has a title (for alerts)
            if (request.getTitle() != null && !request.getTitle().isEmpty()) {
                messageBuilder.setNotification(Notification.builder()
                        .setTitle(request.getTitle())
                        .setBody(request.getBody())
                        .build());
            }

            String response = FirebaseMessaging.getInstance().send(messageBuilder.build());
            System.out.println("FCM Sent (Action: REFRESH_PLANTS): " + response);
        } catch (Exception e) {
            System.err.println("Error sending push notification: " + e.getMessage());
        }
    }
}