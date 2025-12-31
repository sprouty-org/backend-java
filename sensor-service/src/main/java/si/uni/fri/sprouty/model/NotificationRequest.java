package si.uni.fri.sprouty.model;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class NotificationRequest {
    private String userId;
    private String title;
    private String body;
    private Map<String, String> data; // Optional: for silent data updates
}