package si.uni.fri.sprouty.service;

import com.google.api.core.ApiFuture;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.*;
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

    @Scheduled(fixedRateString = "${sprouty.schedule.watering-check:43200000}")
    public void observePlantThirst() {
        DocumentReference globalLockRef = firestore.collection("locks").document("watering_monitor_lock");
        long now = System.currentTimeMillis();

        try {
            boolean jobLockAcquired = Boolean.TRUE.equals(firestore.runTransaction(transaction -> {
                DocumentSnapshot lockSnap = transaction.get(globalLockRef).get();

                long lastRun = 0L;
                if (lockSnap.exists() && lockSnap.contains("lastRun")) {
                    Timestamp ts = lockSnap.getTimestamp("lastRun");
                    if (ts != null) {
                        lastRun = ts.toDate().getTime();
                    }
                }

                if (now - lastRun < 43200000) {
                    return false;
                }

                transaction.set(globalLockRef, Map.of("lastRun", Timestamp.ofTimeMicroseconds(now * 1000)));
                return true;
            }).get());

            if (!jobLockAcquired) return;

            System.out.println("Watering Watcher lock acquired. Processing plants...");

            Map<String, Double> speciesThresholds = loadMasterHumidityThresholds();

            ApiFuture<QuerySnapshot> future = firestore.collection("user_plants")
                    .whereEqualTo("notificationsEnabled", true)
                    .get();

            List<QueryDocumentSnapshot> plants = future.get().getDocuments();
            long nowInSeconds = now / 1000;
            long oneDayInSeconds = 86400;

            for (QueryDocumentSnapshot doc : plants) {
                String speciesName = doc.getString("speciesName");
                Long lastWatered = doc.getLong("lastWatered");
                Long intervalDays = doc.getLong("targetWateringInterval");
                Double currentSoilHum = doc.getDouble("currentHumiditySoil");
                Long lastSeen = doc.getLong("lastSeen");
                String sensorId = doc.getString("connectedSensorId");
                String ownerId = doc.getString("ownerId");
                String customName = doc.getString("customName") != null ? doc.getString("customName") : speciesName;

                if (lastWatered == null || intervalDays == null) continue;

                double dryThreshold = speciesThresholds.getOrDefault(speciesName, 30.0);
                long lastWateredSeconds = lastWatered / 1000;
                long secondsInInterval = intervalDays * oneDayInSeconds;

                boolean isOverdueByCalendar = nowInSeconds > (lastWateredSeconds + secondsInInterval);
                boolean hasSensor = (sensorId != null && !sensorId.isEmpty());
                boolean isDataFresh = (lastSeen != null && (nowInSeconds - (lastSeen / 1000)) < oneDayInSeconds);

                boolean canTrustSensor = hasSensor && isDataFresh;
                boolean isActuallyDry = (canTrustSensor && currentSoilHum != null && currentSoilHum < dryThreshold);

                if (isOverdueByCalendar) {
                    if (canTrustSensor && currentSoilHum != null && currentSoilHum >= dryThreshold) {
                        doc.getReference().update("healthStatus", "Healthy");
                    } else {
                        String reason = isActuallyDry ?
                                String.format("Soil is at %.1f%% (Min: %.1f%%).", currentSoilHum, dryThreshold) :
                                "It's been " + intervalDays + " days since last watering.";

                        sendWateringReminder(ownerId, customName, reason);
                        doc.getReference().update("healthStatus", "Thirsty");
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Watering Observer Fail: " + e.getMessage());
        }
    }

    private Map<String, Double> loadMasterHumidityThresholds() throws Exception {
        Map<String, Double> thresholds = new HashMap<>();
        ApiFuture<QuerySnapshot> masterFuture = firestore.collection("master_plants").get();

        for (QueryDocumentSnapshot doc : masterFuture.get().getDocuments()) {
            String species = doc.getString("speciesName");
            String soilH = doc.getString("soilH");

            if (species != null && soilH != null) {
                try {
                    String minVal = soilH.split(",")[0].trim();
                    thresholds.put(species, Double.parseDouble(minVal));
                } catch (Exception ignored) {}
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
            restTemplate.postForEntity("http://notification-service/notifications/send", request, String.class);
        } catch (Exception e) {
            System.err.println("Cloud Comm Fail: " + e.getMessage());
        }
    }
}