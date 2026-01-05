package si.uni.fri.sprouty.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import si.uni.fri.sprouty.dto.GardenProfileResponse;
import si.uni.fri.sprouty.dto.IdentificationResponse;
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

    @Operation(summary = "Identify plant from image", description = "Recognizes species via Pl@ntNet, fetches care data via OpenAI, and saves to user garden.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful identification",
                    content = @Content(schema = @Schema(implementation = IdentificationResponse.class))),
            @ApiResponse(responseCode = "500", description = "AI service or Storage failure")
    })
    @PostMapping(value = "/identify", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> identifyPlant(
            @Parameter(description = "Firebase UID") @RequestHeader(name = "X-User-Id") String uid,
            @RequestPart(name = "image") MultipartFile image) {
        try {
            Map<String, Object> result = plantService.identifyAndProcess(uid, image.getBytes());
            IdentificationResponse response = new IdentificationResponse(
                    (UserPlant) result.get("userPlant"),
                    (MasterPlant) result.get("masterPlant")
            );
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Identification error: " + e.getMessage());
        }
    }

    @Operation(summary = "Link sensor to plant", description = "Associates a physical ESP32 sensor (by MAC address) to a specific plant in the garden.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Sensor linked successfully"),
            @ApiResponse(responseCode = "400", description = "Sensor already in use or invalid ID")
    })
    @PostMapping("/connect-sensor")
    public ResponseEntity<?> connectSensor(
            @Parameter(description = "Firebase UID") @RequestHeader(name = "X-User-Id") String uid,
            @RequestParam(name = "plantId") String plantId,
            @RequestParam(name = "sensorId") String sensorId) {
        try {
            plantService.manageSensor(uid, plantId, sensorId);
            return ResponseEntity.ok("Sensor successfully linked.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @Operation(summary = "Disconnect sensor", description = "Removes the link between a plant and its sensor, making the sensor available for other plants.")
    @PostMapping("/{id}/disconnect-sensor")
    public ResponseEntity<?> disconnectSensor(
            @RequestHeader(name = "X-User-Id") String uid,
            @PathVariable(name = "id") String plantId) {
        try {
            plantService.manageSensor(uid, plantId, null);
            return ResponseEntity.ok("Sensor disconnected and ready for reuse.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @Operation(summary = "Rename a plant", description = "Allows the user to set a custom nickname for their plant.")
    @PatchMapping("/{id}/rename")
    public ResponseEntity<?> renamePlant(
            @RequestHeader(name = "X-User-Id") String uid,
            @PathVariable(name = "id") String plantId,
            @RequestParam(name = "newName") String newName) {
        try {
            plantService.updatePlantName(uid, plantId, newName);
            return ResponseEntity.ok("Plant renamed successfully.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @Operation(summary = "Get user's garden")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Garden retrieved successfully",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = UserPlant.class)))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Server error",
                    content = @Content(mediaType = "text/plain", schema = @Schema(implementation = String.class, example = "Firestore connection timeout"))
            )
    })
    @GetMapping("/userPlants")
    public ResponseEntity<?> getMyGarden(@RequestHeader(name = "X-User-Id") String uid) {
        try {
            List<UserPlant> plants = plantService.getUserPlants(uid);
            return ResponseEntity.ok(plants);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(
            summary = "Get relevant master data",
            description = "Fetches the AI-generated care profiles for all species currently present in the user's garden."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Master care data retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = MasterPlant.class))
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Server error",
                    content = @Content(mediaType = "text/plain", schema = @Schema(implementation = String.class, example = "Firestore connection timeout"))
            )
    })
    @GetMapping("/masterPlants")
    public ResponseEntity<List<MasterPlant>> getRelevantMasterData(@RequestHeader(name = "X-User-Id") String uid) {
        try {
            List<MasterPlant> masterData = plantService.getRelevantMasterPlants(uid);
            return ResponseEntity.ok(masterData);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(
            summary = "Get full garden profile",
            description = "Optimized endpoint that fetches both user plants and master care data in a single logic flow."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Profile retrieved",
                    content = @Content(schema = @Schema(implementation = GardenProfileResponse.class))
            ),
            @ApiResponse(responseCode = "500", description = "Server error",
                    content = @Content(schema = @Schema(implementation = String.class)))
    })
    @GetMapping("/profile")
    public ResponseEntity<?> getGardenProfile(@RequestHeader(name = "X-User-Id") String uid) {
        try {
            GardenProfileResponse profile = plantService.getFullGardenProfile(uid);
            return ResponseEntity.ok(profile);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Sync Error: " + e.getMessage());
        }
    }

    @Operation(summary = "Toggle care notifications", description = "Enables or disables push notifications for watering and health alerts.")
    @PatchMapping("/{id}/notifications")
    public ResponseEntity<?> toggleNotifications(
            @RequestHeader(name = "X-User-Id") String uid,
            @PathVariable(name = "id") String plantId,
            @RequestParam(name = "enabled") boolean enabled) {
        try {
            plantService.updateNotificationSettings(uid, plantId, enabled);
            return ResponseEntity.ok(Map.of("notificationsEnabled", enabled));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @Operation(summary = "Log watering event", description = "Resets the watering timer for a plant. Use this when the user manually waters the plant.")
    @PostMapping("/{id}/water")
    public ResponseEntity<?> waterPlant(
            @RequestHeader(name = "X-User-Id") String uid,
            @PathVariable(name = "id") String id) {
        try {
            plantService.resetWateringTimer(uid, id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @Operation(summary = "Delete plant", description = "Removes a plant from the user's garden permanently.")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletePlant(
            @RequestHeader(name = "X-User-Id") String uid,
            @PathVariable(name = "id") String id) {
        try {
            plantService.deleteUserPlant(uid, id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @Operation(summary = "Internal cleanup", hidden = true)
    @DeleteMapping("/internal/user")
    public ResponseEntity<?> deleteUserPlantsInternal(
            @RequestParam(name = "uid") String uid) {
        try {
            plantService.deleteAllPlantsForUser(uid);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error cleaning up user plants: " + e.getMessage());
        }
    }
}