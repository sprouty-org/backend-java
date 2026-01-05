package si.uni.fri.sprouty.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Wrapper containing the created user plant and its global species metadata")
public class IdentificationResponse {

    @Schema(description = "The specific plant instance added to the user's garden")
    private UserPlant userPlant;

    @Schema(description = "The global botanical data for this species (from Firestore or OpenAI)")
    private MasterPlant masterPlant;
}