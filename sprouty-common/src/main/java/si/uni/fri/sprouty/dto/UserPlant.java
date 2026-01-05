package si.uni.fri.sprouty.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "User-specific plant instance linked to real-time hardware sensors")
public class UserPlant {
    @Schema(description = "Unique database ID")
    private String id;

    @Schema(description = "Owner Firebase UID")
    private String ownerId;

    @Schema(description = "Reference to the AI-generated species data")
    private String speciesId;

    @Schema(example = "Monstera")
    private String speciesName;

    @Schema(description = "Personal nickname for the plant", example = "Monty")
    private String customName;

    @Schema(description = "Firebase storage URL for the plant photo")
    private String imageUrl;

    @Schema(description = "Timestamp of the last water log")
    private long lastWatered;

    @Schema(description = "User-adjusted watering frequency (days)", example = "7")
    private int targetWateringInterval;

    @Schema(description = "Calculated health based on sensor data vs AI thresholds", example = "Thirsty")
    private String healthStatus;

    @Schema(description = "Last time the ESP32 checked in")
    private long lastSeen;

    @Schema(description = "MAC address of the ESP32-S3/CAM")
    private String connectedSensorId;

    @Schema(description = "Active push notification status")
    private boolean notificationsEnabled = true;

    @Schema(description = "Real-time Temp from DHT11 (°C)")
    private double currentTemperature;

    @Schema(description = "Real-time Air Humidity from DHT11 (%)")
    private double currentHumidityAir;

    @Schema(description = "Real-time Soil Moisture from Analog Probe (%)")
    private double currentHumiditySoil;

    public UserPlant() {}
}