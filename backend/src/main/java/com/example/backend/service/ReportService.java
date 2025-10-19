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
        String summary = extractLine(text, "事件概要:");

        // 住所→緯度経度に変換
        // todo: 都道府県〜番地以降の住所を繋げる
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
