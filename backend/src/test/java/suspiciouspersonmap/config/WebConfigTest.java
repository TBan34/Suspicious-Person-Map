package suspiciouspersonmap.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WebConfigTest {

    /**
     * 設定された Origin だけを許可し、Cookie などの資格情報を許可しないことを確認する。
     */
    @Test
    void registersConfiguredOriginsWithoutCredentials() {
        String[] allowedOrigins = {
            "http://localhost:5173",
            "https://map.example.invalid"
        };
        WebConfig webConfig = new WebConfig(allowedOrigins);
        InspectableCorsRegistry registry = new InspectableCorsRegistry();

        webConfig.addCorsMappings(registry);

        CorsConfiguration corsConfiguration = registry.getConfigurations().get("/api/**");
        assertThat(corsConfiguration).isNotNull();
        assertThat(corsConfiguration.getAllowedOrigins()).containsExactly(allowedOrigins);
        assertThat(corsConfiguration.getAllowedMethods())
            .containsExactly("GET", "POST", "PUT", "DELETE");
        assertThat(corsConfiguration.getAllowCredentials()).isNull();
    }

    /**
     * 登録された CORS 設定をテストから取得できる Registry。
     */
    private static class InspectableCorsRegistry extends CorsRegistry {

        /**
         * 登録されたパスごとの CORS 設定を取得する。
         *
         * @return パスをキーとする CORS 設定
         */
        private Map<String, CorsConfiguration> getConfigurations() {
            return getCorsConfigurations();
        }
    }
}
