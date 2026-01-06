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
@Tag(name = "Sensor Integration", description = "Endpoints used by physical hardware to transmit environmental data and images.")
public class SensorController {

    private final SensorService sensorService;

    public SensorController(SensorService sensorService) {
        this.sensorService = sensorService;
    }

    @Operation(
            summary = "Receive Sensor Data",
            description = "Standard endpoint for ESP32/IoT devices to send moisture, temperature, and air humidity. Triggers health calculations and silent UI sync."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully processed sensor data"),
            @ApiResponse(responseCode = "400", description = "Validation failed - check field constraints"),
            @ApiResponse(responseCode = "500", description = "Internal service error during processing")
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
            summary = "Upload Sensor Image",
            description = "Processes JPEG stream from ESP32-CAM and uploads to Firebase Storage."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Image successfully stored in Firebase"),
            @ApiResponse(responseCode = "400", description = "Multipart file is missing or empty"),
            @ApiResponse(responseCode = "500", description = "Storage upload failure or IO processing error")
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
    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("UP");
    }
}