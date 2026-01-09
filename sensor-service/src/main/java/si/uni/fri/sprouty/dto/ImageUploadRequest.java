package si.uni.fri.sprouty.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
@Schema(description = "Multipart payload for sensor image uploads")
public class ImageUploadRequest {

    @Schema(description = "MAC address of the sensor without \":\"", example = "AABBCCDDEEFF")
    private String sensorId;

    @Schema(description = "JPEG binary data from the camera")
    private MultipartFile image;
}