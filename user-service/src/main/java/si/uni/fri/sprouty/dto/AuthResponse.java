package si.uni.fri.sprouty.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class AuthResponse {
    @Schema(example = "PuZ2tkV82WhQOqWVN2Thsoz9LvX1", description = "The unique Firebase UID")
    private String firebaseUid;

    @Schema(example = "eyJhbG...", description = "The internal JWT for microservice access")
    private String token;
}
