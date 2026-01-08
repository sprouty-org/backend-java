package si.uni.fri.sprouty.service;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import org.springframework.stereotype.Service;
import si.uni.fri.sprouty.dto.NotificationRequest;

@Service
public class NotificationService {

    private final FirebaseMessaging firebaseMessaging;
    private final Firestore db;

    public NotificationService(FirebaseMessaging firebaseMessaging, Firestore db) {
        this.firebaseMessaging = firebaseMessaging;
        this.db = db;
    }

    public void sendPush(NotificationRequest request) {
        try {
            DocumentSnapshot userDoc = db.collection("users")
                    .document(request.getUserId())
                    .get()
                    .get();

            if (!userDoc.exists()) {
                return;
            }

            String token = userDoc.getString("fcmToken");

            if (token == null || token.isEmpty()) {
                return;
            }

            Message.Builder messageBuilder = Message.builder()
                    .setToken(token)
                    .putData("action", "REFRESH_PLANTS")
                    .putData("userId", request.getUserId());

            if (request.getTitle() != null && request.getBody() != null && !request.getTitle().isEmpty() && !request.getBody().isEmpty()) {
                Notification notification = Notification.builder()
                        .setTitle(request.getTitle())
                        .setBody(request.getBody())
                        .build();
                messageBuilder.setNotification(notification);
            }

            firebaseMessaging.send(messageBuilder.build());

        } catch (Exception e) {
            System.err.println("FCM Exception: " + e.getMessage());
        }
    }
}