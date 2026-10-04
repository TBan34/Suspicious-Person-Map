package suspiciouspersonmap;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:local_profile_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "local.google.geocoding-api-key=test-google-api-key",
        "local.line.channel-secret=test-line-channel-secret"
})
@ActiveProfiles("local-h2")
class LocalH2ProfileTest {

    /**
     * local-h2 Profile が外部サービスの実値に依存せず、H2 で起動できることを確認する。
     */
    @Test
    void contextLoadsWithLocalH2Profile() {
    }
}
