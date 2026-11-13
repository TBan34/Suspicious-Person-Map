package suspiciouspersonmap.service.parsing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import suspiciouspersonmap.common.constant.ReportErrorEnum;
import suspiciouspersonmap.exception.ReportException;

/** 正規タグの肯定例と、誤分類しやすい文脈の境界を検証する。 */
class ReportTagClassifierTest {
    private final ReportTagClassifier classifier = new ReportTagClassifier();

    /**
     * 全24種類の正規タグについて明示された行為を分類できることを確認する。
     */
    @ParameterizedTest
    @CsvSource({
        "殺人事件が発生しました。,殺人", "強盗事件が発生しました。,強盗", "自転車が盗まれる事案が発生しました。,窃盗",
        "女性が男から殴られました。,暴行", "女性が男から腕を掴まれました。,身体接触",
        "女性が男から体を触られそうになりました。,身体接触未遂", "女性が男につきまとわれました。,つきまとい",
        "女性が男に追いかけられました。,追いかけ", "女性が男から「何年生？」と声を掛けられました。,不審な声かけ",
        "男が女性に「殺すぞ」と声をかけました。,威嚇", "男が卑わいな言葉をかけました。,わいせつな言動",
        "女性が男から身体を触られるわいせつ事案が発生しました。,痴漢",
        "女性が男からスカート内を盗撮されました。,盗撮", "男が下半身を露出しました。,露出",
        "男が包丁を持っていました。,刃物所持", "男が女性にスマートフォンを向けました。,撮影行為",
        "男が浴室をのぞいていました。,のぞき", "男が玄関内に押し入りました。,住居侵入",
        "男が修理業者を名乗り訪問しました。,不審な訪問", "男が前に立ち通行を妨げました。,通行妨害",
        "女児が男から接近されました。,不審な接近", "男が女性を凝視しました。,凝視",
        "男が住宅付近を徘徊していました。,徘徊", "男が大声で叫んでいました。,大声・奇声"
    })
    void classifiesEachCanonicalTag(String text, String tag) {
        assertThat(classifier.classify(text)).containsExactly(tag);
        assertThat(ReportTagClassifier.VALID_TAGS).hasSize(24);
    }

    /**
     * 同一行為の重複だけを抑制し、別行為のタグや接触未遂を保持することを確認する。
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "女性が男から「写真を撮らせてほしい」と声を掛けられました。|不審な声かけ",
        "男が自身の股間を衣服の上から触っていました。|わいせつな言動",
        "女性が男から「体を触らせてください」と言われました。|わいせつな言動",
        "女性が男から「体を触らせて」と言われ、肩を触られました。|わいせつな言動,身体接触",
        "女性が男から「下着何色」と声を掛けられました。|わいせつな言動",
        "女性が男から「下着何色」と声を掛けられ、体を触られました。|わいせつな言動,痴漢",
        "男がスカート内を盗撮し、カメラで撮影しました。|盗撮",
        "女性が男から身体を触られるわいせつ事案が発生しました。|痴漢",
        "男が女性につきまとい、走って追いかけました。|追いかけ",
        "男が下半身を露出して、卑わいな言葉をかけました。|露出,わいせつな言動",
        "女性が脱衣所をのぞかれ、男から携帯電話を向けられました。|のぞき,盗撮",
        "女性が男から肩を叩かれ、「これあげる」と声をかけられました。|身体接触,不審な声かけ",
        "女性が男から身体を触られました。|痴漢",
        "男が女性に向かって「殺すぞ」と大声で叫びました。|威嚇",
        "女児が男につきまとわれた。女児は大声で助けを求めた。|つきまとい",
        "男が女性につきまとった。女性が男を一喝したところ男は逃げた。|つきまとい",
        "女性が男に追いかけられ、女性は抵抗した。|追いかけ",
        "男が女性に「何年生？」と声をかけ、その後「殺すぞ」と脅した。|不審な声かけ,威嚇",
        "女性が男から腕を触られた。その後、男が別の男性を殴った。|身体接触,暴行",
        "女性が男から体を触られる事案が発生した。女性が男に抵抗した。|痴漢"
    })
    void appliesContextAndOverlapRules(String text, String expected) {
        assertThat(classifier.classify(text)).containsExactlyInAnyOrderElementsOf(Arrays.asList(expected.split(",")));
    }

    /**
     * 物品名を含むだけの衣服、不審者以外の人物による反応だけではタグを付けないことを確認する。
     */
    @ParameterizedTest
    @ValueSource(strings = {
        "男はカッターシャツを着用しています。", "男は包丁の絵のシャツを着用しています。",
        "女性は大声で助けを求めました。", "女性は悲鳴を上げました。",
        "男性は通行していました。"
    })
    void rejectsWhenNoAffirmativeSuspiciousPersonActionExists(String text) {
        ReportException error = assertThrows(ReportException.class, () -> classifier.classify(text));
        assertThat(error.getErrorType()).isEqualTo(ReportErrorEnum.UNCLASSIFIABLE_TAG);
    }

    /**
     * 全ての合意済み物品名で所持を認識することを確認する。
     */
    @ParameterizedTest
    @ValueSource(strings = {"刃物", "包丁", "ナイフ", "カッターナイフ", "鎌", "斧", "日本刀"})
    void recognizesExplicitBladePossession(String item) {
        assertThat(classifier.classify("男が" + item + "を所持していた。")).containsExactly("刃物所持");
    }

    /**
     * 重複抑制後の3種類は受け付け、4種類以上は切り詰めず拒否することを確認する。
     */
    @Test
    void rejectsMoreThanThreeDistinctTagsAfterDeduplication() {
        assertThat(classifier.classify("男が女性につきまとい追いかけ、下半身を露出して、卑わいな言葉をかけた。"))
            .containsExactlyInAnyOrder("追いかけ", "露出", "わいせつな言動");
        ReportException error = assertThrows(ReportException.class, () -> classifier.classify(
            "男が女性につきまとい追いかけ、下半身を露出して、卑わいな言葉をかけ、包丁を所持していた。"));
        assertThat(error.getErrorType()).isEqualTo(ReportErrorEnum.TOO_MANY_TAGS);
        assertThat(error.getDetail()).isEqualTo("タグ候補が上限の3件を超えています");
    }
}
