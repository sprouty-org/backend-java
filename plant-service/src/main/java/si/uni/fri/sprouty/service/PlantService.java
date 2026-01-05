package si.uni.fri.sprouty.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;
import com.google.firebase.FirebaseApp;
import com.google.firebase.cloud.FirestoreClient;
import com.google.firebase.cloud.StorageClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import si.uni.fri.sprouty.dto.GardenProfileResponse;
import si.uni.fri.sprouty.dto.MasterPlant;
import com.google.cloud.firestore.*;
import si.uni.fri.sprouty.dto.UserPlant;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import java.util.List;
import java.util.Map;

@Service
public class PlantService {
    private final Firestore db;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${openai.api.key}")
    private String openAiKey;

    @Value("${plantnet.api.key}")
    private String plantNetKey;

    public PlantService() {
        this.db = FirestoreClient.getFirestore(FirebaseApp.getInstance(), "sprouty-firestore");
    }


    private DocumentReference getValidatedPlantReference(String userId, String plantId) throws Exception {
        DocumentReference docRef = db.collection("user_plants").document(plantId);
        DocumentSnapshot snapshot = docRef.get().get();

        if (!snapshot.exists()) {
            throw new Exception("Plant not found");
        }

        String ownerId = snapshot.getString("ownerId");
        if (ownerId == null || !ownerId.equals(userId)) {
            throw new Exception("Unauthorized: You do not own this plant");
        }

        return docRef;
    }

    public void manageSensor(String userId, String plantId, String sensorId) throws Exception {
        DocumentReference docRef = getValidatedPlantReference(userId, plantId);
        String targetId = (sensorId == null || sensorId.isBlank()) ? null : sensorId;
        docRef.update("connectedSensorId", targetId).get();
    }


    public void updatePlantName(String userId, String plantId, String newName) throws Exception {
        DocumentReference docRef = getValidatedPlantReference(userId, plantId);
        String targetName = (newName == null || newName.isBlank()) ? null : newName.trim();
        docRef.update("customName", targetName).get();
    }

    public void updateNotificationSettings(String userId, String plantId, boolean enabled) throws Exception {
        DocumentReference docRef = getValidatedPlantReference(userId, plantId);
        docRef.update("notificationsEnabled", enabled).get();
    }

    public Map<String, Object> identifyAndProcess(String uid, byte[] imageBytes) throws Exception {
        String recognizedSpecies = callPlantRecognitionApi(imageBytes);
        String masterId = recognizedSpecies.toLowerCase().replace(" ", "_");
        String publicImageUrl = uploadImageToStorage(imageBytes, uid);

        DocumentReference masterRef = db.collection("master_plants").document(masterId);
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
        assert masterPlant != null;
        userPlant.setSpeciesName(masterPlant.getSpeciesName());
        userPlant.setImageUrl(publicImageUrl);
        userPlant.setLastWatered(System.currentTimeMillis());
        userPlant.setTargetWateringInterval(masterPlant.getWaterInterval());
        userPlant.setHealthStatus("Healthy");
        userPlant.setConnectedSensorId(null);

        DocumentReference userPlantRef = db.collection("user_plants").document();
        userPlant.setId(userPlantRef.getId());
        userPlantRef.set(userPlant).get();

        return Map.of("userPlant", userPlant, "masterPlant", masterPlant);
    }

    public String callPlantRecognitionApi(byte[] imageBytes) throws Exception {
        String url = "https://my-api.plantnet.org/v2/identify/all?api-key=" + plantNetKey;
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        ByteArrayResource imageResource = new ByteArrayResource(imageBytes) {
            @Override
            public String getFilename() { return "upload.jpg"; }
        };
        body.add("images", imageResource);
        body.add("organs", "auto");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, requestEntity, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode firstResult = root.path("results").get(0);
            return firstResult.path("species").path("scientificNameWithoutAuthor").asText();
        } catch (Exception e) {
            throw new Exception("Pl@ntNet error: " + e.getMessage());
        }
    }

    private MasterPlant fetchPlantDataFromOpenAI(String species) throws Exception {
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
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
        String content = objectMapper.readTree(response.getBody()).path("choices").get(0).path("message").path("content").asText();
        return objectMapper.readValue(content, MasterPlant.class);
    }

    private String uploadImageToStorage(byte[] imageBytes, String userId) {
        Bucket bucket = StorageClient.getInstance().bucket();
        String fileName = String.format("users/%s/plants/%s.jpg", userId, UUID.randomUUID());
        Blob blob = bucket.create(fileName, imageBytes, "image/jpeg");
        return blob.signUrl(3650, TimeUnit.DAYS).toString();
    }

    public List<UserPlant> getUserPlants(String userId) throws Exception {
        Query query = db.collection("user_plants").whereEqualTo("ownerId", userId);
        return query.get().get().getDocuments().stream()
                .map(doc -> {
                    UserPlant plant = doc.toObject(UserPlant.class);
                    plant.setId(doc.getId());
                    return plant;
                }).collect(Collectors.toList());
    }

    public List<MasterPlant> getRelevantMasterPlants(String userId) throws Exception {
        List<UserPlant> userPlants = getUserPlants(userId);
        List<String> speciesIds = userPlants.stream()
                .map(UserPlant::getSpeciesId)
                .distinct()
                .filter(id -> id != null && !id.isEmpty())
                .collect(Collectors.toList());
        if (speciesIds.isEmpty()) return List.of();
        Query masterQuery = db.collection("master_plants").whereIn(FieldPath.documentId(), speciesIds);
        return masterQuery.get().get().getDocuments().stream()
                .map(doc -> {
                    MasterPlant master = doc.toObject(MasterPlant.class);
                    master.setId(doc.getId());
                    return master;
                }).collect(Collectors.toList());
    }

    public void deleteUserPlant(String userId, String plantId) throws Exception {
        DocumentReference docRef = db.collection("user_plants").document(plantId);
        DocumentSnapshot snapshot = docRef.get().get();
        if (snapshot.exists() && Objects.equals(snapshot.getString("ownerId"), userId)) docRef.delete().get();
    }

    public void deleteAllPlantsForUser(String uid) throws Exception {
        Firestore db = FirestoreClient.getFirestore(FirebaseApp.getInstance(), "sprouty-firestore");

        ApiFuture<QuerySnapshot> future = db.collection("user_plants")
                .whereEqualTo("ownerId", uid)
                .get();

        List<QueryDocumentSnapshot> documents = future.get().getDocuments();

        if (documents.isEmpty()) {
            return;
        }

        WriteBatch batch = db.batch();
        for (QueryDocumentSnapshot doc : documents) {
            batch.delete(doc.getReference());
        }
        batch.commit().get();
    }

    public void resetWateringTimer(String userId, String plantId) throws Exception {
        DocumentReference docRef = db.collection("user_plants").document(plantId);
        DocumentSnapshot snapshot = docRef.get().get();

        if (!snapshot.exists() || !Objects.equals(snapshot.getString("ownerId"), userId)) {
            throw new Exception("Unauthorized or plant not found");
        }
        docRef.update("lastWatered", System.currentTimeMillis()).get();
    }

    public GardenProfileResponse getFullGardenProfile(String uid) throws Exception {
        List<UserPlant> userPlants = getUserPlants(uid);
        if (userPlants.isEmpty()) {
            return new GardenProfileResponse(List.of(), List.of());
        }

        List<String> speciesIds = userPlants.stream()
                .map(UserPlant::getSpeciesId)
                .distinct()
                .filter(id -> id != null && !id.isEmpty())
                .collect(Collectors.toList());

        if (speciesIds.isEmpty()) {
            return new GardenProfileResponse(userPlants, List.of());
        }

        Query masterQuery = db.collection("master_plants").whereIn(FieldPath.documentId(), speciesIds);
        List<MasterPlant> masterPlants = masterQuery.get().get().getDocuments().stream()
                .map(doc -> {
                    MasterPlant master = doc.toObject(MasterPlant.class);
                    master.setId(doc.getId());
                    return master;
                }).collect(Collectors.toList());
        return new GardenProfileResponse(userPlants, masterPlants);
    }
}