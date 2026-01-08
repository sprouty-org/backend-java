package si.uni.fri.sprouty.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import si.uni.fri.sprouty.dto.*;
import si.uni.fri.sprouty.service.UserService;

@RestController
@RequestMapping("/users")
@Tag(name = "Authentication", description = "Endpoints for user registration, login, and account management.")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "Register with Email/Password")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Registration successful", content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid registration data", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Email already in use", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal system error", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping(value = "/register", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AuthResponse> registerUser(@RequestBody EmailRegisterRequest request) {
        return ResponseEntity.ok(userService.registerWithEmail(request));
    }

    @Operation(summary = "Register with Google", description = "Verifies a Google ID token and creates a first-time account.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Sign in successful", content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "401", description = "Invalid Google ID token", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal system error", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping(value = "/register/google", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AuthResponse> exchangeGoogleRegisterToken(@RequestBody RegisterRequest request) {
        return ResponseEntity.ok(userService.registerWithGoogle(request));
    }

    @Operation(summary = "Login (Email or Google)", description = "Validates Firebase ID token and issues an internal JWT.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Login successful", content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "401", description = "Invalid or expired session", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal system error", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping(value = {"/login/google", "/login/email"}, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(userService.login(request));
    }

    @Operation(summary = "Delete Account", description = "Triggers cascading deletion: calls Plant Service to remove plants, then cleans up User records.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Account fully deleted"),
            @ApiResponse(responseCode = "404", description = "User not found in Auth system", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Partial deletion error or system failure", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "Plant Service unavailable", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteAccount(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") String uid) {
        userService.deleteUserFully(uid);
        return ResponseEntity.noContent().build();
    }
}