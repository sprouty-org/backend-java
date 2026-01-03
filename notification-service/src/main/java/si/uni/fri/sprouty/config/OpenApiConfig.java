package si.uni.fri.sprouty.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI notificationServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Sprouty Notification Service API")
                        .description("Dispatches push notifications and silent data syncs via Firebase Cloud Messaging (FCM).")
                        .version("v1.0"));
    }
}