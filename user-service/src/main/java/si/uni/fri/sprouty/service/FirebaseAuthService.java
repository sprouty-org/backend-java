package si.uni.fri.sprouty.service;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import org.springframework.stereotype.Service;

@Service
public class FirebaseAuthService {

    /**
     * Verifies a Firebase ID Token.
     * This works for tokens issued via Google Sign-In (once linked to Firebase)
     * and standard Email/Password login.
     */
    public String verifyFirebaseToken(String idToken) throws Exception {
        try {
            FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdToken(idToken);
            return decodedToken.getUid();
        } catch (FirebaseAuthException e) {
            System.err.println("Firebase Token verification failed: " + e.getMessage());
            throw new Exception("Invalid Firebase ID Token: " + e.getMessage());
        }
    }
}