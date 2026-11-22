package com.leonardogalvao.pontocontrole.auth;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

public final class CredentialPolicy {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private CredentialPolicy() {
    }

    public static String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        return email.strip().toLowerCase(Locale.ROOT);
    }

    public static boolean isValidEmail(String email) {
        return email != null
                && email.length() >= 3
                && email.length() <= 254
                && EMAIL_PATTERN.matcher(email).matches();
    }

    public static boolean isValidPassword(String password) {
        if (password == null) {
            return false;
        }
        int utf8Bytes = password.getBytes(StandardCharsets.UTF_8).length;
        return utf8Bytes >= 12 && utf8Bytes <= 72;
    }
}
