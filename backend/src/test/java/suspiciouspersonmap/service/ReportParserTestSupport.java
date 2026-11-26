package suspiciouspersonmap.service;

import suspiciouspersonmap.service.parsing.ReportTagClassifier;
import suspiciouspersonmap.service.parsing.ReportTextAddressParser;
import suspiciouspersonmap.service.parsing.ReportTextDateTimeParser;
import suspiciouspersonmap.service.parsing.ReportTextParser;

/** テストで実際の解析コンポーネントを組み合わせる。 */
public final class ReportParserTestSupport {
    /** インスタンス化を禁止する。 */
    private ReportParserTestSupport() {}

    /**
     * 外部依存のない解析器を生成する。
     * @return 実物の解析器
     */
    public static ReportTextParser createParser() {
        AddressNormalizer addressNormalizer = new AddressNormalizer();
        ReportTextDateTimeParser dateTimeParser = new ReportTextDateTimeParser();
        ReportTagClassifier tagClassifier = new ReportTagClassifier();
        ReportTextAddressParser addressParser = new ReportTextAddressParser(addressNormalizer);
        return new ReportTextParser(dateTimeParser, addressParser, tagClassifier);
    }
}
