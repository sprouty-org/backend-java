package si.uni.fri.sprouty.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.firestore.*;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;
import com.google.firebase.cloud.StorageClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import si.uni.fri.sprouty.dto.GardenProfileResponse;
import si.uni.fri.sprouty.dto.MasterPlant;
import si.uni.fri.sprouty.dto.UserPlant;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class PlantService {

    private static final Logger logger = LoggerFactory.getLogger(PlantService.class);
    private final Firestore db;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate;

    private final String USER_PLANTS_COLLECTION = "user_plants";
    private final String MASTER_PLANTS_COLLECTION = "master_plants";

    @Value("${openai.api.key}")
    private String openAiKey;

    @Value("${plantnet.api.key}")
    private String plantNetKey;

    public PlantService(Firestore db, RestTemplate restTemplate) {
        this.db = db;
        this.restTemplate = restTemplate;
    }

    // --- SECURITY HELPER ---

    private DocumentReference getValidatedPlantReference(String userId, String plantId) {
        try {
            DocumentReference docRef = db.collection(USER_PLANTS_COLLECTION).document(plantId);
            DocumentSnapshot snapshot = docRef.get().get();

            if (!snapshot.exists()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Plant record not found.");
            }

            String ownerId = snapshot.getString("ownerId");
            if (ownerId == null || !ownerId.equals(userId)) {
                logger.warn("Security Alert: User {} attempted to access plant {} owned by {}", userId, plantId, ownerId);
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: You do not own this plant.");
            }

            return docRef;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Database access error.");
        }
    }

    // --- CORE LOGIC ---

    public Map<String, Object> identifyAndProcess(String uid, byte[] imageBytes) {
        try {
            String recognizedSpecies = callPlantRecognitionApi(imageBytes);
            String masterId = recognizedSpecies.toLowerCase().trim().replace(" ", "_");
            String publicImageUrl = uploadImageToStorage(imageBytes, uid);

            DocumentReference masterRef = db.collection(MASTER_PLANTS_COLLECTION).document(masterId);
            DocumentSnapshot masterSnap = masterRef.get().get();

            MasterPlant masterPlant;
            if (!masterSnap.exists()) {
                masterPlant = fetchPlantDataFromOpenAI(recognizedSpecies);
                masterPlant.setId(masterId);
                masterRef.set(masterPlant).get();
            } else {
                masterPlant = masterSnap.toObject(MasterPlant.class);
            }

            UserPlant userPlant = new UserPlant();
            userPlant.setOwnerId(uid);
            userPlant.setSpeciesId(masterId);
            userPlant.setSpeciesName(masterPlant != null ? masterPlant.getSpeciesName() : recognizedSpecies);
            userPlant.setImageUrl(publicImageUrl);
            userPlant.setLastWatered(System.currentTimeMillis());
            userPlant.setTargetWateringInterval(masterPlant != null ? masterPlant.getWaterInterval() : 7);
            userPlant.setHealthStatus("Healthy");
            userPlant.setConnectedSensorId(null);
            userPlant.setNotificationsEnabled(true);

            DocumentReference userPlantRef = db.collection(USER_PLANTS_COLLECTION).document();
            userPlant.setId(userPlantRef.getId());
            userPlantRef.set(userPlant).get();

            return Map.of("userPlant", userPlant, "masterPlant", masterPlant != null ? masterPlant : new Object());
        } catch (Exception e) {
            logger.error("Failed to process plant identification for user {}", uid, e);
            throw (e instanceof ResponseStatusException) ? (ResponseStatusException) e :
                    new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Plant processing failed.");
        }
    }

    public void manageSensor(String userId, String plantId, String sensorId) {
        DocumentReference docRef = getValidatedPlantReference(userId, plantId);
        try {
            if (sensorId != null && !sensorId.isBlank()) {
                QuerySnapshot existing = db.collection(USER_PLANTS_COLLECTION)
                        .whereEqualTo("connectedSensorId", sensorId)
                        .get().get();
                if (!existing.isEmpty()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This sensor is already linked to another plant.");
                }
            }
            docRef.update("connectedSensorId", sensorId).get();
        } catch (Exception e) {
            throw (e instanceof ResponseStatusException) ? (ResponseStatusException) e :
                    new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to update sensor link.");
        }
    }

    public void resetWateringTimer(String userId, String plantId) {
        DocumentReference docRef = getValidatedPlantReference(userId, plantId);
        docRef.update("lastWatered", System.currentTimeMillis());
    }

    public void updatePlantName(String userId, String plantId, String newName) {
        DocumentReference docRef = getValidatedPlantReference(userId, plantId);
        docRef.update("customName", newName != null ? newName.trim() : null);
    }

    public void updateNotificationSettings(String userId, String plantId, boolean enabled) {
        DocumentReference docRef = getValidatedPlantReference(userId, plantId);
        docRef.update("notificationsEnabled", enabled);
    }

    // --- EXTERNAL API CALLS ---

    String callPlantRecognitionApi(byte[] imageBytes) {
        String url = "https://my-api.plantnet.org/v2/identify/all?api-key=" + plantNetKey;
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("images", new ByteArrayResource(imageBytes) {
            @Override
            public String getFilename() { return "plant.jpg"; }
        });
        body.add("organs", "auto");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, new HttpEntity<>(body, headers), String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            return root.path("results").get(0).path("species").path("scientificNameWithoutAuthor").asText();
        } catch (Exception e) {
            logger.error("PlantNet identification failed", e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not identify plant.");
        }
    }

    private MasterPlant fetchPlantDataFromOpenAI(String species) {
        String url = "https://api.openai.com/v1/chat/completions";

        String prompt = String.format(
                "Return ONLY a valid JSON object for the plant species \"%s\". " +
                        "Format the JSON with these exact keys: " +
                        "\"speciesName\": (string), " +
                        "\"type\": (Choose one: Herb, Shrub, Tree, Vine, Succulent, Cactus, Fern, Palm), " +
                        "\"life\": (Choose one: Annual, Biennial, Perennial), " +
                        "\"fruit\": (Detailed description of the plant's fruit, seeds, or reproductive parts, minimum 2 sentences), " +
                        "\"uses\": (Array of 2 strings, each describing a unique medicinal, culinary, or practical use in detail), " +
                        "\"fact\": (One fascinating historical or biological sentence), " +
                        "\"tox\": (Detailed explanation of toxicity for humans and pets, including symptoms or safety warnings), " +
                        "\"minT\": (Integer, Celsius), \"maxT\": (Integer, Celsius), " +
                        "\"light\": (string: e.g., 'Bright Indirect Light'), " +
                        "\"soilH\": \"n1,n2\" (Numeric range 0-100, no spaces), " +
                        "\"airH\": \"n1,n2\" (Numeric range 0-100, no spaces), " +
                        "\"waterInterval\": (Integer, days between watering), " +
                        "\"growth\": (fast/moderate/slow), " +
                        "\"soil\": (Specific soil preference, e.g., 'Well-draining sandy loam'), " +
                        "\"maxHeight\": (Integer, cm), " +
                        "\"careDifficulty\": (Choose one: Easy, Medium, Hard)", species);

        Map<String, Object> body = Map.of(
                "model", "gpt-3.5-turbo",
                "messages", List.of(Map.of("role", "user", "content", prompt)),
                "response_format", Map.of("type", "json_object")
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(openAiKey);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, new HttpEntity<>(body, headers), String.class);
            String content = objectMapper.readTree(response.getBody()).path("choices").get(0).path("message").path("content").asText();
            return objectMapper.readValue(content, MasterPlant.class);
        } catch (Exception e) {
            logger.error("OpenAI care data fetch failed for species: {}", species, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "AI Care Data Generation Failed.");
        }
    }

    // --- STORAGE & DELETE ---

    String uploadImageToStorage(byte[] imageBytes, String userId) {
        try {
            Bucket bucket = StorageClient.getInstance().bucket();
            String fileName = String.format("users/%s/plants/%s.jpg", userId, UUID.randomUUID());
            Blob blob = bucket.create(fileName, imageBytes, "image/jpeg");
            return blob.signUrl(3650, TimeUnit.DAYS).toString();
        } catch (Exception e) {
            logger.error("Image upload to Firebase Storage failed for user {}", userId, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to save plant image.");
        }
    }

    public void deleteUserPlant(String userId, String plantId) {
        DocumentReference docRef = getValidatedPlantReference(userId, plantId);
        try {
            docRef.delete().get();
            logger.info("Plant {} successfully deleted by user {}", plantId, userId);
        } catch (Exception e) {
            logger.error("Error deleting plant {} for user {}", plantId, userId, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to delete plant record.");
        }
    }

    public void deleteAllPlantsForUser(String uid) {
        try {
            Query query = db.collection(USER_PLANTS_COLLECTION).whereEqualTo("ownerId", uid);
            QuerySnapshot snapshot = query.get().get();

            if (snapshot.isEmpty()) return;

            WriteBatch batch = db.batch();
            for (DocumentSnapshot doc : snapshot.getDocuments()) {
                batch.delete(doc.getReference());
            }
            batch.commit().get();
            logger.info("Purged all plant records for user {}", uid);
        } catch (Exception e) {
            logger.error("Failed to batch delete plants for user {}", uid, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Partial data cleanup failure.");
        }
    }

    // --- GETTERS ---

    public List<UserPlant> getUserPlants(String userId) {
        try {
            return db.collection(USER_PLANTS_COLLECTION).whereEqualTo("ownerId", userId).get().get()
                    .getDocuments().stream()
                    .map(doc -> doc.toObject(UserPlant.class))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to retrieve your plants.");
        }
    }

    public GardenProfileResponse getFullGardenProfile(String uid) {
        try {
            List<UserPlant> userPlants = getUserPlants(uid);
            if (userPlants.isEmpty()) return new GardenProfileResponse(List.of(), List.of());

            List<String> speciesIds = userPlants.stream()
                    .map(UserPlant::getSpeciesId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());

            List<MasterPlant> masterPlants = db.collection(MASTER_PLANTS_COLLECTION)
                    .whereIn(FieldPath.documentId(), speciesIds)
                    .get().get().getDocuments().stream()
                    .map(doc -> doc.toObject(MasterPlant.class))
                    .collect(Collectors.toList());

            return new GardenProfileResponse(userPlants, masterPlants);
        } catch (Exception e) {
            logger.error("Failed to build garden profile for user {}", uid, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not load garden profile.");
        }
    }
}