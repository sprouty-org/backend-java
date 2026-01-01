package si.uni.fri.sprouty.dto;


import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserPlant {
    private String id;               // Unique ID for this specific physical plant
    private String ownerId;          // Link to User
    private String speciesId;        // Link to Global Plant ID (Foreign Key)
    private String speciesName;      // Redundant but fast for UI display

    // User Customization
    private String customName;

    private String imageUrl;

    // Health & Growth
    private long lastWatered;
    private String healthStatus;     // "Healthy", "Dry", "Overwatered"
    private long lastSeen; // Timestamp of the last sensor reading

    // Sensor & Notifications
    private String connectedSensorId;
    private boolean notificationsEnabled = true;

    // The 3 new fields for real-time display
    private double currentTemp;
    private double currentHumidityAir;
    private double currentHumiditySoil;

    public UserPlant() {}
}