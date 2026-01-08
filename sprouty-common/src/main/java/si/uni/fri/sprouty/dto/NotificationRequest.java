package si.uni.fri.sprouty.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request object for sending push notifications or triggering silent background syncs.")
public class NotificationRequest {

    @Schema(description = "Firebase UID of the target user.", example = "GuTOgdV82ZhQOqYVM1Thsoz8LvZ2")
    private String userId;

    @Schema(description = "The title of the notification popup.", example = "Water Me!")
    private String title;

    @Schema(description = "The message body shown in the notification tray.", example = "Your Monstera is thirsty.")
    private String body;

    @Schema(description = "Key-value pairs for custom data payloads (silent updates).",
            example = "{\"action\": \"REFRESH_PLANTS\", \"plantId\": \"123\"}")
    private Map<String, String> data = new HashMap<>();
}