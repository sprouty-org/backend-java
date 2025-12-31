package si.uni.fri.sprouty.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import si.uni.fri.sprouty.model.NotificationRequest;
import si.uni.fri.sprouty.service.FcmService;

@RestController
@RequestMapping("/notifications")
public class NotificationController {
    private final FcmService fcmService;

    public NotificationController(FcmService fcmService) { this.fcmService = fcmService; }

    @PostMapping("/send")
    public ResponseEntity<String> send(@RequestBody NotificationRequest request) {
        fcmService.sendPush(request);
        return ResponseEntity.ok("Sent");
    }
}