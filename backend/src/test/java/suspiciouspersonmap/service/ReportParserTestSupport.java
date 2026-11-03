package suspiciouspersonmap.service;

import suspiciouspersonmap.service.parsing.DateTimeParser;
import suspiciouspersonmap.service.parsing.ReportAddressExtractor;
import suspiciouspersonmap.service.parsing.ReportMessageParser;
import suspiciouspersonmap.service.parsing.ReportTagClassifier;

/** テストで実際の解析コンポーネントを組み合わせる。 */
public final class ReportParserTestSupport {
    /** インスタンス化を禁止する。 */
    private ReportParserTestSupport() {}

    /**
     * 外部依存のない解析器を生成する。
     * @return 実物の解析器
     */
    public static ReportMessageParser createParser() {
        AddressNormalizer addressNormalizer = new AddressNormalizer();
        DateTimeParser dateTimeParser = new DateTimeParser();
        ReportTagClassifier tagClassifier = new ReportTagClassifier();
        ReportAddressExtractor addressExtractor = new ReportAddressExtractor(addressNormalizer);
        return new ReportMessageParser(dateTimeParser, addressExtractor, tagClassifier);
    }
}
