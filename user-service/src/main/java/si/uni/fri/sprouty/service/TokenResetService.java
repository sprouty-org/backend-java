package si.uni.fri.sprouty.service;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.google.firebase.cloud.FirestoreClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TokenResetService {

    /**
     * Resets tokens for all users at 00:00 (Midnight) every day.
     * Cron format: second, minute, hour, day of month, month, day of week
     */
    @Scheduled(cron = "0 0 0 * * *")
    public void resetDailyTokens() {
        Firestore db = FirestoreClient.getFirestore();
        CollectionReference users = db.collection("users");

        try {
            // 1. Get all user documents
            ApiFuture<QuerySnapshot> query = users.get();
            List<QueryDocumentSnapshot> documents = query.get().getDocuments();

            // 2. Use a Firestore Batch to update multiple users efficiently
            WriteBatch batch = db.batch();

            for (QueryDocumentSnapshot document : documents) {
                DocumentReference docRef = users.document(document.getId());

                // Update only the specific fields
                batch.update(docRef, "identificationTokens", 5);
                batch.update(docRef, "lastTokenReset", System.currentTimeMillis());
            }

            // 3. Commit the batch
            batch.commit().get();
            System.out.println("Successfully reset tokens for " + documents.size() + " users.");

        } catch (Exception e) {
            System.err.println("Failed to reset daily tokens: " + e.getMessage());
        }
    }
}