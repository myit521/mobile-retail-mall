package com.sky.observability;

import com.sky.exception.BaseException;
import com.sky.handler.GlobalExceptionHandler;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.logging.LoggingInitializationContext;
import org.springframework.boot.logging.logback.LogbackLoggingSystem;
import org.springframework.core.env.StandardEnvironment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveLoggingTest {
    @Test
    void removesSecretsPaymentCiphertextAndPersonalData() {
        String canary = "canary-secret-9fca";
        String input = "password=" + canary + " token=Bearer abc.def.ghi apiKey=sk-live-123456 "
                + "ciphertext=PAYMENT-CIPHER-CANARY phone=13812345678 email=alice@example.com idCard=110101199001011234";
        String safe = SensitiveValueSanitizer.sanitize(input);
        assertThat(safe).doesNotContain(canary, "abc.def.ghi", "sk-live-123456", "PAYMENT-CIPHER-CANARY",
                        "13812345678", "alice@example.com", "110101199001011234")
                .contains("[REDACTED]");
    }

    @Test
    void removesQuotedMultilineAndStandaloneCredentialForms() {
        String input = "{\"PASSWORD\":\"secret value\",\"cipherText\":\"PAYMENT\\nCIPHER\"}\n"
                + "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjMifQ.signature\n"
                + "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI0NTYifQ.signature sk-live-ABC123 AKIAIOSFODNN7EXAMPLE";
        String safe = SensitiveValueSanitizer.sanitize(input);
        assertThat(safe).doesNotContain("secret value", "PAYMENT", "CIPHER", "eyJhbGciOiJIUzI1NiJ9",
                "sk-live-ABC123", "AKIAIOSFODNN7EXAMPLE");
    }

    @Test
    void removesShortJwtAndEscapeAwareQuotedSecrets() {
        String shortJwt = "eyJhbGciOiJub25lIn0.e30.signature";
        String input = "{\"PASSWORD\":\"prefix\\\\\\\"ESCAPED-QUOTE-CANARY\\\\tail\","
                + "\"nested\":\"{\\\"ApiKey\\\":\\\"line1\\\\nLINE2-CANARY\\\"}\"}\r\n"
                + shortJwt;

        String safe = SensitiveValueSanitizer.sanitize(input);

        assertThat(safe).doesNotContain(shortJwt, "ESCAPED-QUOTE-CANARY", "LINE2-CANARY", "prefix", "tail")
                .contains("[REDACTED]");
    }

    @Test
    void keepsOrdinaryVersionsAndIpAddressesWhileBoundingHostileInput() {
        String hostile = "{\"password\":\"" + "\\\\\\\"".repeat(100_000) + "FINAL-CANARY\"}";

        String safe = org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(
                Duration.ofSeconds(2), () -> SensitiveValueSanitizer.sanitize(hostile));

        assertThat(safe).doesNotContain("FINAL-CANARY");
        assertThat(SensitiveValueSanitizer.sanitize("release=1.2.3 host=192.168.1.10"))
                .isEqualTo("release=1.2.3 host=192.168.1.10");
    }

    @Test
    void onlyDeclaredCorrelationKeysCanEnterMdc() {
        assertThat(MdcContext.allowedKeys()).containsExactlyInAnyOrder("traceId", "eventId", "correlationId", "orderId");
        assertThat(MdcContext.allowedKeys()).doesNotContain("payload", "headers", "token");
    }

    @Test
    void productionLoggingIsJsonRollingAndMdcIsAllowlisted() throws Exception {
        String configuration = Files.readString(Path.of("src/main/resources/logback-spring.xml"));
        assertThat(configuration).contains("LogstashEncoder", "SizeAndTimeBasedRollingPolicy");
        assertThat(configuration).contains("<includeMdcKeyName>traceId</includeMdcKeyName>",
                "<includeMdcKeyName>eventId</includeMdcKeyName>",
                "<includeMdcKeyName>correlationId</includeMdcKeyName>",
                "<includeMdcKeyName>orderId</includeMdcKeyName>");
        assertThat(configuration).doesNotContain("<includeMdc>true</includeMdc>");
    }

    @Test
    void actualProductionConfigurationStartsAndEmitsOnlyAllowlistedMdc(@TempDir Path logDirectory) throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        Process process = new ProcessBuilder(java, "-cp", classpath,
                ProductionLoggingProbe.class.getName(), logDirectory.toString())
                .redirectErrorStream(true)
                .start();
        boolean finished = process.waitFor(20, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            process.waitFor(5, TimeUnit.SECONDS);
        }
        String processOutput = new String(process.getInputStream().readAllBytes());
        assertThat(finished).as(processOutput).isTrue();
        assertThat(process.exitValue()).as(processOutput).isZero();

        Path output = logDirectory.resolve("application.json");
        assertThat(output).exists();
        String line = Files.readAllLines(output).get(0);
        JSONObject json = JSON.parseObject(line);
        assertThat(json.getString("traceId")).isEqualTo("trace-prod");
        assertThat(json.getString("eventId")).isEqualTo("event-prod");
        assertThat(json).doesNotContainKey("payload");
        assertThat(line).doesNotContain("forbidden-payload");
    }

    public static final class ProductionLoggingProbe {
        private ProductionLoggingProbe() { }

        public static void main(String[] args) {
            System.setProperty("LOG_PATH", args[0]);
            LogbackLoggingSystem logging = new LogbackLoggingSystem(SensitiveLoggingTest.class.getClassLoader());
            StandardEnvironment environment = new StandardEnvironment();
            environment.setActiveProfiles("prod");
            logging.beforeInitialize();
            logging.initialize(new LoggingInitializationContext(environment), "classpath:logback-spring.xml", null);
            MDC.put("traceId", "trace-prod");
            MDC.put("eventId", "event-prod");
            MDC.put("payload", "forbidden-payload");
            LoggerFactory.getLogger("production-json-probe").info("probe");
            MDC.clear();
            logging.cleanUp();
        }
    }

    @Test
    void businessExceptionCanaryIsAbsentFromCapturedLogs() {
        String canary = "captured-secret-4c87";
        String formatted = captureBusinessLog("password=" + canary);
        assertThat(formatted).doesNotContain(canary).contains("[REDACTED]");
    }

    @Test
    void capturedLoggerRemovesShortJwtEscapedMultilineAndOversizedSecrets() {
        String shortJwt = "eyJhbGciOiJub25lIn0.e30.signature";
        String boundaryMessage = "{\"PaSsWoRd\":\"prefix\\\\\\\"ESCAPED-CANARY\\\\tail\"}\r\n"
                + "{\\\"apiKey\\\":\\\"line1\\\\nMULTILINE-CANARY\\\"} " + shortJwt;
        String boundaryLog = captureBusinessLog(boundaryMessage);
        assertThat(boundaryLog).doesNotContain(shortJwt, "ESCAPED-CANARY", "MULTILINE-CANARY", "prefix", "tail");

        String oversized = "{\"password\":\"" + "\\\\\\\"".repeat(100_000) + "OVERSIZED-CANARY\"}";
        String oversizedLog = org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(
                Duration.ofSeconds(2), () -> captureBusinessLog(oversized));
        assertThat(oversizedLog).doesNotContain("OVERSIZED-CANARY").contains("[REDACTED]");
        assertThat(oversizedLog.length()).isLessThan(5_000);
    }

    private String captureBusinessLog(String message) {
        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger)
                org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            new GlobalExceptionHandler().business(new BaseException(message));
        } finally {
            logger.detachAppender(appender);
        }
        assertThat(appender.list).hasSize(1);
        return appender.list.get(0).getFormattedMessage();
    }
}
