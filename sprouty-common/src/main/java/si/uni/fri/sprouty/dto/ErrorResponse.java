package si.uni.fri.sprouty.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Standardized error response for all Sprouty microservices")
public record ErrorResponse(
        @Schema(example = "Error message", description = "Human-readable error message")
        String message,

        @Schema(example = "1704660000000", description = "Epoch timestamp of the error")
        long timestamp,

        @Schema(example = "409", description = "HTTP status code")
        int status
) {}