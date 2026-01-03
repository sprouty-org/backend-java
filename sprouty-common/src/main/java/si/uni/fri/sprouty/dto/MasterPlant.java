package si.uni.fri.sprouty.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class MasterPlant {
    private String id;
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
    private int waterInterval;
    private String growth;
    private String soil;
    private int maxHeight;
    private String careDifficulty;

    public MasterPlant() {}
}