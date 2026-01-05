package si.uni.fri.sprouty.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.List;

@Data
@AllArgsConstructor
@Schema(description = "The complete garden state for a user.")
public class GardenProfileResponse {
    private List<UserPlant> userPlants;
    private List<MasterPlant> masterPlants;
}