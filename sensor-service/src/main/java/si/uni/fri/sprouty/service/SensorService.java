package si.uni.fri.sprouty.service;

import com.google.cloud.firestore.*;
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
import java.util.Random;
import java.util.concurrent.TimeUnit;

@Service
public class SensorService {

    private final RestTemplate restTemplate;
    private final Firestore db;
    private final StorageClient storage;
    private final String NOTIFICATION_SERVICE_URL = "http://notification-service/notifications/send";

    public SensorService(RestTemplate restTemplate, Firestore db, StorageClient storage) {
        this.restTemplate = restTemplate;
        this.db = db;
        this.storage = storage;
    }

    public void processSensorUpdate(String macAddress, double temp, double humAir, double humSoil) {
        try {
            QuerySnapshot qs = db.collection("user_plants").whereEqualTo("connectedSensorId", macAddress).get().get();

            if (qs.isEmpty()) return;

            DocumentSnapshot doc = qs.getDocuments().getFirst();
            UserPlant userPlant = doc.toObject(UserPlant.class);

            DocumentSnapshot masterDoc = db.collection("master_plants").document(userPlant.getSpeciesId()).get().get();
            MasterPlant master = masterDoc.toObject(MasterPlant.class);

            String newHealthStatus = (master != null) ? calculateHealth(temp, humSoil, humAir, master) : "Unknown";

            // Update Firestore
            Map<String, Object> updates = new HashMap<>();
            updates.put("currentHumiditySoil", humSoil);
            updates.put("currentTemperature", temp);
            updates.put("currentHumidityAir", humAir);
            updates.put("healthStatus", newHealthStatus);
            updates.put("lastSeen", System.currentTimeMillis());
            doc.getReference().update(updates).get();

            triggerSilentSync(userPlant.getOwnerId());

            if (master != null && userPlant.isNotificationsEnabled() && !newHealthStatus.equals("Healthy") && !newHealthStatus.equals(userPlant.getHealthStatus())) {
                checkAndSendAlert(userPlant, newHealthStatus);
            }
            recordHistoryAndCleanup(doc.getId(), temp, humAir, humSoil);

        } catch (Exception e) {
            System.err.println("Processing Error: " + e.getMessage());
        }
    }

    private void triggerSilentSync(String userId) {
        NotificationRequest syncRequest = new NotificationRequest();
        syncRequest.setUserId(userId);

        try {
            restTemplate.postForEntity(NOTIFICATION_SERVICE_URL, syncRequest, String.class);
        } catch (Exception e) {
            System.err.println("Silent sync failed: " + e.getMessage());
        }
    }

    public String uploadSensorImage(byte[] imageBytes, String mac) {
        try {
            String bucketName = "sprouty-plantapp.firebasestorage.app";
            Bucket bucket = storage.bucket(bucketName);

            String fileName = String.format("sensors/%s/%d.jpg", mac, System.currentTimeMillis());

            Blob blob = bucket.create(fileName, imageBytes, "image/jpeg");

            return blob.signUrl(365, TimeUnit.DAYS).toString();

        } catch (Exception e) {
            System.err.println("Storage Upload Fail: " + e.getMessage());
            return null;
        }
    }

    private String calculateHealth(double temp, double humSoil, double humAir, MasterPlant master) {
        if (master == null) return "Unknown";

        if (temp <= 0.0) return "Freezing Risk";

        try {
            String[] soilInterval = master.getSoilH().split(",");
            double minSoil = Double.parseDouble(soilInterval[0].trim());
            double maxSoil = Double.parseDouble(soilInterval[1].trim());

            if (humSoil < (minSoil - 15)) return "Thirsty";
            if (humSoil > (maxSoil + 15)) return "Overwatered";

            if (temp < (master.getMinT() - 5)) return "Too Cold";
            if (temp > (master.getMaxT() + 5)) return "Too Hot";

            if (master.getAirH() != null && !master.getAirH().isBlank()) {
                String[] airInterval = master.getAirH().split(",");
                double minAir = Double.parseDouble(airInterval[0].trim());
                double maxAir = Double.parseDouble(airInterval[1].trim());

                if (humAir < (minAir - 20)) return "Dry Air";
                if (humAir > (maxAir + 20)) return "Too Humid";
            }

        } catch (Exception e) {
            System.err.println("Health Calculation Error: " + e.getMessage());
        }

        return "Healthy";
    }

    private void checkAndSendAlert(UserPlant userPlant, String healthStatus) {
        NotificationRequest request = new NotificationRequest();
        request.setUserId(userPlant.getOwnerId());
        String title = userPlant.getCustomName() != null && !userPlant.getCustomName().isBlank() ? userPlant.getCustomName() : userPlant.getSpeciesId();
        request.setTitle("Sprouty Alert: " + title + " needs attention!");

        String message = getFriendlyMessage(healthStatus);
        request.setBody(message);

        try {
            restTemplate.postForEntity(NOTIFICATION_SERVICE_URL, request, String.class);
        } catch (Exception e) {
            System.err.println("Notification Service unreachable: " + e.getMessage());
        }
    }

    private String getFriendlyMessage(String status) {
        Random rand = new Random();
        int index = rand.nextInt(3); // Picks 0, 1, or 2

        return switch (status) {
            case "Thirsty" -> new String[]{
                    "I'm parched! A little water, please?",
                    "If I were a human, I'd be a raisin by now. Water me!",
                    "Is it me, or is it getting desert-like in here? I need a drink!"
            }[index];

            case "Overwatered" -> new String[]{
                    "I'm a plant, not a fish! Too much water!",
                    "Tell the captain I'm going down! I'm drowning over here.",
                    "I think I've had enough to drink. Let me dry off a bit."
            }[index];

            case "Too Cold" -> new String[]{
                    "Brrr! If I had teeth, they'd be chattering. It's freezing!",
                    "I’m a tropical soul in a tundra. Warm me up!",
                    "Is there a window open? My leaves are turning into ice cubes."
            }[index];

            case "Too Hot" -> new String[]{
                    "I'm sweating over here! Can we find some shade?",
                    "It's like an oven in here. I'm literally wilting!",
                    "Too. Much. Sun. I’m starting to feel like a grilled salad."
            }[index];

            case "Freezing Risk" -> new String[]{
                    "EMERGENCY! I'm basically a popsicle. Save me!",
                    "I’m seeing a white light... and it’s frost. Move me now!",
                    "Winter is coming, and I am NOT ready. Get me somewhere warm!"
            }[index];

            case "Dry Air" -> new String[]{
                    "My leaves are getting crispy. Where's the humidity?",
                    "I feel like I'm living in a hair dryer. Send mist!",
                    "The air is so dry I'm starting to itch. A spray would be nice."
            }[index];

            case "Too Humid" -> new String[]{
                    "It's like a Turkish sauna in here. I can barely breathe!",
                    "I'm getting all soggy. Give me some fresh air!",
                    "I love humidity, but this is a bit much. Open a window?"
            }[index];

            default -> new String[]{
                    "I just wanted to say hi! (And maybe check my soil?)",
                    "Something feels off... can you take a look at me?",
                    "I'm not feeling my best. Check my levels!"
            }[index];
        };
    }

    private void recordHistoryAndCleanup(String plantId, double temp, double humAir, double humSoil) {
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