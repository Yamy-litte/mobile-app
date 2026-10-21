package com.example.libraryonline.utils;

import android.util.Patterns;

public final class InputValidator {
    private InputValidator() {}

    public static boolean isValidEmail(String email) {
        return email != null
                && email.trim().length() <= 254
                && Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches();
    }

    public static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * Returns a canonical Vietnamese mobile number in 0xxxxxxxxx format.
     * Formatting validation does not prove that the number is active or belongs to the user.
     */
    public static String normalizeVietnamPhone(String phone) {
        if (phone == null) return "";

        String normalized = phone.trim().replaceAll("[\\s().-]", "");
        if (normalized.startsWith("+84")) {
            normalized = "0" + normalized.substring(3);
        } else if (normalized.startsWith("84")) {
            normalized = "0" + normalized.substring(2);
        }
        return normalized;
    }

    public static boolean isValidVietnamPhone(String phone) {
        return normalizeVietnamPhone(phone).matches("0(3|5|7|8|9)[0-9]{8}");
    }

    public static boolean isValidPassword(String password) {
        return password != null && password.length() >= 8;
    }

    public static boolean isValidFullName(String value) {
        return value!=null && value.trim().length()>=2 && value.trim().length()<=120;
    }

    public static boolean isValidBirthDate(String value) {
        if(value==null || !value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))return false;
        java.text.SimpleDateFormat format=new java.text.SimpleDateFormat("yyyy-MM-dd",java.util.Locale.ROOT);
        format.setLenient(false);
        try {
            format.parse(value);
            return value.compareTo(format.format(new java.util.Date()))<=0;
        } catch(java.text.ParseException invalid) { return false; }
    }

    public static boolean isNonEmpty(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
