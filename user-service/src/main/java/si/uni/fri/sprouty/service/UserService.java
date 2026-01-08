package si.uni.fri.sprouty.service;

import com.google.cloud.firestore.Firestore;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.google.firebase.auth.UserRecord;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import si.uni.fri.sprouty.dto.*;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
public class UserService {

    private final Firestore db;
    private final FirebaseAuth firebaseAuth;
    private final RestTemplate restTemplate;

    @Value("${jwt.secret}")
    private String secretKey;

    public UserService(Firestore db, FirebaseAuth firebaseAuth, RestTemplate restTemplate) {
        this.db = db;
        this.firebaseAuth = firebaseAuth;
        this.restTemplate = restTemplate;
    }

    // --- Authentication Logic ---

    public AuthResponse registerWithEmail(EmailRegisterRequest request) throws Exception {
        try {
            UserRecord.CreateRequest createRequest = new UserRecord.CreateRequest()
                    .setEmail(request.getEmail())
                    .setPassword(request.getPassword())
                    .setDisplayName(request.getDisplayName());

            UserRecord userRecord = firebaseAuth.createUser(createRequest);
            String uid = userRecord.getUid();

            saveUserToFirestore(new User(uid, request.getEmail(), request.getDisplayName(), request.getFcmToken()));
            return new AuthResponse(uid, generateInternalJwt(uid));
        } catch (FirebaseAuthException e) {
            if ("email-already-exists".equals(e.getAuthErrorCode().name().toLowerCase().replace("_", "-"))) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists.");
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Registration failed: " + e.getMessage());
        }
    }

    public AuthResponse registerWithGoogle(RegisterRequest request) throws Exception {
        try {
            String uid = verifyFirebaseToken(request.getIdToken());
            UserRecord userRecord = firebaseAuth.getUser(uid);

            String email = userRecord.getEmail();
            String displayName = (userRecord.getDisplayName() != null) ? userRecord.getDisplayName() : "Gardener";

            saveUserToFirestore(new User(uid, email, displayName, request.getFcmToken()));
            return new AuthResponse(uid, generateInternalJwt(uid));
        } catch (FirebaseAuthException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google Token verification failed.");
        }
    }

    public AuthResponse login(LoginRequest request) throws Exception {
        try {
            String uid = verifyFirebaseToken(request.getIdToken());
            updateFcmToken(uid, request.getFcmToken());
            return new AuthResponse(uid, generateInternalJwt(uid));
        } catch (FirebaseAuthException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired session. Please login again.");
        }
    }

    public void deleteUserFully(String uid) throws Exception {
        try {
            String url = "http://plant-service/plants/internal/user?uid=" + uid;
            restTemplate.delete(url);

            db.collection("users").document(uid).delete().get();

            firebaseAuth.deleteUser(uid);
        } catch (FirebaseAuthException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User record not found in Auth system.");
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Partial deletion occurred. System sync required.");
        }
    }

    // --- Internal Helpers ---

    private String verifyFirebaseToken(String idToken) throws FirebaseAuthException {
        FirebaseToken decodedToken = firebaseAuth.verifyIdToken(idToken);
        return decodedToken.getUid();
    }

    private void saveUserToFirestore(User user) throws Exception {
        db.collection("users").document(user.getUid()).set(user).get();
    }

    private void updateFcmToken(String uid, String fcmToken) throws Exception {
        if (fcmToken == null || fcmToken.isEmpty()) return;
        Map<String, Object> updates = new HashMap<>();
        updates.put("fcmToken", fcmToken);
        db.collection("users").document(uid).update(updates).get();
    }

    private String generateInternalJwt(String uid) {
        SecretKey key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(uid)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 86400000)) // 24h
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }
}