package si.uni.fri.sprouty.service;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.*;
import com.google.firebase.cloud.StorageClient;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Blob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import si.uni.fri.sprouty.dto.MasterPlant;
import si.uni.fri.sprouty.dto.NotificationRequest;
import si.uni.fri.sprouty.dto.UserPlant;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@Service
public class SensorService {

    private static final Logger logger = LoggerFactory.getLogger(SensorService.class);
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
            // Find the plant linked to this sensor
            QuerySnapshot qs = db.collection("user_plants").whereEqualTo("connectedSensorId", macAddress).get().get();

            if (qs.isEmpty()) {
                logger.warn("Received data for unlinked sensor: {}", macAddress);
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Sensor not linked to any plant.");
            }

            DocumentSnapshot doc = qs.getDocuments().getFirst();
            UserPlant userPlant = doc.toObject(UserPlant.class);

            // Get plant thresholds
            DocumentSnapshot masterDoc = db.collection("master_plants").document(userPlant.getSpeciesId()).get().get();
            MasterPlant master = masterDoc.toObject(MasterPlant.class);

            String newHealthStatus = (master != null) ? calculateHealth(temp, humSoil, humAir, master) : "Unknown";

            // update plant data
            Map<String, Object> updates = new HashMap<>();
            updates.put("currentHumiditySoil", humSoil);
            updates.put("currentTemperature", temp);
            updates.put("currentHumidityAir", humAir);
            updates.put("healthStatus", newHealthStatus);
            updates.put("lastSeen", System.currentTimeMillis());
            doc.getReference().update(updates).get();

            // Record historical data for charting
            recordHistory(doc.getId(), temp, humAir, humSoil);

            // Trigger a silent data sync in the mobile app
            triggerSilentSync(userPlant.getOwnerId());

            // Notify user if plant health has changed to a non-healthy status
            if (master != null && userPlant.isNotificationsEnabled() && !"Healthy".equals(newHealthStatus) && !newHealthStatus.equals(userPlant.getHealthStatus())) {
                sendPlantNotification(userPlant, "HEALTH_ALERT", newHealthStatus);
            }

        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Critical error processing sensor update for MAC: {}", macAddress, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error processing sensor telemetry.");
        }
    }

    @Scheduled(fixedRate = 6, timeUnit = TimeUnit.HOURS)
    public void monitorSensorConnectivity() {
        // Lock mechanism to ensure only one instance checks the sensors at a time

        DocumentReference lockRef = db.collection("locks").document("sensor_monitor_lock");
        long now = System.currentTimeMillis();

        try {
            boolean lockAcquired = Boolean.TRUE.equals(db.runTransaction(transaction -> {
                DocumentSnapshot lockSnap = transaction.get(lockRef).get();

                long lastRun = 0L;
                if (lockSnap.exists() && lockSnap.contains("lastRun")) {
                    Timestamp ts = lockSnap.getTimestamp("lastRun");
                    if (ts != null) lastRun = ts.toDate().getTime();
                }

                // If already run in the last 5 hours, skip
                if (now - lastRun < TimeUnit.HOURS.toMillis(5)) {
                    return false;
                }

                transaction.set(lockRef, Map.of("lastRun", Timestamp.ofTimeMicroseconds(now * 1000)));
                return true;
            }).get());

            if (!lockAcquired) return;

            // This instance has the lock, proceed to check for offline sensors

            long twentyFourHoursAgo = now - TimeUnit.DAYS.toMillis(1);
            QuerySnapshot offlinePlants = db.collection("user_plants")
                    .whereNotEqualTo("connectedSensorId", null)
                    .whereLessThan("lastSeen", twentyFourHoursAgo)
                    .get().get();

            for (QueryDocumentSnapshot doc : offlinePlants) {
                UserPlant plant = doc.toObject(UserPlant.class);
                if (!"Offline".equals(plant.getHealthStatus())) {
                    sendPlantNotification(plant, "CONNECTION_LOST", null);
                    doc.getReference().update("healthStatus", "Offline");
                    logger.info("Sensor {} marked Offline due to inactivity.", plant.getConnectedSensorId());
                }
            }
        } catch (Exception e) {
            logger.error("Connectivity Monitor Job Failed: ", e);
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
            logger.error("Storage Upload Failed for MAC {}: {}", mac, e.getMessage());
            return null;
        }
    }

    private void sendPlantNotification(UserPlant plant, String type, String healthStatus) {
        NotificationRequest request = new NotificationRequest();
        request.setUserId(plant.getOwnerId());

        String plantName = (plant.getCustomName() != null && !plant.getCustomName().isBlank()) ? plant.getCustomName() : plant.getSpeciesName();

        switch (type) {
            case "CONNECTION_LOST" -> {
                request.setTitle("Connection Lost: " + plantName);
                request.setBody("We haven't heard from your sensor in 24 hours. Check its battery!");
            }
            case "HEALTH_ALERT" -> {
                request.setTitle("Sprouty Alert: " + plantName + " needs attention!");
                request.setBody(getFriendlyMessage(healthStatus));
            }
        }

        try {
            restTemplate.postForEntity(NOTIFICATION_SERVICE_URL, request, String.class);
        } catch (Exception e) {
            logger.error("Notification delivery failed for user {}: {}", plant.getOwnerId(), e.getMessage());
        }
    }

    private void triggerSilentSync(String userId) {
        NotificationRequest syncRequest = new NotificationRequest();
        syncRequest.setUserId(userId);
        try {
            restTemplate.postForEntity(NOTIFICATION_SERVICE_URL, syncRequest, String.class);
        } catch (Exception e) {
            logger.warn("Silent sync failed for user {}: {}", userId, e.getMessage());
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
            logger.warn("Threshold parsing failed for species {}: {}", master.getSpeciesName(), e.getMessage());
        }
        return "Healthy";
    }

    private String getFriendlyMessage(String status) {
        Random rand = new Random();
        int index = rand.nextInt(3);

        return switch (status) {
            case "Thirsty" -> new String[]{
                    "I'm parched! A little water, please?",
                    "Water me! \"desert music starts playing\"",
                    "I need a drink! Just no alcohol this time."
            }[index];

            case "Overwatered" -> new String[]{
                    "I'm drowning over here. Throw me a floaty!",
                    "Tell the captain I'm going down!",
                    "Too much water! Drain me a bit!"
            }[index];

            case "Too Cold" -> new String[]{
                    "Brrr! It's freezing!",
                    "Warm me up!",
                    "Is there a window open? I'm cold!"
            }[index];

            case "Too Hot" -> new String[]{
                    "I'm sweating! Put me in the freezer! (just kidding don't)",
                    "It's like an oven in here! Help!",
                    "Okay I like summer but this is too much! Get me some shade!"
            }[index];

            case "Freezing Risk" -> new String[]{
                    "EMERGENCY! I'm a popsicle!",
                    "Save me from the cold, I am turning into a snowman!",
                    "Give me a blanket or something!"
            }[index];

            case "Dry Air" -> new String[]{
                    "My leaves are getting crispy. Where's the humidity?",
                    "I feel like I'm living in a hair dryer. Send mist!",
                    "I am drier than the texts from your crush, please spray me a bit."
            }[index];

            case "Too Humid" -> new String[]{
                    "It's like a Turkish sauna in here!",
                    "I can barely breathe, it's too soggy!",
                    "Give me some fresh air, It's worse than a rainforest in here!"
            }[index];

            default -> "Something feels off... can you take a look at me?";
        };
    }

    private void recordHistory(String plantId, double temp, double humAir, double humSoil) {
        Map<String, Object> log = new HashMap<>();
        log.put("plantId", plantId);
        log.put("temperature", temp);
        log.put("humidityAir", humAir);
        log.put("humiditySoil", humSoil);
        log.put("timestamp", System.currentTimeMillis());
        db.collection("sensor_history").add(log);
    }
}