package com.example.backend.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.backend.exception.GeocodingException;
import com.example.backend.model.GeoPoint;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GeocodeServiceTest {

    private static final String API_KEY = "test-secret-geocoding-api-key";
    private static final String PREFECTURE = "福岡県";
    private static final String MUNICIPALITY = "福岡市中央区";
    private static final String DISTRICT = "天神1丁目";
    private static final String ADDRESS_DETAILS = "1-1";

    /**
     * Geocoding 成功時のDEBUGログにAPIキーおよび完全なリクエストURIが含まれないことを確認する。
     */
    @Test
    void doesNotLogApiKeyOrRequestUri() {
        RestTemplate restTemplate = new RestTemplate() {
            /**
             * 外部通信を行わず、完全一致した座標を含む固定レスポンスを返す。
             *
             * @param url Geocoding API のリクエストURI
             * @param responseType レスポンスの変換先クラス
             * @return 完全一致した座標を含むテスト用レスポンス
             */
            @Override
            public <T> T getForObject(URI url, Class<T> responseType) {
                String response = """
                    {
                      "status": "OK",
                      "results": [
                        {
                          "types": ["street_address"],
                          "geometry": {
                            "location": {"lat": 33.5902, "lng": 130.4017}
                          }
                        }
                      ]
                    }
                    """;
                return responseType.cast(response);
            }
        };
        GeocodeService geocodeService = createGeocodeService(restTemplate);
        Logger logger = (Logger) LoggerFactory.getLogger(GeocodeService.class);
        Level originalLevel = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.setLevel(Level.DEBUG);
        logger.addAppender(appender);

        try {
            GeoPoint point = geocodeService.getLatLng(
                PREFECTURE,
                MUNICIPALITY,
                DISTRICT,
                ADDRESS_DETAILS
            );

            assertThat(point.getLatitude()).isEqualTo(33.5902);
            assertThat(point.getLongitude()).isEqualTo(130.4017);
            List<String> messages = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
            assertThat(messages).isNotEmpty();
            assertThat(messages).allSatisfy(message -> assertThat(message)
                .doesNotContain(API_KEY)
                .doesNotContain("key=")
                .doesNotContain("https://maps.googleapis.com/maps/api/geocode/json"));
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(originalLevel);
            appender.stop();
        }
    }

    /**
     * APIキー付きURIを含む通信例外が、上位でログ出力される例外のメッセージや原因に残らないことを確認する。
     */
    @Test
    void doesNotRetainApiKeyContainingCommunicationException() {
        RestTemplate restTemplate = new RestTemplate() {
            /**
             * APIキー付きURIを含む通信例外を送出し、情報漏えいの可能性を再現する。
             *
             * @param url Geocoding API のリクエストURI
             * @param responseType レスポンスの変換先クラス
             * @return 例外を送出するためレスポンスなし
             */
            @Override
            public <T> T getForObject(URI url, Class<T> responseType) {
                throw new RestClientException("通信失敗: " + url);
            }
        };
        GeocodeService geocodeService = createGeocodeService(restTemplate);

        GeocodingException exception = assertThrows(
            GeocodingException.class,
            () -> geocodeService.getLatLng(
                PREFECTURE,
                MUNICIPALITY,
                DISTRICT,
                ADDRESS_DETAILS
            )
        );

        assertThat(exception.getMessage())
            .contains("exceptionType=RestClientException")
            .doesNotContain(API_KEY)
            .doesNotContain("key=");
        assertThat(exception.getAddress())
            .isEqualTo("福岡県福岡市中央区天神1丁目1-1");
        assertThat(exception.getCause()).isNull();
    }

    /**
     * 許容する住所候補がすべて不採用の場合に、市区町村へ広げず専用例外を送出することを確認する。
     */
    @Test
    void throwsDedicatedExceptionWhenAllAcceptableCandidatesFail() {
        RestTemplate restTemplate = new RestTemplate() {
            /**
             * 外部通信を行わず、住所を特定できない固定レスポンスを返す。
             *
             * @param url Geocoding API のリクエストURI
             * @param responseType レスポンスの変換先クラス
             * @return ZERO_RESULTS を含むテスト用レスポンス
             */
            @Override
            public <T> T getForObject(URI url, Class<T> responseType) {
                return responseType.cast("{\"status\":\"ZERO_RESULTS\",\"results\":[]}");
            }
        };
        GeocodeService geocodeService = createGeocodeService(restTemplate);

        GeocodingException exception = assertThrows(
            GeocodingException.class,
            () -> geocodeService.getLatLng(
                PREFECTURE,
                MUNICIPALITY,
                DISTRICT,
                ADDRESS_DETAILS
            )
        );

        assertThat(exception.getMessage())
            .isEqualTo("入力された住所の範囲では有効な位置情報を取得できませんでした");
        assertThat(exception.getAddress())
            .isEqualTo("福岡県福岡市中央区天神1丁目1-1");
    }

    /**
     * 指定された RestTemplate とテスト用APIキーを持つ GeocodeService を生成する。
     *
     * @param restTemplate Geocoding API 呼び出しに使用する RestTemplate
     * @return テスト用APIキーを設定した GeocodeService
     */
    private GeocodeService createGeocodeService(RestTemplate restTemplate) {
        GeocodeService geocodeService = new GeocodeService(
            restTemplate,
            new ObjectMapper(),
            new GeocodingAddressCandidateGenerator(new AddressNormalizer())
        );
        ReflectionTestUtils.setField(geocodeService, "apiKey", API_KEY);
        return geocodeService;
    }
}
