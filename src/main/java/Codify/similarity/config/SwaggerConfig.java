package Codify.similarity.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI CodifySimilarityAPI() {
        Info info = new Info()
                .title("Codify Similarity API")
                .description("Codify Similarity API 명세서")
                .version("1.0.0");

        // 추후에 Spring Security 관련 설정 필요
        return new OpenAPI()
                .addServersItem(new Server().url("/"))
                .info(info);
    }
}