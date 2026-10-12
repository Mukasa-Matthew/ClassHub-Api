package com.classhub.notification.delivery;

final class WhatsAppPhoneNormalizer {

    private WhatsAppPhoneNormalizer() {
    }

    static String toProviderNumber(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        String trimmed = phone.trim();
        boolean explicitInternational = trimmed.startsWith("+");
        String digits = trimmed.replaceAll("[^0-9]", "");

        if (digits.matches("07\\d{8}")) {
            return "256" + digits.substring(1);
        }
        if (digits.matches("7\\d{8}")) {
            return "256" + digits;
        }
        if (digits.matches("2567\\d{8}")) {
            return digits;
        }
        if (explicitInternational && digits.length() >= 8 && digits.length() <= 15) {
            return digits;
        }
        return null;
    }
}
