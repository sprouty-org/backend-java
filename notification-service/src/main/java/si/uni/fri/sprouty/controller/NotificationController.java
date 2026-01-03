package si.uni.fri.sprouty.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import si.uni.fri.sprouty.dto.NotificationRequest;
import si.uni.fri.sprouty.service.FcmService;

@RestController
@RequestMapping("/notifications")
@Tag(name = "Push Notifications", description = "Endpoints for sending Firebase Cloud Messages to mobile devices.")
public class NotificationController {
    private final FcmService fcmService;

    public NotificationController(FcmService fcmService) {
        this.fcmService = fcmService;
    }

    @Operation(
            summary = "Send Notification",
            description = "Sends a message to a specific user's device. If 'title' and 'body' are omitted, it acts as a 'Silent Sync' for the Android UI."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Message successfully handed off to FCM"),
            @ApiResponse(responseCode = "500", description = "FCM delivery failed or User token not found")
    })
    @PostMapping("/send")
    public ResponseEntity<String> send(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Details of the notification or sync request.",
                    content = @Content(schema = @Schema(example = "{\"userId\": \"user123\", \"title\": \"Water Me!\", \"body\": \"Your Monstera is thirsty.\" }"))
            )
            @RequestBody NotificationRequest request) {
        try {
            fcmService.sendPush(request);
            return ResponseEntity.ok("Sent");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Failed: " + e.getMessage());
        }
    }
}