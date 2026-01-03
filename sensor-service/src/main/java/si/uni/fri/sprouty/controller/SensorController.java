package si.uni.fri.sprouty.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
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
            description = "Uploads an image from a camera-enabled sensor (like ESP32-CAM) to Firebase Storage."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Image successfully stored and signed URL returned"),
            @ApiResponse(responseCode = "500", description = "Upload failed")
    })
    @PostMapping(value = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<String>> uploadImage(
            @Parameter(description = "MAC Address of the sensor") @RequestPart("sensorId") String sensorId,
            @Parameter(description = "The image file (JPG/PNG)") @RequestPart("image") FilePart filePart) {

        return filePart.content()
                .map(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);
                    return bytes;
                })
                .collectList()
                .flatMap(list -> {
                    int totalSize = list.stream().mapToInt(b -> b.length).sum();
                    byte[] allBytes = new byte[totalSize];
                    int offset = 0;
                    for (byte[] b : list) {
                        System.arraycopy(b, 0, allBytes, offset, b.length);
                        offset += b.length;
                    }

                    return Mono.fromCallable(() -> {
                        String imageUrl = sensorService.uploadSensorImage(allBytes, sensorId);
                        return ResponseEntity.ok("Image processed: " + imageUrl);
                    }).subscribeOn(Schedulers.boundedElastic());
                });
    }
}