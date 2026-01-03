package si.uni.fri.sprouty.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI plantServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Sprouty Plant Service API")
                        .description("Microservice for AI plant identification and garden management.")
                        .version("v1.0"));
    }
}
