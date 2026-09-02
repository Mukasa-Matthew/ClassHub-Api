package com.classhub.notification.config;

import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "classhub.notifications")
public class NotificationProperties {

    private boolean enabled = true;
    private Email email = new Email();
    private WhatsApp whatsapp = new WhatsApp();
    private Push push = new Push();
    private Reminders reminders = new Reminders();
    private Delivery delivery = new Delivery();
    private String timezone = "Africa/Kampala";
    private String webBaseUrl = "http://localhost:5173";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Email getEmail() {
        return email;
    }

    public void setEmail(Email email) {
        this.email = email;
    }

    public WhatsApp getWhatsapp() {
        return whatsapp;
    }

    public void setWhatsapp(WhatsApp whatsapp) {
        this.whatsapp = whatsapp;
    }

    public Push getPush() {
        return push;
    }

    public void setPush(Push push) {
        this.push = push;
    }

    public Reminders getReminders() {
        return reminders;
    }

    public void setReminders(Reminders reminders) {
        this.reminders = reminders;
    }

    public Delivery getDelivery() {
        return delivery;
    }

    public void setDelivery(Delivery delivery) {
        this.delivery = delivery;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public String getWebBaseUrl() {
        return webBaseUrl;
    }

    public void setWebBaseUrl(String webBaseUrl) {
        this.webBaseUrl = webBaseUrl;
    }

    public ZoneId zoneId() {
        return ZoneId.of(timezone);
    }

    public static class Email {
        private boolean enabled = false;
        private String apiUrl = "https://api.brevo.com/v3/smtp/email";
        private String apiKey;
        private String senderName = "ClassHub";
        private String senderEmail;
        private String replyToEmail;
        private boolean sandbox = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getApiUrl() { return apiUrl; }
        public void setApiUrl(String apiUrl) { this.apiUrl = apiUrl; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getSenderName() { return senderName; }
        public void setSenderName(String senderName) { this.senderName = senderName; }
        public String getSenderEmail() { return senderEmail; }
        public void setSenderEmail(String senderEmail) { this.senderEmail = senderEmail; }
        public String getReplyToEmail() { return replyToEmail; }
        public void setReplyToEmail(String replyToEmail) { this.replyToEmail = replyToEmail; }
        public boolean isSandbox() { return sandbox; }
        public void setSandbox(boolean sandbox) { this.sandbox = sandbox; }
    }

    public static class WhatsApp {
        private boolean enabled = false;
        private String baseUrl = "https://wa.sopraent.com";
        private String apiKey;
        private String deviceId;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getDeviceId() { return deviceId; }
        public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    }

    public static class Push {
        private boolean enabled = false;
        private String vapidPublicKey;
        private String vapidPrivateKey;
        private String subject;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getVapidPublicKey() { return vapidPublicKey; }
        public void setVapidPublicKey(String vapidPublicKey) { this.vapidPublicKey = vapidPublicKey; }
        public String getVapidPrivateKey() { return vapidPrivateKey; }
        public void setVapidPrivateKey(String vapidPrivateKey) { this.vapidPrivateKey = vapidPrivateKey; }
        public String getSubject() { return subject; }
        public void setSubject(String subject) { this.subject = subject; }
        public boolean isConfigured() {
            return !isBlank(vapidPublicKey) && !isBlank(vapidPrivateKey) && !isBlank(subject);
        }

        private static boolean isBlank(String value) {
            return value == null || value.isBlank();
        }
    }

    public static class Reminders {
        private boolean enabled = true;
        private List<Integer> deadlineReminderDays = List.of(7, 3, 1, 0);
        private Duration schedulerInterval = Duration.ofHours(1);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<Integer> getDeadlineReminderDays() {
            return deadlineReminderDays;
        }

        public void setDeadlineReminderDays(List<Integer> deadlineReminderDays) {
            this.deadlineReminderDays = deadlineReminderDays;
        }

        public Duration getSchedulerInterval() {
            return schedulerInterval;
        }

        public void setSchedulerInterval(Duration schedulerInterval) {
            this.schedulerInterval = schedulerInterval;
        }
    }

    public static class Delivery {
        private int batchSize = 50;
        private int maxAttempts = 3;
        private Duration schedulerInterval = Duration.ofMinutes(1);
        private List<Duration> retryBackoff = List.of(
                Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(30));

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public Duration getSchedulerInterval() {
            return schedulerInterval;
        }

        public void setSchedulerInterval(Duration schedulerInterval) {
            this.schedulerInterval = schedulerInterval;
        }

        public List<Duration> getRetryBackoff() {
            return retryBackoff;
        }

        public void setRetryBackoff(List<Duration> retryBackoff) {
            this.retryBackoff = retryBackoff;
        }
    }
}
