package com.sky.observability;

import java.util.regex.Pattern;

public final class SensitiveValueSanitizer {
    private static final int MAX_INPUT_LENGTH = 4_096;
    private static final String SECRET_NAME = "password|passwd|pwd|token|authorization|api[_-]?key|secret|ciphertext|resource";
    private static final Pattern SECRET_PREFIX = Pattern.compile(
            "(?i)[\\\"']?(?:" + SECRET_NAME + ")[\\\"']?\\s*[=:]\\s*");
    private static final Pattern NAMED_SECRET = Pattern.compile(
            "(?i)([\\\"']?(?:" + SECRET_NAME + ")[\\\"']?\\s*[=:]\\s*)(?:Bearer\\s+)?([^\\s,;}]+)");
    private static final Pattern BEARER = Pattern.compile("(?i)Bearer\\s+[A-Za-z0-9._~+/-]+=*");
    private static final Pattern JWT = Pattern.compile("(?<![A-Za-z0-9_-])eyJ[A-Za-z0-9_-]*\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+(?![A-Za-z0-9_-])");
    private static final Pattern API_KEY = Pattern.compile("(?i)(?<![A-Za-z0-9_-])(?:sk-[A-Za-z0-9_-]{8,}|AKIA[A-Z0-9]{16}|AIza[A-Za-z0-9_-]{20,}|gh[pousr]_[A-Za-z0-9]{20,}|xox[baprs]-[A-Za-z0-9-]{10,})(?![A-Za-z0-9_-])");
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern ID_CARD = Pattern.compile("(?<!\\d)\\d{17}[0-9Xx](?!\\d)");
    private SensitiveValueSanitizer() { }
    public static String sanitize(String value) {
        if (value == null) return null;
        String safe = value.length() <= MAX_INPUT_LENGTH
                ? value
                : value.substring(0, MAX_INPUT_LENGTH) + "[TRUNCATED]";
        safe = redactQuotedSecrets(safe);
        if (safe.contains("\\\"")) {
            safe = redactQuotedSecrets(safe.replace("\\\"", "\""));
        }
        safe = NAMED_SECRET.matcher(safe).replaceAll("$1[REDACTED]");
        safe = BEARER.matcher(safe).replaceAll("Bearer [REDACTED]");
        safe = JWT.matcher(safe).replaceAll("[REDACTED]");
        safe = API_KEY.matcher(safe).replaceAll("[REDACTED]");
        safe = PHONE.matcher(safe).replaceAll("[REDACTED]");
        safe = EMAIL.matcher(safe).replaceAll("[REDACTED]");
        return ID_CARD.matcher(safe).replaceAll("[REDACTED]");
    }

    private static String redactQuotedSecrets(String value) {
        StringBuilder safe = new StringBuilder(value.length());
        java.util.regex.Matcher matcher = SECRET_PREFIX.matcher(value);
        int copiedThrough = 0;
        int searchFrom = 0;
        while (matcher.find(searchFrom)) {
            int opening = matcher.end();
            if (opening >= value.length() || (value.charAt(opening) != '\"' && value.charAt(opening) != '\'')) {
                searchFrom = matcher.end();
                continue;
            }
            char quote = value.charAt(opening);
            int closing = findClosingQuote(value, opening + 1, quote);
            safe.append(value, copiedThrough, opening + 1).append("[REDACTED]");
            if (closing < 0) {
                safe.append(quote);
                copiedThrough = value.length();
                break;
            }
            safe.append(quote);
            copiedThrough = closing + 1;
            searchFrom = copiedThrough;
        }
        return safe.append(value, copiedThrough, value.length()).toString();
    }

    private static int findClosingQuote(String value, int from, char quote) {
        for (int i = from; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current == '\\') {
                i++;
            } else if (current == quote) {
                return i;
            }
        }
        return -1;
    }
}
