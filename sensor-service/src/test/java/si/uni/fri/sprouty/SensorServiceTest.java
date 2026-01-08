package si.uni.fri.sprouty;

import com.google.api.core.ApiFutures;
import com.google.cloud.firestore.*;
import com.google.firebase.cloud.StorageClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;
import si.uni.fri.sprouty.dto.MasterPlant;
import si.uni.fri.sprouty.dto.UserPlant;
import si.uni.fri.sprouty.service.SensorService;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SensorServiceTest {

    @Mock private Firestore db;
    @Mock private StorageClient storage;
    @Mock private RestTemplate restTemplate;

    @Mock private CollectionReference usersCollection;
    @Mock private CollectionReference masterCollection;
    @Mock private CollectionReference historyCollection; // Added this
    @Mock private Query query;
    @Mock private QuerySnapshot querySnapshot;
    @Mock private QueryDocumentSnapshot documentSnapshot;
    @Mock private DocumentReference plantDocRef;
    @Mock private DocumentSnapshot masterDocSnapshot;
    @Mock private DocumentReference masterDocRef;

    private SensorService sensorService;

    @BeforeEach
    void setUp() {
        sensorService = new SensorService(restTemplate, db, storage);
    }

    @Test
    void processSensorUpdate_ShouldUpdateFirestoreAndLogHistory() {
        String mac = "AABBCCDDEEFF";

        // 1. Mock finding the UserPlant by MAC
        when(db.collection("user_plants")).thenReturn(usersCollection);
        when(usersCollection.whereEqualTo("connectedSensorId", mac)).thenReturn(query);
        when(query.get()).thenReturn(ApiFutures.immediateFuture(querySnapshot));
        when(querySnapshot.isEmpty()).thenReturn(false);
        when(querySnapshot.getDocuments()).thenReturn(List.of(documentSnapshot));

        UserPlant plant = new UserPlant();
        plant.setSpeciesId("basil_01");
        plant.setOwnerId("user123");
        plant.setHealthStatus("Healthy");
        when(documentSnapshot.toObject(UserPlant.class)).thenReturn(plant);
        when(documentSnapshot.getReference()).thenReturn(plantDocRef);
        when(documentSnapshot.getId()).thenReturn("plant_doc_id");

        // 2. Mock Master Plant Thresholds
        when(db.collection("master_plants")).thenReturn(masterCollection);
        when(masterCollection.document("basil_01")).thenReturn(masterDocRef);
        when(masterDocRef.get()).thenReturn(ApiFutures.immediateFuture(masterDocSnapshot));

        MasterPlant master = new MasterPlant();
        master.setSoilH("30, 70");
        master.setMinT(15);
        master.setMaxT(30);
        when(masterDocSnapshot.toObject(MasterPlant.class)).thenReturn(master);

        // 3. Mock the updates and history collection
        when(plantDocRef.update(anyMap())).thenReturn(ApiFutures.immediateFuture(null));
        when(db.collection("sensor_history")).thenReturn(historyCollection);
        when(historyCollection.add(anyMap())).thenReturn(ApiFutures.immediateFuture(null));


        sensorService.processSensorUpdate(mac, 22.0, 50.0, 45.0);
        verify(plantDocRef).update(argThat(map ->
                map.get("healthStatus").equals("Healthy") &&
                        map.get("currentTemperature").equals(22.0)
        ));
        verify(historyCollection).add(argThat(map ->
                map.get("plantId").equals("plant_doc_id") &&
                        map.get("temperature").equals(22.0)
        ));
        verify(restTemplate, atLeastOnce()).postForEntity(anyString(), any(), eq(String.class));
    }
}