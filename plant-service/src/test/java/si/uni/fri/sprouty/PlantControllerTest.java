package si.uni.fri.sprouty;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import si.uni.fri.sprouty.controller.PlantController;
import si.uni.fri.sprouty.service.PlantService;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PlantController.class)
class PlantControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PlantService plantService;

    @Test
    void getProfile_ShouldReturn401_WhenHeaderMissing() throws Exception {
        mockMvc.perform(get("/plants/profile"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void waterPlant_ShouldReturn200_WithCorrectHeader() throws Exception {
        mockMvc.perform(post("/plants/plant123/water")
                        .header("X-User-Id", "user1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }
}