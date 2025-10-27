@Service
public class ReportService {

    private final ReportRepository reportRepository;
    private final GeocodeService geocodeService;

    public ReportService(ReportRepository reportRepository, GeocodeService geocodeService) {
        this.reportRepository = reportRepository;
        this.geocodeService = geocodeService;
    }

    public void processReportMessage(String userId, String text) {
        // 簡易パース例
        String tag = extractLine(text, "分類:");
        String prefecture = extractLine(text, "都道府県:");
        String municipality = extractLine(text, "市区町村:");
        String district = extractLine(text, "丁目:");
        String addressDetails = extractLine(text, "番地以降:");
        String summary = extractLine(text, "不審者情報:");

        // 住所→緯度経度に変換
        // NOTE: 一般ユーザーが不審者情報を登録できるようにする際は、正確に住所登録できるよう追加考慮が必要。都道府県+番地のようなあり得ない住所は弾きたい。
        StringBuilder sb = new StringBuilder();
        sb.append(StringUtils.isEmpty(prefecture) ? "" : prefecture);
        sb.append(StringUtils.isEmpty(municipality) ? "" : municipality);
        sb.append(StringUtils.isEmpty(district) ? "" : district);
        sb.append(StringUtils.isEmpty(addressDetails) ? "" : addressDetails);
        String address = sb.toString();
        GeoPoint location = geocodeService.getLatLng(address);

        // DB保存
        Report report = new Report();
        report.setUserId(userId);
        report.setTag(tag);
        report.setPrefecture(prefecture);
        report.setMunicipality(municipality);
        report.setDistrict(district);
        report.setAddressDetails(addressDetails);
        report.setSummary(summary);
        report.setCreated(LocalDateTime.now());
        reportRepository.save(report);
    }

    private String extractLine(String text, String prefix) {
        for (String line : text.split("\n")) {
            if (line.startsWith(prefix)) {
                return line.replace(prefix, "").trim();
            }
        }
        return "";
    }
}
