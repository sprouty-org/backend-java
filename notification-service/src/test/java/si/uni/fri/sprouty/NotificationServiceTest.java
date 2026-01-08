package si.uni.fri.sprouty;

import com.google.api.core.ApiFutures;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import si.uni.fri.sprouty.dto.NotificationRequest;
import si.uni.fri.sprouty.service.NotificationService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private Firestore db;
    @Mock private FirebaseMessaging fcm;
    @Mock private CollectionReference collectionReference;
    @Mock private DocumentReference documentReference;
    @Mock private DocumentSnapshot documentSnapshot;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(fcm, db);
    }

    @Test
    void sendPush_ShouldSendFullNotification_WhenTitleAndBodyPresent() throws Exception {
        // Arrange
        String userId = "user123";
        NotificationRequest request = new NotificationRequest();
        request.setUserId(userId);
        request.setTitle("Water Me!");
        request.setBody("I am thirsty.");

        mockFirestoreUser(userId, "mock-fcm-token");
        when(fcm.send(any(Message.class))).thenReturn("msg_id_123");

        // Act
        notificationService.sendPush(request);

        // Assert
        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(fcm).send(messageCaptor.capture());

        Message captured = messageCaptor.getValue();
        assertNotNull(captured);
    }

    @Test
    void sendPush_ShouldSendSilentNotification_WhenTitleIsMissing() throws Exception {
        // Arrange
        String userId = "user123";
        NotificationRequest request = new NotificationRequest();
        request.setUserId(userId);

        mockFirestoreUser(userId, "mock-fcm-token");

        // Act
        notificationService.sendPush(request);

        // Assert
        verify(fcm, times(1)).send(any(Message.class));
    }

    @Test
    void sendPush_ShouldAbort_WhenUserNotFound() throws Exception {
        // Arrange
        String userId = "missing_user";
        when(db.collection("users")).thenReturn(collectionReference);
        when(collectionReference.document(userId)).thenReturn(documentReference);
        when(documentReference.get()).thenReturn(ApiFutures.immediateFuture(documentSnapshot));
        when(documentSnapshot.exists()).thenReturn(false);

        NotificationRequest request = new NotificationRequest();
        request.setUserId(userId);

        // Act
        notificationService.sendPush(request);

        // Assert
        verify(fcm, never()).send(any());
    }

    private void mockFirestoreUser(String userId, String token) throws Exception {
        when(db.collection("users")).thenReturn(collectionReference);
        when(collectionReference.document(userId)).thenReturn(documentReference);
        when(documentReference.get()).thenReturn(ApiFutures.immediateFuture(documentSnapshot));
        when(documentSnapshot.exists()).thenReturn(true);
        when(documentSnapshot.getString("fcmToken")).thenReturn(token);
    }
}