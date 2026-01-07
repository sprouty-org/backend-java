package si.uni.fri.sprouty.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "User profile information stored in Firestore")
public class User {

    @Schema(description = "Unique Firebase UID", example = "PuZ2tkV82WhQOqWVN2Thsoz9LvX1")
    private String uid;

    @Schema(description = "User's email address", example = "janez@sprouty.si")
    private String email;

    @Schema(description = "User's display name", example = "Janez Novak")
    private String displayName;

    @Schema(description = "Firebase Cloud Messaging token for push notifications",
            example = "fcm-token-123-abc", nullable = true)
    private String fcmToken;
}