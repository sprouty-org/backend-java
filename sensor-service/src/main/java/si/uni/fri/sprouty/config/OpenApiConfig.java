package si.uni.fri.sprouty.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI sensorServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Sprouty Sensor Service API")
                        .description("Handles real-time environmental data ingestion and storage of sensor-captured images.")
                        .version("v1.0"));
    }
}