package si.uni.fri.sprouty.controller;

import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import si.uni.fri.sprouty.model.MasterPlant;
import si.uni.fri.sprouty.model.UserPlant;
import si.uni.fri.sprouty.service.PlantService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/plants")
public class PlantController {

    private final PlantService plantService;

    public PlantController(PlantService plantService) {
        this.plantService = plantService;
    }


    /**
     * Connect a hardware sensor ID to a specific UserPlant.
     * Explicitly naming parameters to satisfy Spring Boot 3.2+ reflection requirements.
     */
    @PostMapping("/connect-sensor")
    public ResponseEntity<?> connectSensor(
            @RequestHeader("X-User-Id") String uid,
            @RequestParam("plantId") String plantId,   // Added ("plantId")
            @RequestParam("sensorId") String sensorId  // Added ("sensorId")
    ) {
        try {
            plantService.linkSensorToPlant(uid, plantId, sensorId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @PostMapping(value = "/identify", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<Object>> identifyPlant(
            @RequestHeader("X-User-Id") String uid,
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
                        Map<String, Object> result = plantService.identifyAndProcess(uid, allBytes);
                        return ResponseEntity.ok((Object) result);
                    }).subscribeOn(Schedulers.boundedElastic());
                })
                .onErrorResume(e -> Mono.just(ResponseEntity.status(500).body(e.getMessage())));
    }

    @GetMapping("/user")
    public ResponseEntity<?> getMyGarden(@RequestHeader("X-User-Id") String uid) {
        try {
            List<UserPlant> plants = plantService.getUserPlants(uid);
            return ResponseEntity.ok(plants);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @GetMapping("/master")
    public ResponseEntity<?> getRelevantMasterData(@RequestHeader("X-User-Id") String uid) {
        try {
            List<MasterPlant> masterData = plantService.getRelevantMasterPlants(uid);
            return ResponseEntity.ok(masterData);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateUserPlant(
            @RequestHeader("X-User-Id") String uid,
            @PathVariable String id,
            @RequestBody UserPlant userPlant) {
        try {
            plantService.updateUserPlant(uid, id, userPlant);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletePlant(
            @RequestHeader("X-User-Id") String uid,
            @PathVariable String id) {
        try {
            plantService.deleteUserPlant(uid, id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }
}