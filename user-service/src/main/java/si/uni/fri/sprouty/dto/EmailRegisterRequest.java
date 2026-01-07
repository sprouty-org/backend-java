package si.uni.fri.sprouty.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EmailRegisterRequest {
    @Schema(example = "test-user@sprouty.si", description = "User email")
    private String email;

    @Schema(example = "testpassword123", description = "User password")
    private String password;

    @Schema(example = "Sprouty Tester", description = "Name shown in app")
    private String displayName;

    @Schema(example = "test_fcm_token", description = "Your device FCM token")
    private String fcmToken;
}