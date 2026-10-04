package suspiciouspersonmap.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeocodingAddressCandidateGeneratorTest {

    private final GeocodingAddressCandidateGenerator candidateGenerator =
        new GeocodingAddressCandidateGenerator(new AddressNormalizer());

    /**
     * 番地以降を右側から短縮し、必須住所部分を下限とする候補が順番に生成されることを確認する。
     */
    @Test
    void generatesCandidatesWithoutRemovingRequiredAddressParts() {
        List<String> candidates = candidateGenerator.generate(
            "福岡県",
            "福岡市中央区",
            "天神1丁目",
            "1-2-3"
        );

        assertThat(candidates).containsExactly(
            "福岡県福岡市中央区天神1丁目1-2-3",
            "福岡県福岡市中央区天神1丁目1-2",
            "福岡県福岡市中央区天神1丁目1",
            "福岡県福岡市中央区天神1丁目"
        );
    }

    /**
     * 番地以降がない場合に、入力された必須住所部分だけが候補になることを確認する。
     */
    @Test
    void usesBaseAddressOnlyWhenAddressDetailsAreMissing() {
        List<String> candidates = candidateGenerator.generate(
            "東京都",
            "町田市",
            "原町田6丁目",
            "　"
        );

        assertThat(candidates).containsExactly("東京都町田市原町田6丁目");
    }

    /**
     * 市や町を含む地名を行政区画として再解析せず、そのまま保持することを確認する。
     */
    @Test
    void preservesMunicipalityNamesContainingAdministrativeCharacters() {
        assertThat(candidateGenerator.generate("千葉県", "市川市", "八幡1丁目", null))
            .containsExactly("千葉県市川市八幡1丁目");
        assertThat(candidateGenerator.generate("東京都", "町田市", "原町田1丁目", null))
            .containsExactly("東京都町田市原町田1丁目");
    }

    /**
     * 末尾ハイフンを除去し、空または重複する候補を生成しないことを確認する。
     */
    @Test
    void removesTrailingHyphensWithoutCreatingInvalidCandidates() {
        List<String> candidates = candidateGenerator.generate(
            "福岡県",
            "福岡市中央区",
            "天神1丁目",
            "1-"
        );

        assertThat(candidates).containsExactly(
            "福岡県福岡市中央区天神1丁目1",
            "福岡県福岡市中央区天神1丁目"
        );
        assertThat(candidates).noneMatch(candidate -> candidate.endsWith("-"));
    }

    /**
     * 数字に挟まれた番と番地を正規化し、変換後の表記から短縮候補を生成することを確認する。
     */
    @Test
    void generatesCandidatesFromNormalizedJapaneseAddressNumberSeparators() {
        assertThat(candidateGenerator.generate(
            "福岡県",
            "福岡市中央区",
            "天神1丁目",
            "3番4"
        )).containsExactly(
            "福岡県福岡市中央区天神1丁目3-4",
            "福岡県福岡市中央区天神1丁目3",
            "福岡県福岡市中央区天神1丁目"
        );
        assertThat(candidateGenerator.generate(
            "福岡県",
            "福岡市中央区",
            "天神1丁目",
            "3番地4"
        )).containsExactly(
            "福岡県福岡市中央区天神1丁目3-4",
            "福岡県福岡市中央区天神1丁目3",
            "福岡県福岡市中央区天神1丁目"
        );
    }

    /**
     * 数字に挟まれていない番や番地を含む地名・建物名から変換候補を生成しないことを確認する。
     */
    @Test
    void preservesAddressAndBuildingNamesContainingBanCharacters() {
        assertThat(candidateGenerator.generate(
            "宮城県",
            "仙台市青葉区",
            "一番町4丁目",
            "3番館4"
        )).containsExactly(
            "宮城県仙台市青葉区一番町4丁目3番館4",
            "宮城県仙台市青葉区一番町4丁目"
        );
        assertThat(candidateGenerator.generate(
            "東京都",
            "千代田区",
            "三番町",
            "番地タワー4"
        )).containsExactly(
            "東京都千代田区三番町番地タワー4",
            "東京都千代田区三番町"
        );
    }

    /**
     * 必須住所項目が正規化後に空になる場合に候補生成を拒否することを確認する。
     */
    @Test
    void rejectsMissingRequiredAddressPart() {
        assertThatThrownBy(() -> candidateGenerator.generate("福岡県", "　", "天神1丁目", "1-1"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Geocodingに必要な住所項目が不足しています");
    }
}
