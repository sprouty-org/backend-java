package si.uni.fri.sprouty.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import si.uni.fri.sprouty.service.SensorService;
import org.springframework.core.io.buffer.DataBufferUtils;

import java.util.Map;

@RestController
@RequestMapping("/sensors")
public class SensorController {

    private final SensorService sensorService;

    public SensorController(SensorService sensorService) {
        this.sensorService = sensorService;
    }

    @PostMapping(value = "/data", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> receiveSensorData(@RequestBody Map<String, Object> payload) {
        String sensorId = (String) payload.get("sensorId");
        double moisture = Double.parseDouble(payload.get("moisture").toString());
        double temperature = payload.containsKey("temperature") ?
                Double.parseDouble(payload.get("temperature").toString()) : 0.0;
        double humidityAir = payload.containsKey("humidity") ?
                Double.parseDouble(payload.get("humidity").toString()) : 0.0;

        sensorService.processSensorUpdate(sensorId, temperature, humidityAir, moisture);
        return ResponseEntity.ok("Sensor data saved");
    }

    @PostMapping(value = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<String>> uploadImage(
            @RequestPart("sensorId") String sensorId,
            @RequestPart("image") FilePart filePart) {

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
                        // CRITICAL: Link the new image URL to the plant in Firestore
                        sensorService.updatePlantImage(sensorId, imageUrl);
                        return ResponseEntity.ok("Image processed: " + imageUrl);
                    }).subscribeOn(Schedulers.boundedElastic());
                });
    }
}