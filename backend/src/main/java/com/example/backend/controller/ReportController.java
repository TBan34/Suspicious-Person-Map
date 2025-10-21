/**
 * Report Controller エンドポイント
 */

@RestController
@RequestMapping("/api/reports")
public class ReportController {
    private final ReportRepository reportRepository;

    public ReportController(ReportRepository reportRepository) {
        this.reportRepository = reportRepository;
    }

    @GetMapping
    public List<Report> getReports() {
        return reportRepository.findAll();
    }
}