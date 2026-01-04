package si.uni.fri.sprouty.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import si.uni.fri.sprouty.service.SensorService;

import java.util.Map;

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
            @ApiResponse(responseCode = "200", description = "Data processed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid payload format")
    })
    @PostMapping(value = "/data", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> receiveSensorData(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Sensor payload containing MAC address and environment readings.",
                    content = @Content(schema = @Schema(example = "{\"sensorId\": \"AABBCCDDEEFF\", \"moisture\": 45.5, \"temperature\": 22.1, \"humidity\": 60.0}"))
            )
            @RequestBody Map<String, Object> payload) {

        String sensorId = (String) payload.get("sensorId");
        double moisture = Double.parseDouble(payload.get("moisture").toString());
        double temperature = payload.containsKey("temperature") ?
                Double.parseDouble(payload.get("temperature").toString()) : 0.0;
        double humidityAir = payload.containsKey("humidity") ?
                Double.parseDouble(payload.get("humidity").toString()) : 0.0;

        sensorService.processSensorUpdate(sensorId, temperature, humidityAir, moisture);
        return ResponseEntity.ok("Sensor data saved");
    }

    @Operation(
            summary = "Upload Sensor Image",
            description = "Processes raw multipart stream from ESP32-CAM and uploads to Firebase Storage."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Image successfully stored"),
            @ApiResponse(responseCode = "400", description = "Missing parts"),
            @ApiResponse(responseCode = "500", description = "Upload failed")
    })
    @PostMapping(value = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> uploadImage(
            @RequestParam("sensorId") String sensorId,
            @RequestParam("image") MultipartFile image) {

        try {
            if (image.isEmpty()) {
                return ResponseEntity.badRequest().body("Image file is empty");
            }

            // Convert MultipartFile to byte[]
            byte[] bytes = image.getBytes();

            // Call the service (Synchronous version)
            String imageUrl = sensorService.uploadSensorImage(bytes, sensorId);

            if (imageUrl != null) {
                return ResponseEntity.ok("Image processed: " + imageUrl);
            } else {
                return ResponseEntity.internalServerError().body("Failed to upload to storage");
            }

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error: " + e.getMessage());
        }
    }
}