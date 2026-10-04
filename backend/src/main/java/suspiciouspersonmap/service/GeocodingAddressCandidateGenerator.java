package suspiciouspersonmap.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 構造化された住所項目から Geocoding 用の住所候補を生成する。
 */
@Component
@RequiredArgsConstructor
public class GeocodingAddressCandidateGenerator {

    // 番地以降の末尾に連続する余分なハイフンを、候補生成前に除去する。
    private static final Pattern TRAILING_HYPHEN_PATTERN = Pattern.compile("-+$");
    // 末尾の「-数字」を一段短縮するため、直前までをグループ1へ取得する（例: 1-2-3 → 1-2）。
    private static final Pattern TRAILING_NUMERIC_SEGMENT_PATTERN = Pattern.compile("^(.*)-\\d+$");

    private final AddressNormalizer addressNormalizer;

    /**
     * 必須住所部分を保持し、番地以降だけを末尾から段階的に短縮した候補を生成する。
     *
     * @param prefecture 都道府県
     * @param municipality 市区町村
     * @param district 丁目
     * @param addressDetails 番地以降の任意情報
     * @return 詳細な住所から順に並べ、重複を除いた住所候補
     */
    public List<String> generate(
            String prefecture,
            String municipality,
            String district,
            String addressDetails) {
        String normalizedPrefecture = addressNormalizer.normalize(prefecture);
        String normalizedMunicipality = addressNormalizer.normalize(municipality);
        String normalizedDistrict = addressNormalizer.normalize(district);
        String normalizedAddressDetails = addressNormalizer.normalize(addressDetails);

        if (normalizedPrefecture == null
                || normalizedMunicipality == null
                || normalizedDistrict == null) {
            throw new IllegalArgumentException("Geocodingに必要な住所項目が不足しています");
        }

        String baseAddress = normalizedPrefecture + normalizedMunicipality + normalizedDistrict;
        Set<String> candidates = new LinkedHashSet<>();

        if (normalizedAddressDetails != null) {
            String shortenedAddressDetails = TRAILING_HYPHEN_PATTERN
                .matcher(normalizedAddressDetails)
                .replaceAll("");

            while (!shortenedAddressDetails.isEmpty()) {
                candidates.add(baseAddress + shortenedAddressDetails);
                Matcher matcher = TRAILING_NUMERIC_SEGMENT_PATTERN.matcher(shortenedAddressDetails);
                if (!matcher.matches()) {
                    break;
                }
                shortenedAddressDetails = matcher.group(1);
            }
        }

        candidates.add(baseAddress);
        return List.copyOf(candidates);
    }
}
