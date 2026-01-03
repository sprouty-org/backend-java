package si.uni.fri.sprouty.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import si.uni.fri.sprouty.dto.MasterPlant;
import si.uni.fri.sprouty.dto.UserPlant;
import si.uni.fri.sprouty.service.PlantService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/plants")
@Tag(name = "Plant Management", description = "Endpoints for AI plant identification, sensor linking, and garden care.")
public class PlantController {

    private final PlantService plantService;

    public PlantController(PlantService plantService) {
        this.plantService = plantService;
    }

    @PostMapping(value = "/identify", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Object> identifyPlant(
            @RequestHeader(name = "X-User-Id") String uid, // Added name
            @RequestPart(name = "image") MultipartFile image) { // Added name

        try {
            byte[] allBytes = image.getBytes();
            Map<String, Object> result = plantService.identifyAndProcess(uid, allBytes);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Internal error: " + e.getMessage());
        }
    }

    @PostMapping("/connect-sensor")
    public ResponseEntity<?> connectSensor(
            @RequestHeader(name = "X-User-Id") String uid, // Added name
            @RequestParam(name = "plantId") String plantId, // Added name
            @RequestParam(name = "sensorId") String sensorId // Added name
    ) {
        try {
            plantService.manageSensor(uid, plantId, sensorId);
            return ResponseEntity.ok("Sensor successfully linked.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @PostMapping("/{id}/disconnect-sensor")
    public ResponseEntity<?> disconnectSensor(
            @RequestHeader(name = "X-User-Id") String uid, // Added name
            @PathVariable(name = "id") String plantId // Added name
    ) {
        try {
            plantService.manageSensor(uid, plantId, null);
            return ResponseEntity.ok("Sensor disconnected and ready for reuse.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @PatchMapping("/{id}/rename")
    public ResponseEntity<?> renamePlant(
            @RequestHeader(name = "X-User-Id") String uid, // Added name
            @PathVariable(name = "id") String plantId, // Added name
            @RequestParam(name = "newName") String newName // Added name
    ) {
        try {
            plantService.updatePlantName(uid, plantId, newName);
            return ResponseEntity.ok("Plant renamed successfully.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @GetMapping("/userPlants")
    public ResponseEntity<?> getMyGarden(@RequestHeader(name = "X-User-Id") String uid) { // Added name logic
        try {
            List<UserPlant> plants = plantService.getUserPlants(uid);
            return ResponseEntity.ok(plants);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @GetMapping("/masterPlants")
    public ResponseEntity<?> getRelevantMasterData(@RequestHeader(name = "X-User-Id") String uid) { // Added name logic
        try {
            List<MasterPlant> masterData = plantService.getRelevantMasterPlants(uid);
            return ResponseEntity.ok(masterData);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @PatchMapping("/{id}/notifications")
    public ResponseEntity<?> toggleNotifications(
            @RequestHeader(name = "X-User-Id") String uid, // Added name
            @PathVariable(name = "id") String plantId, // Added name
            @RequestParam(name = "enabled") boolean enabled // Added name
    ) {
        try {
            plantService.updateNotificationSettings(uid, plantId, enabled);
            return ResponseEntity.ok(Map.of("notificationsEnabled", enabled));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @PostMapping("/{id}/water")
    public ResponseEntity<?> waterPlant(
            @RequestHeader(name = "X-User-Id") String uid, // Added name
            @PathVariable(name = "id") String id // Added name
    ) {
        try {
            plantService.resetWateringTimer(uid, id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletePlant(
            @RequestHeader(name = "X-User-Id") String uid, // Added name
            @PathVariable(name = "id") String id) { // Added name
        try {
            plantService.deleteUserPlant(uid, id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @DeleteMapping("/internal/user")
    public ResponseEntity<?> deleteUserPlantsInternal(
            @RequestParam(name = "uid") String uid) { // Added name
        try {
            plantService.deleteAllPlantsForUser(uid);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error cleaning up user plants: " + e.getMessage());
        }
    }
}