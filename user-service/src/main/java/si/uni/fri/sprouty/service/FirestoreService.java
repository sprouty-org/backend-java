package si.uni.fri.sprouty.service;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import org.springframework.stereotype.Service;
import si.uni.fri.sprouty.dto.User;

import java.util.HashMap;
import java.util.Map;

@Service
public class FirestoreService {

    private final String USERS_COLLECTION = "users";
    private final Firestore db;

    public FirestoreService(Firestore db) {
        this.db = db;
    }

    public void saveUser(User user) throws Exception {
        DocumentReference docRef = db.collection(USERS_COLLECTION).document(user.getUid());

        if (!docRef.get().get().exists()) {
            // User does not exist, create new record
            docRef.set(user).get();
        } else {
            // User exists, update FCM token
            updateFcmToken(user.getUid(), user.getFcmToken());
        }
    }

    public void updateFcmToken(String uid, String fcmToken) {
        if (fcmToken == null || fcmToken.isEmpty()) return;

        try {
            DocumentReference docRef = db.collection(USERS_COLLECTION).document(uid);

            Map<String, Object> updates = new HashMap<>();
            updates.put("fcmToken", fcmToken);

            docRef.update(updates).get();
        } catch (Exception e) {
            System.err.println("Error updating FCM token: " + e.getMessage());
        }
    }

    public void deleteUserRecord(String uid) throws Exception {
        db.collection(USERS_COLLECTION).document(uid).delete().get();
    }
}