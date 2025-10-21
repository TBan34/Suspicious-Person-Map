/**
 * Report Entity
 */

@Entity
@Table(name = "reports")
public class Report {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String userId;
    private String title;
    private String address;
    private String detail;
    private double latitude;
    private double longitude;
    private LocalDateTime createdAt;

    // getter/setter
}
