package si.uni.fri.sprouty;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import si.uni.fri.sprouty.controller.UserController;
import si.uni.fri.sprouty.dto.AuthResponse;
import si.uni.fri.sprouty.service.UserService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @Test
    void register_ShouldReturn200_WhenValid() throws Exception {
        AuthResponse response = new AuthResponse("uid-123", "dummy-jwt");
        when(userService.registerWithEmail(any())).thenReturn(response);

        mockMvc.perform(post("/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@test.si\", \"password\":\"pass123\", \"displayName\":\"Test\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firebaseUid").value("uid-123"))
                .andExpect(jsonPath("$.token").value("dummy-jwt"));
    }

    @Test
    void login_ShouldHandleMultiplePaths() throws Exception {
        AuthResponse response = new AuthResponse("uid-123", "dummy-jwt");
        when(userService.login(any())).thenReturn(response);

        mockMvc.perform(post("/users/login/email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"firebase-token\", \"fcmToken\":\"fcm-123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firebaseUid").value("uid-123"));
    }

    @Test
    void deleteAccount_ShouldReturn204_WhenGatewayHeaderPresent() throws Exception {
        mockMvc.perform(delete("/users/me")
                        .header("X-User-Id", "uid-123")) // Simulate Gateway injection
                .andExpect(status().isNoContent());
    }
}