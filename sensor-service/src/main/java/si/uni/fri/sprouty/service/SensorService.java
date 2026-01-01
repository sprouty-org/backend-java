package si.uni.fri.sprouty.service;

import com.google.cloud.firestore.*;
import com.google.firebase.FirebaseApp;
import com.google.firebase.cloud.FirestoreClient;
import com.google.firebase.cloud.StorageClient;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Blob;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import si.uni.fri.sprouty.dto.MasterPlant;
import si.uni.fri.sprouty.dto.NotificationRequest;
import si.uni.fri.sprouty.dto.UserPlant;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class SensorService {

    private final RestTemplate restTemplate = new RestTemplate();
    private Firestore getDb() {
        return FirestoreClient.getFirestore(FirebaseApp.getInstance(), "sprouty-firestore");
    }

    public void processSensorUpdate(String macAddress, double temp, double humAir, double humSoil) {
        try {
            Firestore db = getDb();
            QuerySnapshot qs = db.collection("user_plants")
                    .whereEqualTo("connectedSensorId", macAddress).get().get();

            if (qs.isEmpty()) return;

            DocumentSnapshot doc = qs.getDocuments().getFirst();
            UserPlant userPlant = doc.toObject(UserPlant.class);

            DocumentSnapshot masterDoc = db.collection("master_plants").document(userPlant.getSpeciesId()).get().get();
            MasterPlant master = masterDoc.toObject(MasterPlant.class);
            String newHealthStatus = (master != null) ? calculateHealth(temp, humSoil, master) : "Unknown";

            // Update Firestore
            Map<String, Object> updates = new HashMap<>();
            updates.put("currentHumiditySoil", humSoil);
            updates.put("currentTemperature", temp); // Save temp so it shows in the app
            updates.put("currentHumidityAir", humAir);
            updates.put("healthStatus", newHealthStatus);
            updates.put("lastSeen", System.currentTimeMillis());
            doc.getReference().update(updates).get();

            // 1. ALWAYS Trigger a silent sync so the Android UI updates live data
            triggerSilentSync(userPlant.getOwnerId());

            // 2. Only send a visible alert if the health state actually changed to something bad
            if (master != null && !newHealthStatus.equals("Healthy") && !newHealthStatus.equals(userPlant.getHealthStatus())) {
                checkAndSendAlert(userPlant, newHealthStatus);
            }

            recordHistoryAndCleanup(doc.getId(), temp, humAir, humSoil);

        } catch (Exception e) {
            System.err.println("Processing Error: " + e.getMessage());
        }
    }

    // New helper to poke the app to refresh its data
    private void triggerSilentSync(String userId) {
        NotificationRequest syncRequest = new NotificationRequest();
        syncRequest.setUserId(userId);
        // We leave Title/Body null so FcmService knows this is data-only (silent)
        try {
            restTemplate.postForEntity("http://notification-service/notifications/send", syncRequest, String.class);
        } catch (Exception ignored) {}
    }

    public String uploadSensorImage(byte[] imageBytes, String mac) {
        try {
            Bucket bucket = StorageClient.getInstance().bucket();
            String fileName = String.format("sensors/%s/%d.jpg", mac, System.currentTimeMillis());
            Blob blob = bucket.create(fileName, imageBytes, "image/jpeg");
            return blob.signUrl(365, TimeUnit.DAYS).toString();
        } catch (Exception e) {
            System.err.println("Storage Upload Fail: " + e.getMessage());
            return null;
        }
    }

    // NEW METHOD: Links the uploaded file URL back to the Firestore UserPlant document
    public void updatePlantImage(String macAddress, String imageUrl) {
        try {
            Firestore db = getDb();
            QuerySnapshot qs = db.collection("user_plants")
                    .whereEqualTo("connectedSensorId", macAddress).get().get();
            if (!qs.isEmpty()) {
                qs.getDocuments().getFirst().getReference().update("imageUrl", imageUrl);
                System.out.println("Firestore updated with new image URL for " + macAddress);
            }
        } catch (Exception e) {
            System.err.println("Error updating imageUrl in Firestore: " + e.getMessage());
        }
    }

    private String calculateHealth(double temp, double humSoil, MasterPlant master) {
        try {
            String[] soilInterval = master.getSoilH().split(",");
            double minSoil = Double.parseDouble(soilInterval[0]);
            double maxSoil = Double.parseDouble(soilInterval[1]);
            if (humSoil < minSoil) return "Thirsty";
            if (humSoil > maxSoil) return "Overwatered";
            if (temp < master.getMinT()) return "Too Cold";
            if (temp > master.getMaxT()) return "Too Hot";
        } catch (Exception ignored) { }
        return "Healthy";
    }

    private void checkAndSendAlert(UserPlant userPlant, String healthStatus) {
        NotificationRequest request = new NotificationRequest();
        request.setUserId(userPlant.getOwnerId());
        request.setTitle("Plant Alert: " + userPlant.getCustomName());
        request.setBody("Your plant is currently " + healthStatus.toLowerCase() + ".");
        try {
            restTemplate.postForEntity("http://notification-service/notifications/send", request, String.class);
        } catch (Exception e) {
            System.err.println("Notification Service unreachable");
        }
    }

    private void recordHistoryAndCleanup(String plantId, double temp, double humAir, double humSoil) {
        Firestore db = getDb();
        long now = System.currentTimeMillis();
        Map<String, Object> log = new HashMap<>();
        log.put("plantId", plantId);
        log.put("temperature", temp);
        log.put("humidityAir", humAir);
        log.put("humiditySoil", humSoil);
        log.put("timestamp", now);
        db.collection("sensor_history").add(log);
    }
}