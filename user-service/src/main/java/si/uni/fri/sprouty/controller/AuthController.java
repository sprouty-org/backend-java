package si.uni.fri.sprouty.controller;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.UserRecord;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import si.uni.fri.sprouty.dto.*;
import si.uni.fri.sprouty.service.FirebaseAuthService;
import si.uni.fri.sprouty.service.FirestoreService;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@RestController
@RequestMapping("/users")
@Tag(name = "Authentication", description = "Endpoints for user registration, login, and account management.")
public class AuthController {

    private final FirebaseAuthService firebaseAuthService;
    private final FirestoreService firestoreService;
    private final FirebaseAuth firebaseAuth;
    private final org.springframework.web.reactive.function.client.WebClient.Builder webClientBuilder;

    @Value("${jwt.secret}")
    private String secretKey;

    public AuthController(FirebaseAuthService firebaseAuthService,
                          FirestoreService firestoreService,
                          FirebaseAuth firebaseAuth,
                          WebClient.Builder wcb) {
        this.firebaseAuthService = firebaseAuthService;
        this.firestoreService = firestoreService;
        this.webClientBuilder = wcb;
        this.firebaseAuth = firebaseAuth;
    }

    @Operation(summary = "Register with Email/Password")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Registration successful", content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "500", description = "Registration failed", content = @Content(schema = @Schema(implementation = String.class, example = "Registration failed: error message")))
    })
    @PostMapping(value = "/register", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> registerUser(@RequestBody EmailRegisterRequest request) {
        try {
            UserRecord.CreateRequest createRequest = new UserRecord.CreateRequest()
                    .setEmail(request.getEmail())
                    .setPassword(request.getPassword())
                    .setDisplayName(request.getDisplayName());

            UserRecord userRecord = firebaseAuth.createUser(createRequest);
            String uid = userRecord.getUid();

            User newUser = new User(uid, request.getEmail(), request.getDisplayName(), request.getFcmToken());
            firestoreService.saveUser(newUser);

            String jwt = generateInternalJwt(uid);
            return ResponseEntity.ok(new AuthResponse(uid, jwt));
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Registration failed: " + e.getMessage());
        }
    }

    @Operation(summary = "Register with Google", description = "Verifies a Google ID token and creates a first-time account.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Sign in successful", content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "401", description = "Invalid credentials", content = @Content(schema = @Schema(implementation = String.class, example = "Registration failed: error message"))),
    })
    @PostMapping(value = "/register/google", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> exchangeGoogleRegisterToken(@RequestBody RegisterRequest request) {
        String idToken = request.getIdToken();
        String fcmToken = request.getFcmToken();

        try {
            String uid = firebaseAuthService.verifyFirebaseToken(idToken);
            UserRecord userRecord = firebaseAuth.getUser(uid);
            String email = userRecord.getEmail();
            String displayName = (userRecord.getDisplayName() != null) ? userRecord.getDisplayName() : "Gardener";

            User newUser = new User(uid, email, displayName, fcmToken);
            firestoreService.saveUser(newUser);

            String jwt = generateInternalJwt(uid);
            return ResponseEntity.ok(new AuthResponse(uid, jwt));
        } catch (Exception e) {
            return ResponseEntity.status(401).body("Registration failed: " + e.getMessage());
        }
    }

    @Operation(summary = "Login (Email or Google)", description = "Validates Firebase ID token and issues an internal JWT for Gateway authorization.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Login successful", content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "401", description = "Invalid credentials", content = @Content(schema = @Schema(implementation = String.class, example = "Login failed"))),
    })
    @PostMapping(value = {"/login/google", "/login/email"}, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        String idToken = request.getIdToken();
        String fcmToken = request.getFcmToken();

        try {
            String uid = firebaseAuthService.verifyFirebaseToken(idToken);

            if (fcmToken != null && !fcmToken.isEmpty()) {
                firestoreService.updateFcmToken(uid, fcmToken);
            }

            String jwt = generateInternalJwt(uid);
            return ResponseEntity.ok(new AuthResponse(uid, jwt));
        } catch (Exception e) {
            return ResponseEntity.status(401).body("Login failed");
        }
    }

    @Operation(summary = "Delete Account", description = "Triggers cascading deletion: calls Plant Service to remove plants, then cleans up User records.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Account fully deleted", content = @Content),
            @ApiResponse(responseCode = "500", description = "Partial deletion error", content = @Content(schema = @Schema(implementation = String.class, example = "Partial deletion occurred: error message")))
    })
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/me")
    public ResponseEntity<?> deleteAccount(
            @Parameter(description = "UID extracted from JWT by the Gateway (you should use the FirebaseUid you got from the register here)") @RequestHeader("X-User-Id") String uid) {
        try {
            webClientBuilder.build()
                    .delete()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("http")
                            .host("plant-service")
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