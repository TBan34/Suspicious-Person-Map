package suspiciouspersonmap.service.parsing;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import suspiciouspersonmap.common.constant.ReportErrorEnum;
import suspiciouspersonmap.exception.ReportException;

/** 概要に明記された不審者側の行為を正規タグへ分類し、重複と件数を検証する。 */
@Component
public class ReportTagClassifier {
    // 「」内の発言内容を取得する正規表現のキャプチャグループ名。
    private static final String SPOKEN_CONTENT_GROUP_NAME = "spokenContent";
    // 1件の不審者情報に保存できるタグの最大件数。
    private static final int MAX_TAG_COUNT = 3;

    /** DBへ保存できる有効なタグ。分類結果とテストデータの整合性検証で使用する。 */
    public static final Set<String> VALID_TAGS = Set.of(
        "殺人", "強盗", "窃盗", "暴行", "刃物所持",
        "身体接触", "身体接触未遂", "痴漢", "わいせつな言動", "露出", "のぞき", "盗撮", "撮影行為",
        "追いかけ", "つきまとい", "不審な接近", "凝視", "通行妨害", "徘徊",
        "不審な声かけ", "威嚇", "大声・奇声", "住居侵入", "不審な訪問"
    );

    // 「」内を発言部分として特定する。
    // 行為タグの判定時は発言部分を除外し、要求しただけの行為を実行済みとして扱わない。
    private static final Pattern SPOKEN_CONTENT_PATTERN = Pattern.compile(
        "「(?<" + SPOKEN_CONTENT_GROUP_NAME + ">[^」]*)」"
    );
    // 不審者以外の人物による反応を文単位で分類対象から除外する。
    private static final Pattern NON_SUSPICIOUS_PERSON_RESPONSE_PATTERN = Pattern.compile(
        "(?:女性|女児|男児|児童|被害者|保護者|教員|目撃した男性)(?:ら)?(?:が|は|から)"
        + "(?:(?:その)?男(?:に|を|に対して)|大声で|大きな声で|見知らぬ男に)*"
        + "(?:一喝|抵抗|助けを|悲鳴|叫ん|声をかけた|声を掛けた)[^。]*(?:。|$)"
    );

    // タグ：殺人。殺人事件または殺害行為を検出する。
    private static final Pattern MURDER_PATTERN = Pattern.compile("殺人事件|殺害(?:された|した|する)");
    // タグ：強盗。強盗が明記された表現を検出する。
    private static final Pattern ROBBERY_PATTERN = Pattern.compile("強盗");
    // タグ：窃盗。物品を盗む行為を検出する。
    private static final Pattern THEFT_PATTERN = Pattern.compile("窃盗|盗まれ|置引き");
    // タグ：暴行。殴る、蹴る、液体をかけるなどの行為を検出する。
    // 単純な身体接触より具体的な行為として優先する。
    private static final Pattern ASSAULT_PATTERN = Pattern.compile(
        "暴行|殴られ|殴った|殴る|蹴られ|蹴った|蹴る|体当たり|唾をかけ|液体[^。]{0,12}かけ"
        + "|(?:脇腹|背中|日傘)を叩かれ|名札を引っ張られ"
    );
    // タグ：刃物所持。合意済みの刃物名と、所持・提示・使用の文脈を検出する。
    // 「カッターシャツ」は対象外。
    private static final Pattern BLADE_POSSESSION_PATTERN = Pattern.compile(
        "(?:刃物|包丁|ナイフ|カッターナイフ|鎌|斧|日本刀)(?:を|[、 ])[^。]{0,12}"
        + "(?:持[つちっ]|所持|手に|取り出|突きつけ|振り回)"
    );

    // タグ：身体接触。被害者の身体部位への成立した接触を検出する。
    // 要求、自分の身体への接触、「～されそう」は対象外。
    private static final Pattern PHYSICAL_CONTACT_PATTERN = Pattern.compile(
        "(?:身体|体|腕|手|手のひら|肩|足首|足|顔|お腹|胸|臀部|尻)[^。、「」]{0,15}"
        + "(?:触られ|触れられ|掴まれ|つかまれ|押しあてられ|叩かれ|手を掛けられ)(?!そう)"
        + "|抱き(?:つ|付|着)かれ(?!そう)|抱きついたりされ|くすぐられ"
    );
    // タグ：身体接触未遂。「触られそう」「抱きつかれそう」などを検出する。
    private static final Pattern PHYSICAL_CONTACT_ATTEMPT_PATTERN = Pattern.compile(
        "(?:触られ|触れられ|掴まれ|つかまれ|抱き(?:つ|付|着)かれ)そう"
    );
    // タグ：痴漢。体・身体等への接触、または身体接触を伴うわいせつ事案を検出する。
    // 成立時は一般的な「身体接触」を重複付与しない。
    private static final Pattern SEXUAL_CONTACT_PATTERN = Pattern.compile(
        "わいせつ事案|(?:身体|体|臀部|尻|胸)を(?:触|揉)"
    );
    // タグ：わいせつな言動。性的な発言または自身の下半身を触る行為を検出する。
    // 第三者への接触や露出とは別に扱う。
    private static final Pattern SEXUAL_BEHAVIOR_PATTERN = Pattern.compile(
        "卑猥|卑わい|キスしよう|下着何色|パパ活|服を脱げ|パンツ見せ|揉ませて"
        + "|触らせて|触っていい|自身の股間|下半身を触るところ|自分のズボンを下げよう"
    );
    // タグ：露出。全裸、性器・下半身を見せるなどの行為を検出する。
    private static final Pattern EXPOSURE_PATTERN = Pattern.compile(
        "露出|全裸|(?:下半身|性器)を(?:見せ|出し|出す)"
    );
    // タグ：盗撮、撮影行為。実際の撮影、またはカメラ等を対象へ向けた行為を検出する。
    // 発言内の撮影依頼は事前に除外する。
    private static final Pattern CAMERA_ACTION_PATTERN = Pattern.compile(
        "撮影(?:され|した|する)|写真を撮られ|盗撮|"
        + "(?:スマートフォン|スマホ|携帯電話|カメラ)[^。]{0,25}(?:向け|撮影)"
    );
    // タグ：のぞき。覗く行為のひらがな・漢字表記を検出する。
    private static final Pattern PEEP_PATTERN = Pattern.compile("のぞ[かきくい]|覗[かきくい]");

    // タグ：追いかけ。走るか否かを問わず、対象を追う行為を検出する。
    private static final Pattern CHASE_PATTERN = Pattern.compile("追いかけ|追い掛け|追い回");
    // タグ：つきまとい。継続して後をつける行為を検出する。
    // 「追いかけ」が同時に成立する場合は追いかけを優先する。
    private static final Pattern FOLLOW_PATTERN = Pattern.compile(
        "つきまと|付きまと|後をつけ|後を付け|後方から付けられ|付いてくる|ついてこられ|ついて来られ"
    );
    // タグ：不審な接近。対象への接近を検出する。
    private static final Pattern APPROACH_PATTERN = Pattern.compile("接近|近寄|近づ|近付");
    // タグ：凝視。対象を見つめ続ける行為を検出する。
    private static final Pattern STARE_PATTERN = Pattern.compile("凝視|見つめ続け");
    // タグ：通行妨害。対象の通行を妨げる行為を検出する。
    private static final Pattern PASSAGE_OBSTRUCTION_PATTERN = Pattern.compile(
        "通行を妨げ|制止させ|前に立ち"
    );
    // タグ：徘徊。徘徊が明記された表現を検出する。
    private static final Pattern WANDER_PATTERN = Pattern.compile("徘徊");

    // タグ：不審な声かけ。呼び止め、質問などの発声行為を検出する。
    // 威嚇・性的発言・訪問に該当する場合は後段で重複を抑制する。
    private static final Pattern VOICE_PATTERN = Pattern.compile(
        "声を(?:かけ|掛け)|声かけ|声掛け|呼び止め|道を尋ね|と言われ|と言う"
    );
    // タグ：威嚇。殺害予告、暴言、怒鳴り声などを検出する。
    private static final Pattern THREAT_PATTERN = Pattern.compile(
        "殺すぞ|生意気|暴言|脅[しすさ]|怒鳴|「わっ」と大声"
    );
    // タグ：大声・奇声。相手不明の大声・奇声を検出する。
    // 明確な威嚇が同時に成立する場合は威嚇を優先する。
    private static final Pattern LOUD_VOICE_PATTERN = Pattern.compile(
        "(?:大声|大きな声)[^。]{0,15}(?:叫|出)|奇声"
    );

    // タグ：住居侵入。住居へ侵入する行為を検出する。
    private static final Pattern HOME_INTRUSION_PATTERN = Pattern.compile(
        "住居侵入|玄関内に押し入|住宅に侵入|自宅に侵入"
    );
    // タグ：不審な訪問。不審な訪問や業者を名乗る行為を検出する。
    private static final Pattern VISIT_PATTERN = Pattern.compile("訪問|来訪|業者を名乗");

    /**
     * 概要に明記された不審者側の行為を分類する。
     * @param summary 原文表記を保持した概要
     * @return 重複を除いた1～3件の正規タグ
     */
    public List<String> classify(String summary) {
        String suspiciousPersonContext = ReportMessageTextUtils.normalizeForParsing(summary);
        suspiciousPersonContext = NON_SUSPICIOUS_PERSON_RESPONSE_PATTERN
            .matcher(suspiciousPersonContext)
            .replaceAll("");
        // 不審者以外の人物による発言も行為として扱わないよう、発言者を問わず「」内を判定対象から除外する。
        // これにより、発言内で要求・言及された行為を、不審者が実行した行為として誤判定しない。
        String textWithoutSpokenContent = SPOKEN_CONTENT_PATTERN
            .matcher(suspiciousPersonContext)
            .replaceAll("");

        Set<String> tags = new LinkedHashSet<>();
        addTagWhenMatched(tags, "殺人", MURDER_PATTERN, textWithoutSpokenContent);
        addTagWhenMatched(tags, "強盗", ROBBERY_PATTERN, textWithoutSpokenContent);
        addTagWhenMatched(tags, "窃盗", THEFT_PATTERN, textWithoutSpokenContent);
        addTagWhenMatched(tags, "暴行", ASSAULT_PATTERN, textWithoutSpokenContent);
        addTagWhenMatched(tags, "身体接触未遂", PHYSICAL_CONTACT_ATTEMPT_PATTERN, textWithoutSpokenContent);
        addContactTag(tags, textWithoutSpokenContent);

        addTagWhenMatched(tags, "追いかけ", CHASE_PATTERN, textWithoutSpokenContent);
        // 同じ追跡行為に「追いかけ」と「つきまとい」を重複付与しない。
        if (!tags.contains("追いかけ")) {
            addTagWhenMatched(tags, "つきまとい", FOLLOW_PATTERN, textWithoutSpokenContent);
        }
        addTagWhenMatched(tags, "威嚇", THREAT_PATTERN, suspiciousPersonContext);
        addTagWhenMatched(tags, "わいせつな言動", SEXUAL_BEHAVIOR_PATTERN, suspiciousPersonContext);
        addTagWhenMatched(tags, "露出", EXPOSURE_PATTERN, textWithoutSpokenContent);
        addTagWhenMatched(tags, "のぞき", PEEP_PATTERN, textWithoutSpokenContent);
        addCameraTag(tags, textWithoutSpokenContent);
        addTagWhenMatched(tags, "刃物所持", BLADE_POSSESSION_PATTERN, textWithoutSpokenContent);
        addTagWhenMatched(tags, "住居侵入", HOME_INTRUSION_PATTERN, textWithoutSpokenContent);
        // 住居侵入を伴う訪問は、より具体的な「住居侵入」のみとする。
        if (!tags.contains("住居侵入")) {
            addTagWhenMatched(tags, "不審な訪問", VISIT_PATTERN, textWithoutSpokenContent);
        }
        addTagWhenMatched(tags, "通行妨害", PASSAGE_OBSTRUCTION_PATTERN, textWithoutSpokenContent);
        addTagWhenMatched(tags, "凝視", STARE_PATTERN, textWithoutSpokenContent);
        addTagWhenMatched(tags, "徘徊", WANDER_PATTERN, textWithoutSpokenContent);
        // 同じ発声に「威嚇」と「大声・奇声」を重複付与しない。
        if (!tags.contains("威嚇")) {
            addTagWhenMatched(tags, "大声・奇声", LOUD_VOICE_PATTERN, textWithoutSpokenContent);
        }
        addVoiceTag(tags, suspiciousPersonContext, textWithoutSpokenContent);
        // 「接近」「近寄る」等が明記され、他の具体的な行為がない場合だけ「不審な接近」を付与する。
        // 接近表現にも一致しない場合は、後続の検証でタグ判定不能エラーとする。
        if (tags.isEmpty()) {
            addTagWhenMatched(tags, "不審な接近", APPROACH_PATTERN, textWithoutSpokenContent);
        }
        validateTags(tags);
        return List.copyOf(tags);
    }

    /**
     * 各文の成立済み接触から「身体接触」または「痴漢」タグを判定して追加する。
     * 「痴漢」を「身体接触」より優先し、「暴行」に該当する接触へ「身体接触」を重複付与しない。
     * レスポンス: なし。
     * @param tags 判定したタグの追加先
     * @param textWithoutSpokenContent 発言部分を除いた行為タグ判定用テキスト
     */
    private void addContactTag(
            Set<String> tags,
            String textWithoutSpokenContent) {
        for (String sentence : textWithoutSpokenContent.split("。")) {
            // 「触られそう」等の未遂表現を除外し、成立済みの身体接触だけを判定する。
            String sentenceWithoutContactAttempts = PHYSICAL_CONTACT_ATTEMPT_PATTERN
                .matcher(sentence)
                .replaceAll("");
            if (!PHYSICAL_CONTACT_PATTERN.matcher(sentenceWithoutContactAttempts).find()) {
                continue;
            }

            // 痴漢に該当する場合は、「身体接触」ではなく「痴漢」を付与する。
            if (SEXUAL_CONTACT_PATTERN.matcher(sentence).find()) {
                tags.add("痴漢");
                continue;
            }
            // 「暴行」はclassify()の前段で追加済みのため、「身体接触」は重複付与しない。
            if (ASSAULT_PATTERN.matcher(sentenceWithoutContactAttempts).find()) {
                continue;
            }

            tags.add("身体接触");
        }
    }

    /**
     * 撮影行為を、場所・対象の文脈に応じて盗撮または撮影行為へ分類する。
     * レスポンス: なし。
     * @param tags 判定したタグの追加先
     * @param textWithoutSpokenContent 発言部分を除いた行為タグ判定用テキスト
     */
    private void addCameraTag(Set<String> tags, String textWithoutSpokenContent) {
        if (!CAMERA_ACTION_PATTERN.matcher(textWithoutSpokenContent).find()) {
            return;
        }
        String cameraTag = textWithoutSpokenContent.contains("盗撮")
                || textWithoutSpokenContent.contains("スカート内")
                || textWithoutSpokenContent.contains("脱衣所")
                || textWithoutSpokenContent.contains("浴室")
            ? "盗撮" : "撮影行為";
        tags.add(cameraTag);
    }

    /**
     * 通常の声かけを判定し、訪問・威嚇・性的発言との重複を抑制してタグを追加する。
     * レスポンス: なし。
     * @param tags 判定したタグの追加先
     * @param suspiciousPersonContext 不審者の行為・発言を判定するための概要
     * @param textWithoutSpokenContent 発言部分を除いた行為タグ判定用テキスト
     */
    private void addVoiceTag(
            Set<String> tags,
            String suspiciousPersonContext,
            String textWithoutSpokenContent) {
        // 訪問時の会話は「不審な訪問」に含め、同じ行為へ「不審な声かけ」を重複付与しない。
        if (!tags.contains("不審な訪問")
                && containsOrdinaryVoice(suspiciousPersonContext, textWithoutSpokenContent)) {
            tags.add("不審な声かけ");
        }
    }

    /**
     * 威嚇・性的発言ではない通常の声かけが含まれるか判定する。
     * @param suspiciousPersonContext 不審者の行為・発言を判定するための概要
     * @param textWithoutSpokenContent 発言部分を除いた行為タグ判定用テキスト
     * @return 通常の声かけが含まれる場合はtrue
     */
    private boolean containsOrdinaryVoice(
            String suspiciousPersonContext,
            String textWithoutSpokenContent) {
        if (!VOICE_PATTERN.matcher(textWithoutSpokenContent).find()) {
            return false;
        }

        // 「」内の発言を順に確認し、威嚇・性的発言ではない通常の声かけを探す。
        Matcher spokenContentMatcher = SPOKEN_CONTENT_PATTERN.matcher(suspiciousPersonContext);
        while (spokenContentMatcher.find()) {
            String spokenContent = spokenContentMatcher.group(SPOKEN_CONTENT_GROUP_NAME);
            if (SEXUAL_BEHAVIOR_PATTERN.matcher(spokenContent).find()
                    || THREAT_PATTERN.matcher(spokenContent).find()) {
                continue;
            }
            return true;
        }

        // 「」内に通常発言がない場合は、地の文だけで記載された通常の声かけかを判定する。
        // 例:「男から声を掛けられ、腕を掴まれる」は通常の声かけとして扱う。
        return !THREAT_PATTERN.matcher(suspiciousPersonContext).find()
            && !SEXUAL_BEHAVIOR_PATTERN.matcher(suspiciousPersonContext).find();
    }

    /**
     * 重複除去後のタグが1件以上、上限件数以内であることを検証する。レスポンス: なし。
     * @param classifiedTags 分類したタグ
     */
    private void validateTags(Set<String> classifiedTags) {
        if (classifiedTags.isEmpty()) {
            throw new ReportException(ReportErrorEnum.UNCLASSIFIABLE_TAG, "tags", "タグを判定できません");
        } else if (classifiedTags.size() > MAX_TAG_COUNT) {
            throw new ReportException(
                ReportErrorEnum.TOO_MANY_TAGS,
                "tags",
                "タグ候補が上限の" + MAX_TAG_COUNT + "件を超えています"
            );
        }
    }

    /**
     * 行為パターンが一致した場合に正規タグを追加する。レスポンス: なし。
     * @param tags 判定したタグの追加先
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
