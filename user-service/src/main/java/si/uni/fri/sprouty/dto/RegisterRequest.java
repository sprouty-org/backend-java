package si.uni.fri.sprouty.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterRequest {
    @Schema(example = "eyJhbGciOiJSUzI1NiIs...", description = "Firebase ID Token from Frontend SDK")
    private String idToken;

    @Schema(example = "fcm_token_12345", description = "Current FCM token for notifications")
    private String fcmToken;
}
