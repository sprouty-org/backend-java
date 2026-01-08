package si.uni.fri.sprouty.service;

import com.google.cloud.firestore.Firestore;
import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.google.firebase.auth.UserRecord;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
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

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);
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

    public AuthResponse registerWithEmail(EmailRegisterRequest request) {
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
            if (e.getAuthErrorCode() == AuthErrorCode.EMAIL_ALREADY_EXISTS) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists.");
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Firebase Auth error: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error during email registration for email: {}", request.getEmail(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Registration failed due to a system error.");
        }
    }

    public AuthResponse registerWithGoogle(RegisterRequest request) {
        try {
            String uid = verifyFirebaseToken(request.getIdToken());
            UserRecord userRecord = firebaseAuth.getUser(uid);

            String email = userRecord.getEmail();
            String displayName = (userRecord.getDisplayName() != null) ? userRecord.getDisplayName() : "Gardener";

            saveUserToFirestore(new User(uid, email, displayName, request.getFcmToken()));
            return new AuthResponse(uid, generateInternalJwt(uid));
        } catch (FirebaseAuthException e) {
            logger.warn("Google Token verification failed: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google Token verification failed.");
        } catch (Exception e) {
            logger.error("System error during Google registration", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "External authentication failed.");
        }
    }

    public AuthResponse login(LoginRequest request) {
        try {
            String uid = verifyFirebaseToken(request.getIdToken());
            updateFcmToken(uid, request.getFcmToken());
            return new AuthResponse(uid, generateInternalJwt(uid));
        } catch (FirebaseAuthException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired session. Please login again.");
        } catch (Exception e) {
            logger.error("Login processing error for token: ", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Login failed.");
        }
    }

    public void deleteUserFully(String uid) {
        try {
            String url = "http://plant-service/plants/internal/user?uid=" + uid;
            restTemplate.delete(url);

            db.collection("users").document(uid).delete().get();

            firebaseAuth.deleteUser(uid);

            logger.info("Successfully deleted user data for UID: {}", uid);
        } catch (RestClientException e) {
            logger.error("Failed to cascade deletion to Plant Service for UID: {}", uid, e);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Could not reach downstream services to complete deletion.");
        } catch (FirebaseAuthException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User record not found in Auth system.");
        } catch (Exception e) {
            logger.error("Critical failure during full user deletion for UID: {}", uid, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Partial deletion occurred. Manual cleanup required.");
        }
    }

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
                .expiration(new Date(System.currentTimeMillis() + 24 * 60 * 60 * 1000))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }
}