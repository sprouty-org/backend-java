package si.uni.fri.sprouty.controller;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.UserRecord;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import si.uni.fri.sprouty.dto.User;
import si.uni.fri.sprouty.service.FirebaseAuthService;
import si.uni.fri.sprouty.service.FirestoreService;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

@RestController
@RequestMapping("/users")
@Tag(name = "Authentication", description = "Endpoints for user registration, login, and account lifecycle management.")
public class AuthController {

    private final FirebaseAuthService firebaseAuthService;
    private final FirestoreService firestoreService;
    private final FirebaseAuth firebaseAuth = FirebaseAuth.getInstance();
    private final org.springframework.web.reactive.function.client.WebClient.Builder webClientBuilder;

    @Value("${jwt.secret}")
    private String secretKey;

    public AuthController(FirebaseAuthService firebaseAuthService, FirestoreService firestoreService, WebClient.Builder wcb) {
        this.firebaseAuthService = firebaseAuthService;
        this.firestoreService = firestoreService;
        this.webClientBuilder = wcb;
    }

    @Operation(summary = "Register with Email/Password", description = "Creates a new user in Firebase Auth and a profile record in Firestore.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User created successfully, returns internal JWT"),
            @ApiResponse(responseCode = "500", description = "Registration failed")
    })
    @PostMapping(value = "/register", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> registerUser(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String password = request.get("password");
        String displayName = request.getOrDefault("displayName", "Gardener");
        String fcmToken = request.get("fcmToken");

        try {
            UserRecord.CreateRequest createRequest = new UserRecord.CreateRequest()
                    .setEmail(email)
                    .setPassword(password)
                    .setDisplayName(displayName);

            UserRecord userRecord = firebaseAuth.createUser(createRequest);
            String uid = userRecord.getUid();

            User newUser = new User(uid, email, displayName, fcmToken);
            firestoreService.saveUser(newUser);

            String jwt = generateInternalJwt(uid);
            return ResponseEntity.ok(Map.of("firebaseUid", uid, "token", jwt));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @Operation(summary = "Register with Google", description = "Verifies a Google ID token and creates a first-time account.")
    @PostMapping(value = "/register/google", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> exchangeGoogleRegisterToken(@RequestBody Map<String, String> request) {
        String idToken = request.get("idToken");
        String fcmToken = request.get("fcmToken");

        try {
            String uid = firebaseAuthService.verifyFirebaseToken(idToken);
            UserRecord userRecord = firebaseAuth.getUser(uid);
            String email = userRecord.getEmail();
            String displayName = (userRecord.getDisplayName() != null) ? userRecord.getDisplayName() : "Gardener";

            User newUser = new User(uid, email, displayName, fcmToken);
            firestoreService.saveUser(newUser);

            String jwt = generateInternalJwt(uid);
            return ResponseEntity.ok(Map.of("firebaseUid", uid, "token", jwt));
        } catch (Exception e) {
            return ResponseEntity.status(401).body("Registration failed: " + e.getMessage());
        }
    }

    @Operation(summary = "Login (Email or Google)", description = "Validates Firebase ID token and issues an internal JWT for Gateway authorization.")
    @PostMapping(value = {"/login/google", "/login/email"}, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> login(@RequestBody Map<String, String> request) {
        String idToken = request.get("idToken");
        String fcmToken = request.get("fcmToken");

        try {
            String uid = firebaseAuthService.verifyFirebaseToken(idToken);

            if (fcmToken != null && !fcmToken.isEmpty()) {
                firestoreService.updateFcmToken(uid, fcmToken);
            }

            String jwt = generateInternalJwt(uid);
            return ResponseEntity.ok(Map.of("firebaseUid", uid, "token", jwt));
        } catch (Exception e) {
            return ResponseEntity.status(401).body("Login failed");
        }
    }

    @Operation(summary = "Delete Account", description = "Triggers cascading deletion: calls Plant Service to remove plants, then cleans up User records.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Account fully deleted"),
            @ApiResponse(responseCode = "500", description = "Partial deletion error")
    })
    @DeleteMapping("/me")
    public ResponseEntity<?> deleteAccount(
            @Parameter(description = "UID extracted from JWT by the Gateway") @RequestHeader("X-User-Id") String uid) {
        try {
            // Orchestration: Tell Plant Service to clean up its data first
            webClientBuilder.build()
                    .delete()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("http")
                            .host("plant-service")
                            .port(8082)
                            .path("/plants/internal/user")
                            .queryParam("uid", uid)
                            .build())
                    .retrieve()
                    .toBodilessEntity()
                    .block();

            firestoreService.deleteUserRecord(uid);
            firebaseAuth.deleteUser(uid);

            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Partial deletion occurred: " + e.getMessage());
        }
    }

    private String generateInternalJwt(String firebaseUid) {
        byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        SecretKey key = Keys.hmacShaKeyFor(keyBytes);
        return Jwts.builder()
                .subject(firebaseUid)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 86400000))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }
}