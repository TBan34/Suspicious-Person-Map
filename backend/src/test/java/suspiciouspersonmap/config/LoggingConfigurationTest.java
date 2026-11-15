package suspiciouspersonmap.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.util.LogbackMDCAdapter;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;
import ch.qos.logback.core.status.Status;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.BeanUtils;
import org.springframework.boot.logging.LoggingInitializationContext;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class LoggingConfigurationTest {

    @TempDir
    Path temporaryDirectory;

    private LoggerContext loggerContext;

    /**
     * 非同期の圧縮処理を完了させ、テスト専用ロガーのファイルを閉じる。
     * レスポンス: なし。
     */
    @AfterEach
    void stopLogging() {
        if (loggerContext != null) {
            loggerContext.stop();
        }
    }

    /**
     * 各運用Profileで標準出力を維持し、INFO・WARN・ERRORが対応するファイルだけに保存されることを確認する。
     *
     * @param profile 検証対象の運用Profile
     */
    @ParameterizedTest
    @ValueSource(strings = {"local-h2", "local-postgres", "prod"})
    void separatesLevelsWithoutDuplication(String profile) throws Exception {
        Logger logger = configureLogging(profile);
        logger.setLevel(Level.DEBUG);

        logger.debug("debug-message");
        logger.info("info-message-日本語");
        logger.warn("warn-message");
        logger.error("error-message", new IllegalStateException("test-failure"));

        assertThat(Files.readString(temporaryDirectory.resolve("info.log")))
            .contains("info-message-日本語")
            .doesNotContain("debug-message", "warn-message", "error-message");
        assertThat(Files.readString(temporaryDirectory.resolve("warn.log")))
            .contains("warn-message")
            .doesNotContain("debug-message", "info-message", "error-message");
        assertThat(Files.readString(temporaryDirectory.resolve("error.log")))
            .contains("error-message", "IllegalStateException: test-failure")
            .doesNotContain("debug-message", "info-message", "warn-message");
    }

    /**
     * test Profileではファイル出力先を指定しても、通常運用のログファイルを生成しないことを確認する。
     */
    @Test
    void doesNotWriteFilesInTestProfile() throws Exception {
        Logger logger = configureLogging("test");

        logger.info("test-info");
        logger.warn("test-warn");
        logger.error("test-error");

        assertThat(temporaryDirectory).isEmptyDirectory();
    }

    /**
     * 全レベルで10MB到達と翌日のログ到着により連番付きgzipへ切り替わり、内容を復元できることを確認する。
     */
    @Test
    void compressesArchivesOnSizeAndDateRollover() throws Exception {
        Logger logger = configureLogging("prod");
        LocalDate currentDate = LocalDate.now();
        String largeMessage = "x".repeat(10 * 1024 * 1024);
        long nextDay = currentDate.plusDays(1).atStartOfDay(ZoneId.systemDefault())
            .toInstant().toEpochMilli();

        for (Level level : List.of(Level.INFO, Level.WARN, Level.ERROR)) {
            String levelName = level.levelStr.toLowerCase(java.util.Locale.ROOT);
            TimeBasedRollingPolicy<ILoggingEvent> rollingPolicy = rollingPolicy(level);
            rollingPolicy.getTimeBasedFileNamingAndTriggeringPolicy().setCurrentTime(
                currentDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            );
            logger.atLevel(org.slf4j.event.Level.valueOf(level.levelStr)).log(largeMessage);
            logger.atLevel(org.slf4j.event.Level.valueOf(level.levelStr)).log("same-day-message");
            rollingPolicy.getTimeBasedFileNamingAndTriggeringPolicy().setCurrentTime(nextDay);
            logger.atLevel(org.slf4j.event.Level.valueOf(level.levelStr)).log("next-day-message");

            assertThat(Files.readString(temporaryDirectory.resolve(levelName + ".log")))
                .contains("next-day-message").doesNotContain("same-day-message");
        }
        loggerContext.stop();

        for (String levelName : List.of("info", "warn", "error")) {
            String archivePrefix = levelName + "." + currentDate;
            assertThat(readArchive(temporaryDirectory.resolve(archivePrefix + ".0.log.gz")))
                .contains(largeMessage);
            assertThat(readArchive(temporaryDirectory.resolve(archivePrefix + ".1.log.gz")))
                .contains("same-day-message").doesNotContain("next-day-message");
        }
    }

    /**
     * 起動時にINFO・WARNの30日超とERRORの90日超の過去ログを削除し、保持期間内のログを残すことを確認する。
     */
    @Test
    void removesExpiredArchivesOnStartup() throws Exception {
        LocalDate currentDate = LocalDate.now();
        Path expiredInfo = createArchive("info", currentDate.minusDays(40));
        Path expiredWarn = createArchive("warn", currentDate.minusDays(40));
        Path retainedError = createArchive("error", currentDate.minusDays(40));
        Path expiredError = createArchive("error", currentDate.minusDays(100));
        Path retainedInfo = createArchive("info", currentDate.minusDays(1));
        Path retainedWarn = createArchive("warn", currentDate.minusDays(1));

        configureLogging("prod");

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(expiredInfo).doesNotExist();
            assertThat(expiredWarn).doesNotExist();
            assertThat(expiredError).doesNotExist();
        });
        assertThat(retainedInfo).exists();
        assertThat(retainedWarn).exists();
        assertThat(retainedError).exists();
    }

    /**
     * アプリケーションのLogback設定を、指定Profileと一時保存先で独立したロガーへ読み込む。
     *
     * @param profile 有効にするProfile
     * @return 設定の読み込みが完了したテスト専用ロガー
     */
    private Logger configureLogging(String profile) throws Exception {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("logging.file.path", temporaryDirectory.toString());
        environment.setActiveProfiles(profile);
        loggerContext = new LoggerContext();
        loggerContext.setMDCAdapter(new LogbackMDCAdapter());
        loggerContext.start();

        // Spring拡張の実際の読み込み処理を使用する。Configuratorがpackage-privateのため反射で生成する。
        Class<? extends JoranConfigurator> configuratorClass = Class.forName(
            "org.springframework.boot.logging.logback.SpringBootJoranConfigurator"
        ).asSubclass(JoranConfigurator.class);
        JoranConfigurator configurator = BeanUtils.instantiateClass(
            configuratorClass.getDeclaredConstructor(LoggingInitializationContext.class),
            new LoggingInitializationContext(environment)
        );
        configurator.setContext(loggerContext);
        configurator.doConfigure(getClass().getResource("/logback-spring.xml"));
        assertThat(loggerContext.getStatusManager().getCopyOfStatusList())
            .noneMatch(status -> status.getLevel() == Status.ERROR);

        Logger logger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
        assertThat(logger.getAppender("CONSOLE")).isNotNull();
        // 大容量の検証データをテスト実行時の標準出力へ流さない。
        logger.detachAppender("CONSOLE");
        return logger;
    }

    /**
     * 指定レベルに対応する実際のローテーション設定を取得する。
     *
     * @param level ファイル出力対象のログレベル
     * @return 対応するファイルのローテーション設定
     */
    @SuppressWarnings("unchecked")
    private TimeBasedRollingPolicy<ILoggingEvent> rollingPolicy(Level level) {
        Logger logger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
        RollingFileAppender<ILoggingEvent> appender = (RollingFileAppender<ILoggingEvent>)
            logger.getAppender(level.levelStr + "_FILE");
        return (TimeBasedRollingPolicy<ILoggingEvent>) appender.getRollingPolicy();
    }

    /**
     * 保持期間検証用の圧縮済み過去ログを作成する。
     *
     * @param levelName ファイル名に使用するログレベル
     * @param archiveDate 過去ログの日付
     * @return 作成したgzipファイルのパス
     */
    private Path createArchive(String levelName, LocalDate archiveDate) throws IOException {
        Path archive = temporaryDirectory.resolve(levelName + "." + archiveDate + ".0.log.gz");
        try (GZIPOutputStream output = new GZIPOutputStream(Files.newOutputStream(archive))) {
            output.write("retention-test".getBytes(StandardCharsets.UTF_8));
        }
        return archive;
    }

    /**
     * 圧縮済み過去ログをUTF-8として復元する。
     *
     * @param archive 読み込むgzipファイルのパス
     * @return 圧縮前のログ内容
     */
    private String readArchive(Path archive) throws IOException {
        try (GZIPInputStream input = new GZIPInputStream(Files.newInputStream(archive))) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
