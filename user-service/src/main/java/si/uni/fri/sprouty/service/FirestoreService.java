package si.uni.fri.sprouty.service;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.cloud.FirestoreClient;
import org.springframework.stereotype.Service;
import si.uni.fri.sprouty.dto.User;

import java.util.HashMap;
import java.util.Map;

@Service
public class FirestoreService {

    private static final String FIRESTORE_DB_NAME = "sprouty-firestore";
    private final String USERS_COLLECTION = "users";

    private Firestore getDb() {
        return FirestoreClient.getFirestore(FirebaseApp.getInstance(), FIRESTORE_DB_NAME);
    }

    public void saveUser(User user) throws Exception {
        Firestore db = getDb();
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
            Firestore db = getDb();
            DocumentReference docRef = db.collection(USERS_COLLECTION).document(uid);

            Map<String, Object> updates = new HashMap<>();
            updates.put("fcmToken", fcmToken);

            docRef.update(updates).get();
        } catch (Exception e) {
            System.err.println("Error updating FCM token: " + e.getMessage());
        }
    }

    public void deleteUserRecord(String uid) throws Exception {
        Firestore db = getDb();
        db.collection(USERS_COLLECTION).document(uid).delete().get();
    }
}