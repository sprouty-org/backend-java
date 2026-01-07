package si.uni.fri.sprouty.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import si.uni.fri.sprouty.dto.ImageUploadRequest;
import si.uni.fri.sprouty.dto.SensorDataRequest;
import si.uni.fri.sprouty.service.SensorService;

import java.io.IOException;

@RestController
@RequestMapping("/sensors")
@Tag(name = "Sensor Integration", description = "Endpoints for IoT hardware (ESP32/ESP32-CAM) to transmit plant vitals.")
public class SensorController {

    private final SensorService sensorService;

    public SensorController(SensorService sensorService) {
        this.sensorService = sensorService;
    }

    @Operation(
            summary = "Ingest Environmental Telemetry",
            description = "Receives temperature, air humidity, and soil moisture from a physical sensor. " +
                    "Calculates plant health based on master species thresholds and triggers alerts if necessary."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Data point recorded and health status updated"),
            @ApiResponse(responseCode = "400", description = "Invalid payload (e.g. moisture out of 0-100 range)"),
            @ApiResponse(responseCode = "404", description = "No UserPlant found associated with this sensor ID"),
            @ApiResponse(responseCode = "500", description = "Firestore or Notification Service connectivity error")
    })
    @PostMapping(value = "/data", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> receiveSensorData(@Valid @RequestBody SensorDataRequest request) {
        sensorService.processSensorUpdate(
                request.getSensorId(),
                request.getTemperature(),
                request.getHumidity(),
                request.getMoisture()
        );
        return ResponseEntity.ok("Sensor data saved");
    }

    @Operation(
            summary = "Upload Plant Snapshot",
            description = "Uploads binary image data (JPEG) to Firebase Storage. " +
                    "The image is stored in a structured path: `sensors/{mac}/{timestamp}.jpg`."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Image uploaded successfully. Returns the public signed URL."),
            @ApiResponse(responseCode = "400", description = "Missing multipart image file"),
            @ApiResponse(responseCode = "500", description = "Cloud Storage upload failed")
    })
    @PostMapping(value = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> uploadImage(@ModelAttribute ImageUploadRequest request) {
        if (request.getImage() == null || request.getImage().isEmpty()) {
            return ResponseEntity.badRequest().body("Image file is missing or empty");
        }

        try {
            String imageUrl = sensorService.uploadSensorImage(
                    request.getImage().getBytes(),
                    request.getSensorId()
            );

            return (imageUrl != null)
                    ? ResponseEntity.ok("Image processed: " + imageUrl)
                    : ResponseEntity.internalServerError().body("Failed to upload to storage");

        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("Error reading image bytes");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("System error: " + e.getMessage());
        }
    }
}