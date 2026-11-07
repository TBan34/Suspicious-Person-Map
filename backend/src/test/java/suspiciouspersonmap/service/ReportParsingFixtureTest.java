package suspiciouspersonmap.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import suspiciouspersonmap.service.parsing.ReportTagClassifier;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 手作業で定義したテストデータの整合性を、抽出結果の回帰テストとは独立に検証する。
 * このテストの成功は一次情報の文章からの抽出成功を意味しない。
 */
class ReportParsingFixtureTest {

    private static final Set<String> ERROR_TYPES = Set.of(
        "INVALID_DATETIME", "UNSUPPORTED_DATETIME", "INVALID_ADDRESS", "INVALID_SUMMARY",
        "UNCLASSIFIABLE_TAG", "TOO_MANY_TAGS"
    );

    /**
     * 確定済み・確認待ちを合わせて274件あり、IDの欠落・重複や原文の欠落がないことを確認する。
     */
    @Test
    void accountsForEverySourceSampleWithoutDuplicates() throws IOException {
        Set<String> identifiers = new HashSet<>();
        for (String resource : new String[]{"cases.json", "pending-cases.json"}) {
            for (JsonNode sample : loadFixture(resource).path("cases")) {
                assertThat(sample.path("id").isTextual()).isTrue();
                assertThat(identifiers.add(sample.path("id").asText()))
                    .as("重複ID: %s", sample.path("id")).isTrue();
                assertThat(sample.path("input").isTextual()).isTrue();
                assertThat(sample.path("input").asText()).isNotBlank();
            }
        }
        assertThat(identifiers).hasSize(274);
        for (int index = 1; index <= 274; index++) {
            assertThat(identifiers).contains("sample-%03d".formatted(index));
        }
    }

    /**
     * 確定済みケースには成功時の全項目または拒否理由の片方だけがあり、値が仕様の範囲内であることを確認する。
     */
    @Test
    void validatesExplicitExpectedResults() throws IOException {
        JsonNode fixture = loadFixture("cases.json");
        OffsetDateTime japanDateTime = OffsetDateTime.parse(fixture.path("japanDateTime").asText());
        assertThat(japanDateTime.getOffset().getTotalSeconds()).isEqualTo(9 * 60 * 60);

        for (JsonNode sample : fixture.path("cases")) {
            String identifier = sample.path("id").asText();
            assertThat(sample.has("expected") ^ sample.has("expectedError"))
                .as("期待値または拒否理由が必要: %s", identifier).isTrue();
            assertThat(sample.has("pendingDecisions")).as(identifier).isFalse();

            if (sample.has("expectedError")) {
                assertThat(ERROR_TYPES).as(identifier).contains(sample.path("expectedError").asText());
                continue;
            }

            JsonNode expected = sample.path("expected");
            assertThat(expected.isObject()).as(identifier).isTrue();
            assertThat(expected.size()).as(identifier).isEqualTo(7);
            for (String field : new String[]{"occurDate", "prefecture", "municipality", "district", "summary"}) {
                assertThat(expected.path(field).isTextual()).as("%s: %s", identifier, field).isTrue();
                assertThat(expected.path(field).asText()).as("%s: %s", identifier, field).isNotBlank();
            }
            LocalDateTime occurDate = LocalDateTime.parse(expected.path("occurDate").asText());
            assertThat(occurDate).as(identifier).isBeforeOrEqualTo(japanDateTime.toLocalDateTime());
            assertThat(expected.has("addressDetails")).as(identifier).isTrue();
            JsonNode addressDetails = expected.path("addressDetails");
            assertThat(addressDetails.isNull() || addressDetails.isTextual()).as(identifier).isTrue();
            if (addressDetails.isTextual()) {
                assertThat(addressDetails.asText()).as(identifier).isNotBlank();
            }
            JsonNode tags = expected.path("tags");
            assertThat(tags.isArray()).as(identifier).isTrue();
            assertThat(tags.size()).as(identifier).isBetween(1, 3);
            Set<String> uniqueTags = new HashSet<>();
            for (JsonNode tag : tags) {
                assertThat(tag.isTextual()).as(identifier).isTrue();
                assertThat(ReportTagClassifier.VALID_TAGS).as(identifier).contains(tag.asText());
                assertThat(uniqueTags.add(tag.asText())).as("重複タグ: %s", identifier).isTrue();
            }
        }
    }

    /**
     * 確認待ちをスキップして全件成功としないよう、未確定ケースが0件であることを確認する。
     */
    @Test
    void requiresEverySampleToHaveAnExpectedResult() throws IOException {
        assertThat(loadFixture("pending-cases.json").path("cases").size()).isZero();
    }

    /**
     * クラスパスからフィクスチャを読み込み、ケース配列の存在を確認する。
     *
     * @param resource report-parsing配下のファイル名
     * @return 読み込んだJSONのルートノード
     * @throws IOException リソースを読み込めない場合
     */
    private JsonNode loadFixture(String resource) throws IOException {
        try (InputStream stream = getClass().getResourceAsStream("/report-parsing/" + resource)) {
            assertThat(stream).as(resource).isNotNull();
            JsonNode fixture = new ObjectMapper().readTree(stream);
            assertThat(fixture.path("cases").isArray()).as(resource).isTrue();
            return fixture;
        }
    }
}
