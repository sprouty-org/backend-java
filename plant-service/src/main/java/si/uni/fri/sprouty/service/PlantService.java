package si.uni.fri.sprouty.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.firestore.*;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;
import com.google.firebase.cloud.StorageClient;
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

    private DocumentReference getValidatedPlantReference(String userId, String plantId) throws Exception {
        DocumentReference docRef = db.collection(USER_PLANTS_COLLECTION).document(plantId);
        DocumentSnapshot snapshot = docRef.get().get();

        if (!snapshot.exists()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Plant record not found.");
        }

        String ownerId = snapshot.getString("ownerId");
        if (ownerId == null || !ownerId.equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: You do not own this plant.");
        }

        return docRef;
    }

    // --- CORE LOGIC ---

    public Map<String, Object> identifyAndProcess(String uid, byte[] imageBytes) throws Exception {
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

        assert masterPlant != null;
        return Map.of("userPlant", userPlant, "masterPlant", masterPlant);
    }

    public void manageSensor(String userId, String plantId, String sensorId) throws Exception {
        DocumentReference docRef = getValidatedPlantReference(userId, plantId);

        if (sensorId != null && !sensorId.isBlank()) {
            QuerySnapshot existing = db.collection(USER_PLANTS_COLLECTION)
                    .whereEqualTo("connectedSensorId", sensorId)
                    .get().get();
            if (!existing.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This sensor is already linked to another plant.");
            }
        }
        docRef.update("connectedSensorId", sensorId).get();
    }

    public void resetWateringTimer(String userId, String plantId) throws Exception {
        DocumentReference docRef = getValidatedPlantReference(userId, plantId);
        docRef.update("lastWatered", System.currentTimeMillis()).get();
    }

    public void updatePlantName(String userId, String plantId, String newName) throws Exception {
        DocumentReference docRef = getValidatedPlantReference(userId, plantId);
        docRef.update("customName", newName != null ? newName.trim() : null).get();
    }

    public void updateNotificationSettings(String userId, String plantId, boolean enabled) throws Exception {
        DocumentReference docRef = getValidatedPlantReference(userId, plantId);
        docRef.update("notificationsEnabled", enabled).get();
    }

    // --- EXTERNAL API CALLS ---

    public String callPlantRecognitionApi(byte[] imageBytes) throws Exception {
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
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Plant identification service is currently unavailable.");
        }
    }

    private MasterPlant fetchPlantDataFromOpenAI(String species) throws Exception {
        String url = "https://api.openai.com/v1/chat/completions";

        // RESTORED: Your exact original detailed prompt
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
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            String content = objectMapper.readTree(response.getBody()).path("choices").get(0).path("message").path("content").asText();
            return objectMapper.readValue(content, MasterPlant.class);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "AI Care Data Generation Failed: " + e.getMessage());
        }
    }

    // --- STORAGE & DELETE ---

    String uploadImageToStorage(byte[] imageBytes, String userId) {
        Bucket bucket = StorageClient.getInstance().bucket();
        String fileName = String.format("users/%s/plants/%s.jpg", userId, UUID.randomUUID());
        Blob blob = bucket.create(fileName, imageBytes, "image/jpeg");
        return blob.signUrl(3650, TimeUnit.DAYS).toString();
    }

    public void deleteUserPlant(String userId, String plantId) throws Exception {
        DocumentReference docRef = getValidatedPlantReference(userId, plantId);
        docRef.delete().get();
    }

    public void deleteAllPlantsForUser(String uid) throws Exception {
        Query query = db.collection(USER_PLANTS_COLLECTION).whereEqualTo("ownerId", uid);
        QuerySnapshot snapshot = query.get().get();

        if (snapshot.isEmpty()) return;

        WriteBatch batch = db.batch();
        for (DocumentSnapshot doc : snapshot.getDocuments()) {
            batch.delete(doc.getReference());
        }
        batch.commit().get();
    }

    // --- GETTERS ---

    public List<UserPlant> getUserPlants(String userId) throws Exception {
        return db.collection(USER_PLANTS_COLLECTION).whereEqualTo("ownerId", userId).get().get()
                .getDocuments().stream()
                .map(doc -> doc.toObject(UserPlant.class))
                .collect(Collectors.toList());
    }

    public GardenProfileResponse getFullGardenProfile(String uid) throws Exception {
        List<UserPlant> userPlants = getUserPlants(uid);
        if (userPlants.isEmpty()) return new GardenProfileResponse(List.of(), List.of());

        List<String> speciesIds = userPlants.stream()
                .map(UserPlant::getSpeciesId)
                .distinct()
                .filter(id -> id != null && !id.isEmpty())
                .collect(Collectors.toList());

        List<MasterPlant> masterPlants = db.collection(MASTER_PLANTS_COLLECTION)
                .whereIn(FieldPath.documentId(), speciesIds)
                .get().get().getDocuments().stream()
                .map(doc -> doc.toObject(MasterPlant.class))
                .collect(Collectors.toList());

        return new GardenProfileResponse(userPlants, masterPlants);
    }
}