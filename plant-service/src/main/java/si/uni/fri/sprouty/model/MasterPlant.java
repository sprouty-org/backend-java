package si.uni.fri.sprouty.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class MasterPlant {
    private String id; // Unique Global Plant ID
    private String speciesName;
    private String type;
    private String life;
    private String fruit;
    private List<String> uses;
    private String fact;
    private String tox;
    private int minT;
    private int maxT;
    private String light;
    private String soilH;
    private String airH;

    @JsonProperty("water_interval")
    private int waterInterval; // Match the JSON key exactly

    private String growth;
    private String soil;

    @JsonProperty("maxHeight")
    private int maxHeight;

    public MasterPlant() {}
}