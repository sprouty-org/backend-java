package si.uni.fri.sprouty.controller;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.UserRecord;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import si.uni.fri.sprouty.model.User;
import si.uni.fri.sprouty.service.FirebaseAuthService;
import si.uni.fri.sprouty.service.FirestoreService;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

@RestController
@RequestMapping("/users")
public class AuthController {

    private final FirebaseAuthService firebaseAuthService;
    private final FirestoreService firestoreService;
    private final FirebaseAuth firebaseAuth = FirebaseAuth.getInstance();

    @Value("${jwt.secret}")
    private String secretKey;


    public AuthController(FirebaseAuthService firebaseAuthService, FirestoreService firestoreService) {
        this.firebaseAuthService = firebaseAuthService;
        this.firestoreService = firestoreService;
    }

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

    /**
     * POST /users/register/google
     * Handles first-time Google Sign-in and account creation.
     */
    @PostMapping(value = "/register/google", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> exchangeGoogleRegisterToken(@RequestBody Map<String, String> request) {
        String idToken = request.get("idToken");
        String fcmToken = request.get("fcmToken");

        try {
            // 1. Verify the Firebase/Google ID Token
            String uid = firebaseAuthService.verifyFirebaseToken(idToken);

            // 2. Fetch the user details from Firebase to populate our Firestore record
            UserRecord userRecord = firebaseAuth.getUser(uid);
            String email = userRecord.getEmail();
            String displayName = (userRecord.getDisplayName() != null) ? userRecord.getDisplayName() : "Gardener";

            // 3. Save to our Firestore database
            User newUser = new User(uid, email, displayName, fcmToken);
            firestoreService.saveUser(newUser);

            // 4. Generate the internal JWT for the Gateway
            String jwt = generateInternalJwt(uid);
            return ResponseEntity.ok(Map.of("firebaseUid", uid, "token", jwt));

        } catch (Exception e) {
            System.err.println("Google Registration failed: " + e.getMessage());
            return ResponseEntity.status(401).body("Registration failed: " + e.getMessage());
        }
    }

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

    private String generateInternalJwt(String firebaseUid) {
        byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        return Jwts.builder()
                .setSubject(firebaseUid)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 86400000))
                .signWith(Keys.hmacShaKeyFor(keyBytes), SignatureAlgorithm.HS256)
                .compact();
    }
}