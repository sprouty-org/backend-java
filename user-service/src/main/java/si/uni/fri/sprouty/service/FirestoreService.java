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

    private Firestore getDb() {
        return FirestoreClient.getFirestore(FirebaseApp.getInstance(), "sprouty-firestore");
    }

    public void saveUser(User user) throws Exception {
        Firestore db = getDb();
        DocumentReference docRef = db.collection("users").document(user.getUid());

        if (!docRef.get().get().exists()) {
            System.out.println("Creating new user: " + user.getUid());
            docRef.set(user).get();
        } else {
            updateFcmToken(user.getUid(), user.getFcmToken());
        }
    }

    public void updateFcmToken(String uid, String fcmToken) {
        if (fcmToken == null || fcmToken.isEmpty()) return;

        try {
            Firestore db = getDb();
            DocumentReference docRef = db.collection("users").document(uid);

            Map<String, Object> updates = new HashMap<>();
            updates.put("fcmToken", fcmToken);

            docRef.update(updates).get();
            System.out.println("FCM Token updated for: " + uid);
        } catch (Exception e) {
            System.err.println("Error updating FCM token: " + e.getMessage());
        }
    }

    public void deleteUserRecord(String uid) throws Exception {
        Firestore db = getDb();
        db.collection("users").document(uid).delete().get();
        System.out.println("Firestore record deleted for user: " + uid);
    }
}