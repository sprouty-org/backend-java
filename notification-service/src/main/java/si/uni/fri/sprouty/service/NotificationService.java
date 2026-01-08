package si.uni.fri.sprouty.service;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import si.uni.fri.sprouty.dto.NotificationRequest;

@Service
public class NotificationService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);
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
                logger.warn("Notification failed: User {} does not exist", request.getUserId());
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Target user not found.");
            }

            String token = userDoc.getString("fcmToken");

            if (token == null || token.isBlank()) {
                logger.info("Skipping notification: User {} has no FCM token", request.getUserId());
                return;
            }

            // Build the FCM Message
            Message.Builder messageBuilder = Message.builder()
                    .setToken(token);

            // Add Custom Data Payload (Essential for silent syncs)
            messageBuilder.putData("action", "REFRESH_PLANTS");
            messageBuilder.putData("userId", request.getUserId());

            if (request.getData() != null && !request.getData().isEmpty()) {
                messageBuilder.putAllData(request.getData());
            }

            // Attach Notification UI if present
            if (isDisplayable(request)) {
                Notification notification = Notification.builder()
                        .setTitle(request.getTitle())
                        .setBody(request.getBody())
                        .build();
                messageBuilder.setNotification(notification);
            }

            String response = firebaseMessaging.send(messageBuilder.build());
            logger.info("Successfully sent FCM message for user {}. ID: {}", request.getUserId(), response);

        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            logger.error("FCM Delivery Failure for user {}: ", request.getUserId(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to deliver push notification.");
        }
    }

    private boolean isDisplayable(NotificationRequest request) {
        return request.getTitle() != null && !request.getTitle().isBlank() &&
                request.getBody() != null && !request.getBody().isBlank();
    }
}