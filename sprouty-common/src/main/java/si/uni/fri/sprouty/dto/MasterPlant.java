package si.uni.fri.sprouty.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Species care data generated via OpenAI AI analysis")
public class MasterPlant {
    @Schema(hidden = true)
    private String id;

    @Schema(example = "Monstera Deliciosa")
    private String speciesName;

    @Schema(description = "Botanical classification", allowableValues = {"Herb", "Shrub", "Tree", "Vine", "Succulent", "Cactus", "Fern", "Palm"})
    private String type;

    @Schema(description = "Biological life cycle", allowableValues = {"Annual", "Biennial", "Perennial"})
    private String life;

    @Schema(description = "Common uses", example = "['Air purification', 'Decorative foliage']")
    private List<String> uses;

    @Schema(description = "Detailed description of fruit", example = "Produces small red berries after two years of growth.")
    private String fruit;

    @Schema(description = "One historical or biological fact")
    private String fact;

    @Schema(description = "Detailed toxicity and safety warnings")
    private String tox;

    @Schema(description = "Minimum survival temperature", example = "15")
    private int minT;

    @Schema(description = "Maximum survival temperature", example = "35")
    private int maxT;

    @Schema(description = "Ideal light exposure", example = "Bright Indirect Light")
    private String light;

    @Schema(description = "Ideal soil moisture range (0-100)", example = "30,60")
    private String soilH;

    @Schema(description = "Ideal air humidity range (0-100)", example = "50,80")
    private String airH;

    @Schema(description = "Recommended days between watering", example = "7")
    private int waterInterval;

    @Schema(description = "Rate of growth", allowableValues = {"fast", "moderate", "slow"})
    private String growth;

    @Schema(description = "Specific soil composition preference")
    private String soil;

    @Schema(description = "Maximum growth height in centimeters", example = "200")
    private int maxHeight;

    @Schema(description = "Subjective care difficulty", allowableValues = {"Easy", "Medium", "Hard"})
    private String careDifficulty;

    public MasterPlant() {}
}