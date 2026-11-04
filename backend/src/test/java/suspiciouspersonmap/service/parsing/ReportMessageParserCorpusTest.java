package suspiciouspersonmap.service.parsing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import suspiciouspersonmap.exception.ReportException;
import suspiciouspersonmap.model.ParsedReportMessage;
import suspiciouspersonmap.service.ReportParserTestSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 固定した全274件の期待値を、実際の解析器へ入力して照合する。 */
class ReportMessageParserCorpusTest {

    /**
     * 全原文について登録項目または拒否理由が独立に定義した期待値と一致することを確認する。
     */
    @TestFactory
    List<DynamicTest> parsesEverySourceSample() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        try (InputStream pendingCasesInputStream = getClass()
                .getResourceAsStream("/report-parsing/pending-cases.json");
             InputStream casesInputStream = getClass()
                .getResourceAsStream("/report-parsing/cases.json")) {
            assertThat(pendingCasesInputStream).isNotNull();
            assertThat(casesInputStream).isNotNull();
            assertThat(objectMapper.readTree(pendingCasesInputStream).path("cases").size()).isZero();
            JsonNode fixtureRoot = objectMapper.readTree(casesInputStream);
            long eventTimestamp = OffsetDateTime.parse(fixtureRoot.path("japanDateTime").asText())
                .toInstant().toEpochMilli();
            List<DynamicTest> dynamicTests = new ArrayList<>();
            for (JsonNode testCase : fixtureRoot.path("cases")) {
                dynamicTests.add(DynamicTest.dynamicTest(testCase.path("id").asText(), () -> {
                    ReportMessageParser parser = ReportParserTestSupport.createParser();
                    if (testCase.has("expectedError")) {
                        ReportException error = assertThrows(ReportException.class,
                            () -> parser.parse(testCase.path("input").asText(), eventTimestamp));
                        assertThat(error.getErrorType().name())
                            .isEqualTo(testCase.path("expectedError").asText());
                    } else {
                        JsonNode expectedReport = testCase.path("expected");
                        ParsedReportMessage actualReport = parser.parse(
                            testCase.path("input").asText(), eventTimestamp);
                        assertThat(actualReport.occurDate()).isEqualTo(
                            LocalDateTime.parse(expectedReport.path("occurDate").asText()));
                        assertThat(actualReport.prefecture())
                            .isEqualTo(expectedReport.path("prefecture").asText());
                        assertThat(actualReport.municipality())
                            .isEqualTo(expectedReport.path("municipality").asText());
                        assertThat(actualReport.district())
                            .isEqualTo(expectedReport.path("district").asText());
                        assertThat(actualReport.addressDetails())
                            .isEqualTo(expectedReport.path("addressDetails").asText(null));
                        assertThat(actualReport.summary())
                            .isEqualTo(expectedReport.path("summary").asText());
                        List<String> expectedTags = new ArrayList<>();
                        expectedReport.path("tags")
                            .forEach(tag -> expectedTags.add(tag.asText()));
                        assertThat(actualReport.tags())
                            .containsExactlyInAnyOrderElementsOf(expectedTags);
                    }
                }));
            }
            assertThat(dynamicTests).hasSize(274);
            return dynamicTests;
        }
    }

}
