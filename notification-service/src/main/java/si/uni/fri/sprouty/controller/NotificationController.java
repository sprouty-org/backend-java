package si.uni.fri.sprouty.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import si.uni.fri.sprouty.dto.NotificationRequest;
import si.uni.fri.sprouty.service.FcmService;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
@Tag(name = "Push Notifications", description = "Endpoints for managing FCM delivery.")
public class NotificationController {

    private final FcmService fcmService;

    @Operation(
            summary = "Send Notification",
            description = "Sends a message via Firebase. If 'title' and 'body' are missing, the message serves as a silent update notification to the app's UI."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Message successfully handed off to FCM"),
            @ApiResponse(responseCode = "500", description = "FCM delivery failed or User token not found")
    })
    @PostMapping("/send")
    public ResponseEntity<String> send(@RequestBody NotificationRequest request) {
        try {
            fcmService.sendPush(request);
            return ResponseEntity.ok("Sent");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Failed: " + e.getMessage());
        }
    }
    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("UP");
    }
}