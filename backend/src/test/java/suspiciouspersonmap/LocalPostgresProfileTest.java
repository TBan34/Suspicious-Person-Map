package suspiciouspersonmap;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
        "LOCAL_DB_URL=jdbc:h2:mem:local_postgres_profile_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "LOCAL_DB_USERNAME=sa",
        "LOCAL_DB_PASSWORD=",
        "GOOGLE_GEOCODING_API_KEY=test-google-api-key",
        "LINE_CHANNEL_SECRET=test-line-channel-secret"
})
@ActiveProfiles("local-postgres")
class LocalPostgresProfileTest {

    /**
     * local-postgres Profile が環境変数による設定上書きを解決して起動できることを確認する。
     */
    @Test
    void contextLoadsWithEnvironmentOverrides() {
    }
}
