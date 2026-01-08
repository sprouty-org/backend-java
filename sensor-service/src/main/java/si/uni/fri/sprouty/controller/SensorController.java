package si.uni.fri.sprouty.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import si.uni.fri.sprouty.dto.ErrorResponse;
import si.uni.fri.sprouty.dto.ImageUploadRequest;
import si.uni.fri.sprouty.dto.SensorDataRequest;
import si.uni.fri.sprouty.service.SensorService;

import java.io.IOException;

@RestController
@RequestMapping("/sensors")
@Tag(name = "Sensor Integration", description = "IoT Hardware communication layer.")
public class SensorController {

    private final SensorService sensorService;

    public SensorController(SensorService sensorService) {
        this.sensorService = sensorService;
    }

    @Operation(summary = "Ingest Environmental Telemetry")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Telemetry updated"),
            @ApiResponse(responseCode = "404", description = "Sensor ID not recognized", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Server error", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping(value = "/data", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> receiveSensorData(@Valid @RequestBody SensorDataRequest request) {
        sensorService.processSensorUpdate(
                request.getSensorId(),
                request.getTemperature(),
                request.getHumidity(),
                request.getMoisture()
        );
        return ResponseEntity.ok("Data ingested.");
    }

    @Operation(summary = "Upload Plant Snapshot")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "URL of stored image returned"),
            @ApiResponse(responseCode = "400", description = "Invalid multipart data", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Storage failure", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping(value = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> uploadImage(@ModelAttribute ImageUploadRequest request) {
        if (request.getImage() == null || request.getImage().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Multipart file is missing.");
        }

        try {
            String imageUrl = sensorService.uploadSensorImage(
                    request.getImage().getBytes(),
                    request.getSensorId()
            );

            if (imageUrl == null) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Cloud storage failed to provide URL.");
            }
            return ResponseEntity.ok(imageUrl);

        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read image buffer.");
        }
    }
}