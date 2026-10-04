package suspiciouspersonmap.service;

import suspiciouspersonmap.exception.GeocodingException;
import suspiciouspersonmap.model.GeoPoint;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Google MapsのGeocoding APIを使用し、座標情報を取得する。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeocodeService {

    // Geocoding　API
    private static class GEOCODING_API {

        //　APIの返却ステータス
        private static class RESPONSE_STATUS {
            private static final String OK = "OK";
        }
    }

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final GeocodingAddressCandidateGenerator addressCandidateGenerator;

    @Value("${google.api.key}")
    private String apiKey;

    /**
     * 必須住所部分を維持した候補で Geocoding API を呼び出し、座標情報を取得する。
     *
     * @param prefecture 都道府県
     * @param municipality 市区町村
     * @param district 丁目
     * @param addressDetails 番地以降の任意情報
     * @return 採用条件を満たした住所候補の座標情報
     */
    public GeoPoint getLatLng(
            String prefecture,
            String municipality,
            String district,
            String addressDetails) {
        List<String> addressCandidates = addressCandidateGenerator.generate(
            prefecture,
            municipality,
            district,
            addressDetails
        );

        for (String addressCandidate : addressCandidates) {
            GeoPoint point = callGeocodingApi(addressCandidate);
            if (point != null) {
                log.debug("Geocoding APIの実行に成功しました: {}", addressCandidate);
                return point;
            }
        }

        String mostDetailedAddress = addressCandidates.get(0);
        throw new GeocodingException(
            "入力された住所の範囲では有効な位置情報を取得できませんでした",
            mostDetailedAddress
        );
    }

    /**
     * 指定された住所で Geocoding API を呼び出し、完全一致した座標を抽出する。
     *
     * @param address Geocoding API に渡す住所
     * @return 完全一致した座標情報。取得できない場合は null
     */
    private GeoPoint callGeocodingApi(String address) {
        try {
            URI uri = UriComponentsBuilder
                .fromUriString("https://maps.googleapis.com/maps/api/geocode/json")
                .queryParam("address", address)
                .queryParam("language", "ja")
                .queryParam("region", "jp")
                .queryParam("key", apiKey)
                .build()
                .encode(StandardCharsets.UTF_8)
                .toUri();
            log.debug("Geocoding API呼出し住所: {}", address);

            // Geocoding API実行
            String response = restTemplate.getForObject(uri, String.class);
            JsonNode jsonNode = objectMapper.readTree(response);
            
            // APIのステータスチェック
            String status = jsonNode.get("status").asText();
            // 異常終了または返却結果なしの場合
            if (!StringUtils.equals(GEOCODING_API.RESPONSE_STATUS.OK, status)) {

                String errorMessage = jsonNode.has("error_message")
                    ? jsonNode.get("error_message").asText()
                    : "no error_message";

                log.debug("Geocoding APIが異常終了または返却結果なし. status={}, error_message={}, address={}",
                    status, errorMessage, address);

                return null;
            }

            JsonNode results = jsonNode.get("results");
            log.debug("Geocoding results = {}", results);

            for (JsonNode result : results) {
                // 曖昧な情報の場合、次の取得結果へ
                if (result.path("partial_match").asBoolean()) {
                    continue;
                }

                // 道路情報の場合、次の取得結果へ
                boolean hasRoute = false;
                for (JsonNode type : result.path("types")) {
                    if (StringUtils.equals("route", type.asText())) {
                        hasRoute = true;
                    }
                }
                if (hasRoute) {
                    continue;
                }

                if (result.has("geometry")) {
                    JsonNode location = result.get("geometry").get("location");
                    double lat = location.get("lat").asDouble();
                    double lng = location.get("lng").asDouble();
                    return new GeoPoint(lat, lng);
                }
            }

            return null;

        // RestTemplateの通信エラー
        } catch (RestClientException e) {
            // RestClientExceptionのメッセージにはAPIキー付きURIが含まれる可能性があるため、原因例外を連結しない。
            throw new GeocodingException(
                "RestTemplateの通信エラーが発生しました。exceptionType="
                    + e.getClass().getSimpleName(),
                address
            );
        
        // 上記以外のエラー
        } catch (Exception e) {
            throw new GeocodingException("Geocoding APIの呼び出しに失敗しました", address, e);
        }
    }

}
