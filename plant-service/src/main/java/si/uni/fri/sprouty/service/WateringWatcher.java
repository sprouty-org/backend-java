package si.uni.fri.sprouty.service;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import si.uni.fri.sprouty.dto.NotificationRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class WateringWatcher {

    private final Firestore firestore;
    private final RestTemplate restTemplate;

    public WateringWatcher(Firestore firestore, RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
        this.firestore = firestore;
    }

    @Scheduled(fixedRate = 43200000) // Every 12 hours
    public void observePlantThirst() {
        try {
            // 1. Load Master Data thresholds
            Map<String, Double> speciesThresholds = loadMasterHumidityThresholds();

            // 2. Load User Plants with notifications active
            ApiFuture<QuerySnapshot> future = firestore.collection("user_plants")
                    .whereEqualTo("notificationsEnabled", true)
                    .get();

            List<QueryDocumentSnapshot> plants = future.get().getDocuments();
            long nowInSeconds = System.currentTimeMillis() / 1000;
            long oneDayInSeconds = 86400;

            for (QueryDocumentSnapshot doc : plants) {
                String speciesName = doc.getString("speciesName");
                Long lastWatered = doc.getLong("lastWatered");
                Long intervalDays = doc.getLong("targetWateringInterval");
                Double currentSoilHum = doc.getDouble("currentHumiditySoil");
                Long lastSeen = doc.getLong("lastSeen");
                String sensorId = doc.getString("connectedSensorId"); // Check if sensor exists

                if (lastWatered == null || intervalDays == null) continue;

                double dryThreshold = speciesThresholds.getOrDefault(speciesName, 30.0);
                long secondsInInterval = intervalDays * oneDayInSeconds;

                // Logic Flags
                boolean isOverdueByCalendar = nowInSeconds > (lastWatered + secondsInInterval);
                boolean hasSensor = (sensorId != null && !sensorId.isEmpty());
                boolean isDataFresh = (lastSeen != null && (nowInSeconds - lastSeen) < oneDayInSeconds);

                // We only trust currentSoilHum if there's a sensor AND the data isn't stale
                boolean canTrustSensor = hasSensor && isDataFresh;
                boolean isActuallyDry = (canTrustSensor && currentSoilHum != null && currentSoilHum < dryThreshold);

                // LOGIC ENGINE
                if (isOverdueByCalendar) {
                    // If we have fresh sensor data, and it says it's NOT dry, we override the calendar
                    if (canTrustSensor && currentSoilHum != null && currentSoilHum >= dryThreshold) {
                        System.out.println("Override for " + doc.getString("customName") +
                                ": Calendar says water, but fresh sensor data says soil is still moist.");
                    }
                    // If sensor data says it's dry
                    else if (isActuallyDry) {
                        sendWateringReminder(doc.getString("ownerId"), doc.getString("customName"),
                                String.format("The soil is at %.1f%% (Recommended: >%.1f%%). Time for water!",
                                        currentSoilHum, dryThreshold));
                    }
                    // Fallback: No sensor, or sensor is offline/old data -> Rely on Calendar
                    else {
                        String reason = (!hasSensor) ?
                                "It's been " + intervalDays + " days since your last watering." :
                                "It's time to water, and your sensor hasn't reported in over 24 hours.";

                        sendWateringReminder(doc.getString("ownerId"), doc.getString("customName"), reason);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Watering Observer Fail: " + e.getMessage());
        }
    }

    /**
     * Fetches master plants and extracts the minimum soil humidity.
     * Expects "soilH" string format: "min, max" (e.g., "30, 70")
     */
    private Map<String, Double> loadMasterHumidityThresholds() throws Exception {
        Map<String, Double> thresholds = new HashMap<>();
        ApiFuture<QuerySnapshot> masterFuture = firestore.collection("master_plants").get();

        for (QueryDocumentSnapshot doc : masterFuture.get().getDocuments()) {
            String species = doc.getString("speciesName");
            String soilH = doc.getString("soilH"); // Format: "30, 70"

            if (species != null && soilH != null) {
                try {
                    String minVal = soilH.split(",")[0].trim();
                    thresholds.put(species, Double.parseDouble(minVal));
                } catch (Exception e) {
                    System.err.println("SoilH parsing error for " + species + ": " + e.getMessage());
                }
            }
        }
        return thresholds;
    }

    private void sendWateringReminder(String userId, String plantName, String reason) {
        NotificationRequest request = new NotificationRequest();
        request.setUserId(userId);
        request.setTitle("Sprouty: Thirsty Plant! 💧");
        request.setBody(plantName + " needs attention: " + reason);

        try {
            restTemplate.postForEntity("http://notification-service:8084/notifications/send", request, String.class);
        } catch (Exception e) {
            System.err.println("Failed to send notification: " + e.getMessage());
        }
    }
}