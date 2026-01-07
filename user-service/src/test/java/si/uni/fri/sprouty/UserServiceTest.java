package si.uni.fri.sprouty;

import com.google.api.core.ApiFutures;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.WriteResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.UserRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import si.uni.fri.sprouty.dto.AuthResponse;
import si.uni.fri.sprouty.dto.EmailRegisterRequest;
import si.uni.fri.sprouty.dto.User;
import si.uni.fri.sprouty.service.UserService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private Firestore db;
    @Mock private FirebaseAuth firebaseAuth;
    @Mock private RestTemplate restTemplate;
    @Mock private CollectionReference collectionReference;
    @Mock private DocumentReference documentReference;
    @Mock private UserRecord userRecord;

    @InjectMocks
    private UserService userService;

    @BeforeEach
    void setUp() {
        // Inject the secret key for JWT generation
        ReflectionTestUtils.setField(userService, "secretKey", "mySuperSecretKeyForTestingPurposes1234567890");
    }

    @Test
    void registerWithEmail_ShouldReturnAuthResponse() throws Exception {
        // Arrange
        EmailRegisterRequest request = new EmailRegisterRequest();
        request.setEmail("test@fri.uni-lj.si");
        request.setPassword("password123");
        request.setDisplayName("Janez");

        when(firebaseAuth.createUser(any(UserRecord.CreateRequest.class))).thenReturn(userRecord);
        when(userRecord.getUid()).thenReturn("test-uid");

        // Mock Firestore chain: db.collection().document().set().get()
        when(db.collection("users")).thenReturn(collectionReference);
        when(collectionReference.document(anyString())).thenReturn(documentReference);
        when(documentReference.set(any(User.class))).thenReturn(ApiFutures.immediateFuture(mock(WriteResult.class)));

        // Act
        AuthResponse response = userService.registerWithEmail(request);

        // Assert
        assertNotNull(response);
        assertEquals("test-uid", response.getFirebaseUid());
        assertNotNull(response.getToken());
        verify(firebaseAuth).createUser(any());
    }
}