package si.uni.fri.sprouty.service;

import com.google.api.core.ApiFutures;
import com.google.cloud.firestore.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlantServiceTest {

    @Mock private Firestore db;
    @Mock private RestTemplate restTemplate;

    // Firestore structure mocks
    @Mock private CollectionReference collectionReference;
    @Mock private DocumentReference documentReference;
    @Mock private DocumentSnapshot documentSnapshot;

    @InjectMocks
    @Spy // We use @Spy so we can mock specific internal methods
    private PlantService plantService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testIdentifyAndProcess_CacheMiss_CallsOpenAI() throws Exception {
        // --- ARRANGE ---
        String uid = "user123";
        byte[] imageBytes = new byte[]{0};
        String species = "Fiddle Leaf Fig";
        String masterId = "fiddle_leaf_fig";

        // 1. Stub Internal Methods
        doReturn(species).when(plantService).callPlantRecognitionApi(any());
        doReturn("https://fake.com/fiddle.jpg").when(plantService).uploadImageToStorage(any(), any());

        // 2. Mock Firestore via Reflection (using the same dbMock from the previous fix)
        Firestore dbMock = mock(Firestore.class, RETURNS_DEEP_STUBS);
        java.lang.reflect.Field field = PlantService.class.getDeclaredField("db");
        field.setAccessible(true);
        field.set(plantService, dbMock);

        // 3. Simulate MASTER DATA NOT FOUND
        DocumentReference masterDoc = dbMock.collection("master_plants").document(masterId);
        when(masterDoc.get()).thenReturn(ApiFutures.immediateFuture(documentSnapshot));
        when(documentSnapshot.exists()).thenReturn(false); // <--- The "Miss"

        // 4. Mock OpenAI Response
        String mockJsonResponse = "{\"choices\":[{\"message\":{\"content\":\"{" +
                "\\\"speciesName\\\":\\\"Fiddle Leaf Fig\\\", " +
                "\\\"waterInterval\\\": 10, " +
                "\\\"careDifficulty\\\": \\\"Hard\\\"}\"}}]}";

        // We need to mock the restTemplate specifically for the OpenAI call
        when(restTemplate.postForEntity(contains("openai.com"), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(mockJsonResponse));

        // 5. Mock the Saves using DISTINCT variables
        // For Master Save
        when(masterDoc.set(any())).thenReturn(ApiFutures.immediateFuture(null));

        // For User Save
        DocumentReference userDoc = dbMock.collection("user_plants").document();
        when(userDoc.getId()).thenReturn("new-user-plant-id");
        when(userDoc.set(any())).thenReturn(ApiFutures.immediateFuture(null));

        // --- ACT ---
        Map<String, Object> result = plantService.identifyAndProcess(uid, imageBytes);

        // --- ASSERT ---
        assertNotNull(result);

        // 1. Verify OpenAI was called
        verify(restTemplate, times(1)).postForEntity(contains("openai.com"), any(), any());

        // 2. THE FIX: Verify specifically that masterDoc was saved to
        // Use the specific mock object we created for the master collection
        verify(masterDoc).set(any(si.uni.fri.sprouty.dto.MasterPlant.class));

        // 3. Verify userDoc was saved to
        verify(userDoc).set(any(si.uni.fri.sprouty.dto.UserPlant.class));
    }
}