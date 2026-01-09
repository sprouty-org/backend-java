package si.uni.fri.sprouty.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Environmental data payload from Sprouty hardware")
public class SensorDataRequest {

    @NotBlank(message = "Sensor ID is required")
    @Schema(description = "MAC address of the sensor without \":\"", example = "AABBCCDDEEFF")
    private String sensorId;

    @Min(0) @Max(100)
    @Schema(description = "Soil moisture percentage", example = "45.5")
    private double moisture;

    @Schema(description = "Ambient temperature in Celsius", example = "22.1")
    private double temperature;

    @Schema(description = "Relative air humidity percentage", example = "60.0")
    private double humidity;
}