package suspiciouspersonmap.service.parsing;

import static suspiciouspersonmap.common.constant.ReportErrorEnum.TOO_MANY_TAGS;
import static suspiciouspersonmap.common.constant.ReportErrorEnum.UNCLASSIFIABLE_TAG;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import suspiciouspersonmap.exception.ReportException;

/** 概要に明記された不審者側の行為を正規タグへ分類し、重複と件数を検証する。 */
@Component
public class ReportTagClassifier {
    /** DBへ保存できる正規タグ。分類結果とテストデータの整合性検証で使用する。 */
    public static final Set<String> CANONICAL_TAGS = Set.of(
        "殺人", "強盗", "窃盗", "暴行", "身体接触", "身体接触未遂", "つきまとい",
        "追いかけ", "不審な声かけ", "威嚇", "わいせつな言動", "痴漢", "盗撮", "露出",
        "刃物所持", "撮影行為", "のぞき", "住居侵入", "不審な訪問", "通行妨害",
        "不審な接近", "凝視", "徘徊", "大声・奇声"
    );

    // 鉤括弧内の発言を取得する。要求だけで接触・撮影・殺人などの実行タグを付けないためにも使用する。
    private static final Pattern QUOTE_PATTERN = Pattern.compile("「([^」]*)」");
    // 被害者・保護者・教員等の抵抗や呼びかけを文単位で除外し、不審者の行為として分類しない。
    private static final Pattern VICTIM_RESPONSE_PATTERN = Pattern.compile(
        "(?:女性|女児|男児|児童|被害者|保護者|教員|目撃した男性)(?:ら)?(?:が|は|から)"
        + "(?:(?:その)?男(?:に|を|に対して)|大声で|大きな声で|見知らぬ男に)*"
        + "(?:一喝|抵抗|助けを|悲鳴|叫ん|声をかけた|声を掛けた)[^。]*(?:。|$)"
    );
    // 「触れていない」「所持していない」など、明示的に否定された文節だけを分類対象から除外する。
    private static final Pattern NEGATED_ACTION_PATTERN = Pattern.compile(
        "[^。、]*?(?:触れていない|触っていない|触られていない|していない|されていない|"
        + "持っていない|所持していない|追いかけていない|つきまとっていない)[^。、]*"
    );

    // 被害者の身体部位への成立した接触。要求、自分の身体への接触、「～されそう」は対象外。
    private static final Pattern CONTACT_PATTERN = Pattern.compile(
        "(?:身体|体|腕|手|手のひら|肩|足首|足|顔|お腹|胸|臀部|尻)[^。、「」]{0,15}"
        + "(?:触られ|触れられ|掴まれ|つかまれ|押しあてられ|叩かれ|手を掛けられ)(?!そう|な)"
        + "|抱き(?:つ|付|着)かれ(?!そう|な)|抱きついたりされ|くすぐられ"
    );
    // 「触られそう」「抱きつかれそう」など、成立していない身体接触。
    private static final Pattern CONTACT_ATTEMPT_PATTERN = Pattern.compile(
        "(?:触られ|触れられ|掴まれ|つかまれ|抱き(?:つ|付|着)かれ)そう"
    );
    // 殺害予告、暴言、怒鳴り声など、相手への威嚇が明記された表現。
    private static final Pattern THREAT_PATTERN = Pattern.compile(
        "殺すぞ|生意気|暴言|脅[しすさ]|怒鳴|「わっ」と大声"
    );
    // 性的な発言または自身の下半身を触る行為。第三者への接触や露出とは別に扱う。
    private static final Pattern SEXUAL_REMARK_PATTERN = Pattern.compile(
        "卑猥|卑わい|キスしよう|下着何色|パパ活|服を脱げ|パンツ見せ|揉ませて"
        + "|触らせて|触っていい|自身の股間|下半身を触るところ|自分のズボンを下げよう"
    );
    // 殴る・蹴る・液体をかける等の暴行。単純な身体接触より具体的な行為として優先する。
    private static final Pattern ASSAULT_PATTERN = Pattern.compile(
        "暴行|殴られ|殴った|殴る|蹴られ|蹴った|蹴る|体当たり|唾をかけ|液体[^。]{0,12}かけ"
        + "|(?:脇腹|背中|日傘)を叩かれ|名札を引っ張られ"
    );
    // 継続して後をつける行為。「追いかけ」が同時に成立する場合は追いかけを優先する。
    private static final Pattern FOLLOW_PATTERN = Pattern.compile(
        "つきまと|付きまと|後をつけ|後を付け|後方から付けられ|付いてくる|ついてこられ|ついて来られ"
    );
    // 走るか否かを問わず、対象を追うことが明記された行為。
    private static final Pattern CHASE_PATTERN = Pattern.compile("追いかけ|追い掛け|追い回");
    // 全裸、性器・下半身を見せる等、露出が成立した表現。
    private static final Pattern EXPOSURE_PATTERN = Pattern.compile(
        "露出|全裸|(?:下半身|性器)を(?:見せ|出し|出す)"
    );
    // 実際の撮影、またはカメラ等を対象へ向けた行為。発言内の撮影依頼は事前に除外する。
    private static final Pattern CAMERA_ACTION_PATTERN = Pattern.compile(
        "撮影(?:され|した|する)|写真を撮られ|盗撮|"
        + "(?:スマートフォン|スマホ|携帯電話|カメラ)[^。]{0,25}(?:向け|撮影)"
    );
    // 覗く行為のひらがな・漢字表記。
    private static final Pattern PEEP_PATTERN = Pattern.compile("のぞ[かきくい]|覗[かきくい]");
    // 合意済みの刃物名と、所持・提示・使用の文脈を同時に要求する。「カッターシャツ」は対象外。
    private static final Pattern BLADE_POSSESSION_PATTERN = Pattern.compile(
        "(?:刃物|包丁|ナイフ|カッターナイフ|鎌|斧|日本刀)(?:を|[、 ])[^。]{0,12}"
        + "(?:持[つちっ]|所持|手に|取り出|突きつけ|振り回)"
    );
    // 呼び止め、質問等の発声行為。威嚇・性的発言・訪問に該当する場合は後段で重複を抑制する。
    private static final Pattern VOICE_PATTERN = Pattern.compile(
        "声を(?:かけ|掛け)|声かけ|声掛け|呼び止め|道を尋ね|と言われ|と言う"
    );
    // 相手不明の大声・奇声。明確な威嚇が同時に成立する場合は威嚇を優先する。
    private static final Pattern LOUD_VOICE_PATTERN = Pattern.compile(
        "(?:大声|大きな声)[^。]{0,15}(?:叫|出)|奇声"
    );

    // 以下は、それぞれ明記された単独行為を検出するパターン。
    private static final Pattern APPROACH_PATTERN = Pattern.compile("接近|近寄|近づ|近付");
    private static final Pattern STARE_PATTERN = Pattern.compile("凝視|見つめ続け");
    private static final Pattern OBSTRUCTION_PATTERN = Pattern.compile("通行を妨げ|制止させ|前に立ち");
    private static final Pattern INTRUSION_PATTERN = Pattern.compile(
        "住居侵入|玄関内に押し入|住宅に侵入|自宅に侵入"
    );
    private static final Pattern VISIT_PATTERN = Pattern.compile("訪問|来訪|業者を名乗");
    private static final Pattern THEFT_PATTERN = Pattern.compile("窃盗|盗まれ|置引き");
    private static final Pattern MURDER_PATTERN = Pattern.compile("殺人事件|殺害(?:された|した|する)");
    private static final Pattern ROBBERY_PATTERN = Pattern.compile("強盗");
    private static final Pattern WANDER_PATTERN = Pattern.compile("徘徊");
    // 性的な接触であることが明記された表現。成立時は一般的な「身体接触」を重複付与しない。
    private static final Pattern SEXUAL_CONTACT_PATTERN = Pattern.compile(
        "痴漢|わいせつ事案|(?:臀部|尻|胸)を(?:触|揉)|下着何色"
    );

    /**
     * 概要に明記された不審者側の行為を分類する。
     * @param summary 原文表記を保持した概要
     * @return 重複を除いた1～3件の正規タグ
     */
    public List<String> classify(String summary) {
        String offenderContext = ReportMessageTextUtils.normalizeForParsing(summary);
        offenderContext = VICTIM_RESPONSE_PATTERN.matcher(offenderContext).replaceAll("");
        offenderContext = NEGATED_ACTION_PATTERN.matcher(offenderContext).replaceAll("");
        // 発言そのものを評価するタグ以外では、引用内の要求を実行済みの行為として扱わない。
        String actionsOutsideQuotes = QUOTE_PATTERN.matcher(offenderContext).replaceAll("");

        Set<String> tags = new LinkedHashSet<>();
        addTagWhenMatched(tags, "殺人", MURDER_PATTERN, actionsOutsideQuotes);
        addTagWhenMatched(tags, "強盗", ROBBERY_PATTERN, actionsOutsideQuotes);
        addTagWhenMatched(tags, "窃盗", THEFT_PATTERN, actionsOutsideQuotes);
        addTagWhenMatched(tags, "暴行", ASSAULT_PATTERN, actionsOutsideQuotes);
        addTagWhenMatched(tags, "身体接触未遂", CONTACT_ATTEMPT_PATTERN, actionsOutsideQuotes);
        addContactTag(tags, offenderContext, actionsOutsideQuotes);

        addTagWhenMatched(tags, "追いかけ", CHASE_PATTERN, actionsOutsideQuotes);
        if (!tags.contains("追いかけ")) {
            addTagWhenMatched(tags, "つきまとい", FOLLOW_PATTERN, actionsOutsideQuotes);
        }
        addTagWhenMatched(tags, "威嚇", THREAT_PATTERN, offenderContext);
        addTagWhenMatched(tags, "わいせつな言動", SEXUAL_REMARK_PATTERN, offenderContext);
        addTagWhenMatched(tags, "露出", EXPOSURE_PATTERN, actionsOutsideQuotes);
        addTagWhenMatched(tags, "のぞき", PEEP_PATTERN, actionsOutsideQuotes);
        addCameraTag(tags, actionsOutsideQuotes);
        addTagWhenMatched(tags, "刃物所持", BLADE_POSSESSION_PATTERN, actionsOutsideQuotes);
        addTagWhenMatched(tags, "住居侵入", INTRUSION_PATTERN, actionsOutsideQuotes);
        if (!tags.contains("住居侵入")) {
            addTagWhenMatched(tags, "不審な訪問", VISIT_PATTERN, actionsOutsideQuotes);
        }
        addTagWhenMatched(tags, "通行妨害", OBSTRUCTION_PATTERN, actionsOutsideQuotes);
        addTagWhenMatched(tags, "凝視", STARE_PATTERN, actionsOutsideQuotes);
        addTagWhenMatched(tags, "徘徊", WANDER_PATTERN, actionsOutsideQuotes);
        if (!tags.contains("威嚇")) {
            addTagWhenMatched(tags, "大声・奇声", LOUD_VOICE_PATTERN, actionsOutsideQuotes);
        }
        addVoiceTag(tags, offenderContext, actionsOutsideQuotes);
        if (tags.isEmpty()) {
            addTagWhenMatched(tags, "不審な接近", APPROACH_PATTERN, actionsOutsideQuotes);
        }
        return validateTags(tags);
    }

    /**
     * 文ごとに成立した接触を調べ、性的接触・暴行との重複を抑制してタグを追加する。
     * レスポンス: なし。
     * @param tags 分類済みタグ
     * @param offenderContext 被害者側の反応と否定文節を除いた概要
     * @param actionsOutsideQuotes 引用内の発言を除いた行為記述
     */
    private void addContactTag(
            Set<String> tags,
            String offenderContext,
            String actionsOutsideQuotes) {
        // 引用内の句点を文の区切りにしないよう、引用内から句点だけを除去する。
        String textForSentenceSplit = QUOTE_PATTERN.matcher(offenderContext).replaceAll(matchResult ->
            Matcher.quoteReplacement(matchResult.group().replace("。", "")));
        for (String sentence : textForSentenceSplit.split("。")) {
            String completedActions = CONTACT_ATTEMPT_PATTERN
                .matcher(QUOTE_PATTERN.matcher(sentence).replaceAll(""))
                .replaceAll("");
            if (!CONTACT_PATTERN.matcher(completedActions).find()) {
                continue;
            }
            if (SEXUAL_CONTACT_PATTERN.matcher(sentence).find()) {
                tags.add("痴漢");
            } else if (!ASSAULT_PATTERN.matcher(completedActions).find()) {
                tags.add("身体接触");
            }
        }
        if (actionsOutsideQuotes.contains("痴漢")) {
            tags.add("痴漢");
        }
    }

    /**
     * 撮影行為を、場所・対象の文脈に応じて盗撮または撮影行為へ分類する。
     * レスポンス: なし。
     * @param tags 分類済みタグ
     * @param actionsOutsideQuotes 引用内の発言を除いた行為記述
     */
    private void addCameraTag(Set<String> tags, String actionsOutsideQuotes) {
        if (!CAMERA_ACTION_PATTERN.matcher(actionsOutsideQuotes).find()) {
            return;
        }
        String cameraTag = actionsOutsideQuotes.contains("盗撮")
                || actionsOutsideQuotes.contains("スカート内")
                || actionsOutsideQuotes.contains("脱衣所")
                || actionsOutsideQuotes.contains("浴室")
            ? "盗撮" : "撮影行為";
        tags.add(cameraTag);
    }

    /**
     * 通常の声かけを判定し、訪問・威嚇・性的発言との重複を抑制してタグを追加する。
     * レスポンス: なし。
     * @param tags 分類済みタグ
     * @param offenderContext 被害者側の反応と否定文節を除いた概要
     * @param actionsOutsideQuotes 引用内の発言を除いた行為記述
     */
    private void addVoiceTag(Set<String> tags, String offenderContext, String actionsOutsideQuotes) {
        boolean ordinaryQuoteFlg = containsOrdinaryQuote(offenderContext, actionsOutsideQuotes);
        boolean ordinaryVoiceWithoutQuoteFlg = !tags.contains("威嚇")
            && !tags.contains("わいせつな言動")
            && VOICE_PATTERN.matcher(actionsOutsideQuotes).find();
        if (!tags.contains("不審な訪問") && (ordinaryQuoteFlg || ordinaryVoiceWithoutQuoteFlg)) {
            tags.add("不審な声かけ");
        }
    }

    /**
     * 威嚇・性的発言ではない引用付きの声かけが含まれるか判定する。
     * @param offenderContext 被害者側の反応と否定文節を除いた概要
     * @param actionsOutsideQuotes 引用内の発言を除いた行為記述
     * @return 通常の引用付き声かけが含まれる場合はtrue
     */
    private boolean containsOrdinaryQuote(String offenderContext, String actionsOutsideQuotes) {
        Matcher quoteMatcher = QUOTE_PATTERN.matcher(offenderContext);
        while (quoteMatcher.find()) {
            // 発言直後の同一文節も確認し、「大声で怒鳴った」等を通常の声かけから除外する。
            String followingClause = offenderContext.substring(quoteMatcher.end()).split("[。、]", 2)[0];
            if (!SEXUAL_REMARK_PATTERN.matcher(quoteMatcher.group(1)).find()
                    && !THREAT_PATTERN.matcher(quoteMatcher.group(1)).find()
                    && !THREAT_PATTERN.matcher(followingClause).find()
                    && !followingClause.contains("大声")
                    && VOICE_PATTERN.matcher(actionsOutsideQuotes).find()) {
                return true;
            }
        }
        return false;
    }

    /**
     * タグが正規タグだけで構成され、重複除去後に1～3件であることを検証する。
     * @param classifiedTags 分類したタグ
     * @return 重複を除いた検証済みタグ
     */
    private List<String> validateTags(Set<String> classifiedTags) {
        if (classifiedTags.isEmpty()
                || classifiedTags.stream().anyMatch(tag -> !CANONICAL_TAGS.contains(tag))) {
            throw new ReportException(UNCLASSIFIABLE_TAG, "tags", "タグを判定できません");
        }
        if (classifiedTags.size() > 3) {
            throw new ReportException(TOO_MANY_TAGS, "tags", "タグ候補が上限の3件を超えています");
        }
        return List.copyOf(classifiedTags);
    }

    /**
     * 行為パターンが一致した場合に正規タグを追加する。レスポンス: なし。
     * @param tags 分類済みタグ
     * @param tag 追加する正規タグ
     * @param actionPattern 行為の判定パターン
     * @param targetText 判定対象文字列
     */
    private void addTagWhenMatched(
            Set<String> tags,
            String tag,
            Pattern actionPattern,
            String targetText) {
        if (actionPattern.matcher(targetText).find()) {
            tags.add(tag);
        }
    }
}
