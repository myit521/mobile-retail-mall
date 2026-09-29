package com.sky.observability;

import java.util.regex.Pattern;

public final class SafeLogIdentifier {
    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:@/-]*");
    private static final String INVALID = "[INVALID]";

    private SafeLogIdentifier() {
    }

    public static String require(String value, String name, int maxLength) {
        if (value == null || value.length() > maxLength || !SAFE.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " must use safe characters and contain 1 to " + maxLength + " characters");
        }
        return value;
    }

    public static String forLog(Object value, int maxLength) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return text.length() <= maxLength && SAFE.matcher(text).matches() ? text : INVALID;
    }
}
