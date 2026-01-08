package si.uni.fri.sprouty.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import si.uni.fri.sprouty.dto.*;
import si.uni.fri.sprouty.service.PlantService;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/plants")
@Tag(name = "Plant Management", description = "Endpoints for AI plant identification, sensor linking, and garden care.")
public class PlantController {

    private final PlantService plantService;

    public PlantController(PlantService plantService) {
        this.plantService = plantService;
    }

    // --- IDENTIFICATION ---

    @Operation(summary = "Identify plant from image", description = "Recognizes species via Pl@ntNet and fetches care data via OpenAI.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful identification", content = @Content(schema = @Schema(implementation = IdentificationResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "AI/Storage failure", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping(value = "/identify", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<IdentificationResponse> identifyPlant(
            @Parameter(hidden = true) @RequestHeader(name = "X-User-Id") String uid,
            @RequestPart(name = "image") MultipartFile image) {

        try {
            Map<String, Object> result = plantService.identifyAndProcess(uid, image.getBytes());
            return ResponseEntity.ok(new IdentificationResponse(
                    (UserPlant) result.get("userPlant"),
                    (MasterPlant) result.get("masterPlant")
            ));
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid image file.");
        }
    }

    // --- GARDEN MANAGEMENT ---

    @Operation(summary = "Get full garden profile", description = "Returns all user plants and associated botanical data.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Profile retrieved", content = @Content(schema = @Schema(implementation = GardenProfileResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/profile")
    public ResponseEntity<GardenProfileResponse> getGardenProfile(
            @Parameter(hidden = true) @RequestHeader(name = "X-User-Id") String uid) {
        return ResponseEntity.ok(plantService.getFullGardenProfile(uid));
    }

    @Operation(summary = "Log watering event", description = "Resets the watering timer for a specific plant.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Watering logged"),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Not your plant", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Plant not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/{id}/water")
    public ResponseEntity<Void> waterPlant(
            @Parameter(hidden = true) @RequestHeader(name = "X-User-Id") String uid,
            @PathVariable(name = "id") String plantId) {
        plantService.resetWateringTimer(uid, plantId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Rename a plant")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Plant renamed"),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Plant not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @SecurityRequirement(name = "bearerAuth")
    @PatchMapping("/{id}/rename")
    public ResponseEntity<Void> renamePlant(
            @Parameter(hidden = true) @RequestHeader(name = "X-User-Id") String uid,
            @PathVariable(name = "id") String plantId,
            @RequestParam(name = "newName") String newName) {
        plantService.updatePlantName(uid, plantId, newName);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Toggle care notifications")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Notifications toggled"),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Plant not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @SecurityRequirement(name = "bearerAuth")
    @PatchMapping("/{id}/notifications")
    public ResponseEntity<Map<String, Boolean>> toggleNotifications(
            @Parameter(hidden = true) @RequestHeader(name = "X-User-Id") String uid,
            @PathVariable(name = "id") String plantId,
            @RequestParam(name = "enabled") boolean enabled) {
        plantService.updateNotificationSettings(uid, plantId, enabled);
        return ResponseEntity.ok(Map.of("notificationsEnabled", enabled));
    }

    // --- SENSOR LINKING ---

    @Operation(summary = "Link sensor to plant")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Sensor linked"),
            @ApiResponse(responseCode = "400", description = "Sensor already in use", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/connect-sensor")
    public ResponseEntity<String> connectSensor(
            @Parameter(hidden = true) @RequestHeader(name = "X-User-Id") String uid,
            @RequestParam(name = "plantId") String plantId,
            @RequestParam(name = "sensorId") String sensorId) {
        plantService.manageSensor(uid, plantId, sensorId);
        return ResponseEntity.ok("Sensor successfully linked.");
    }

    @Operation(summary = "Disconnect sensor")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Sensor disconnected"),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Plant not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/{id}/disconnect-sensor")
    public ResponseEntity<Void> disconnectSensor(
            @Parameter(hidden = true) @RequestHeader(name = "X-User-Id") String uid,
            @PathVariable(name = "id") String plantId) {
        plantService.manageSensor(uid, plantId, null);
        return ResponseEntity.ok().build();
    }

    // --- DELETION ---

    @Operation(summary = "Delete plant")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Plant deleted"),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Plant not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    })
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePlant(
            @Parameter(hidden = true) @RequestHeader(name = "X-User-Id") String uid,
            @PathVariable(name = "id") String plantId) {
        plantService.deleteUserPlant(uid, plantId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Internal cleanup", hidden = true)
    @DeleteMapping("/internal/user")
    public ResponseEntity<Void> deleteUserPlantsInternal(@RequestParam(name = "uid") String uid) {
        plantService.deleteAllPlantsForUser(uid);
        return ResponseEntity.noContent().build();
    }
}