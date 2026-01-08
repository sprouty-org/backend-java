package si.uni.fri.sprouty.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import si.uni.fri.sprouty.dto.ErrorResponse;
import si.uni.fri.sprouty.dto.NotificationRequest;
import si.uni.fri.sprouty.service.NotificationService;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
@Tag(name = "Push Notifications", description = "Endpoints for managing FCM delivery and silent syncs.")
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(
            summary = "Send Notification",
            description = "Sends a message via Firebase. If 'title' and 'body' are missing, the message serves as a silent update notification to trigger a UI refresh."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Message successfully handed off to FCM"),
            @ApiResponse(responseCode = "404", description = "Target user has no registered device", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "FCM infrastructure error", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/send")
    public ResponseEntity<Void> send(@RequestBody NotificationRequest request) {
        notificationService.sendPush(request);
        return ResponseEntity.ok().build();
    }
}