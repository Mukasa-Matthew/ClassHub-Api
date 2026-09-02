package com.classhub.notification.delivery;

import com.classhub.notification.NotificationType;
import com.classhub.notification.config.NotificationProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

@Service
public class NotificationChannelTemplateService {

    private final NotificationProperties properties;

    public NotificationChannelTemplateService(NotificationProperties properties) {
        this.properties = properties;
    }

    public EmailContent email(NotificationDeliveryRequest request) {
        NotificationMessage message = request.message();
        String name = recipientName(request);
        String url = actionUrl(message.actionPath());
        String securityFooter = securityFooter(message.eventType());
        String plainText = "Hello " + name + ",\n\n" + clean(message.title()) + "\n\n" + clean(message.body())
                + ctaText(message, url)
                + (securityFooter.isBlank() ? "" : "\n\n" + securityFooter)
                + "\n\nClassHub";
        String html = """
                <!doctype html><html><body style="font-family:Arial,sans-serif;color:#17233c;background:#f8fafc;margin:0">
                <div style="max-width:600px;margin:auto;padding:28px;background:#ffffff">
                  <p style="font-weight:700;color:#2563eb">ClassHub</p>
                  <h2 style="color:#17233c">%s</h2>
                  <p>Hello %s,</p>
                  <p style="white-space:pre-line">%s</p>
                  %s
                  %s
                  <p style="color:#64748b;font-size:12px">ClassHub academic communication</p>
                </div></body></html>
                """.formatted(
                escape(message.title()),
                escape(name),
                escape(clean(message.body())),
                ctaHtml(message, url),
                securityFooter.isBlank()
                        ? ""
                        : "<p style=\"color:#475569;font-size:13px\">" + escape(securityFooter) + "</p>");
        return new EmailContent(message.title(), plainText, html);
    }

    public String whatsapp(NotificationDeliveryRequest request) {
        NotificationMessage message = request.message();
        String greeting = "ClassHub\n\nHi " + recipientName(request) + ",\n";
        String url = actionUrl(message.actionPath());
        return switch (message.eventType()) {
            case PASSWORD_RESET_OTP -> greeting + clean(message.body());
            case PASSWORD_CHANGED -> greeting + clean(message.body());
            case ACCOUNT_SETUP -> greeting + clean(message.body()) + "\n\nComplete account setup: " + url;
            case ACCOUNT_SETUP_COMPLETED -> greeting + clean(message.body()) + "\n\nOpen ClassHub: " + url;
            case CLASS_JOIN_REQUESTED, CLASS_JOIN_APPROVED, CLASS_JOIN_REJECTED,
                    CLASS_MEMBER_DEACTIVATED, CLASS_MEMBER_REACTIVATED ->
                    greeting + clean(message.shortText()) + "\n\nOpen ClassHub: " + url;
            case COURSEWORK_PUBLISHED, COURSEWORK_DEADLINE_REMINDER,
                    COURSEWORK_DEADLINE_CHANGED, COURSEWORK_CANCELLED,
                    COURSEWORK_INSTRUCTIONS_UPDATED ->
                    greeting + message.title() + "\n" + clean(message.shortText())
                            + "\n\nView coursework: " + url;
            case ANNOUNCEMENT_PUBLISHED -> greeting + message.title() + "\n" + clean(message.shortText())
                    + "\n\nView announcement: " + url;
        };
    }

    private String actionUrl(String path) {
        String base = clean(properties.getWebBaseUrl()).replaceAll("/+$", "");
        String safePath = path == null || path.isBlank() ? "/" : path;
        return base + (safePath.startsWith("/") ? safePath : "/" + safePath);
    }

    private static String securityFooter(NotificationType type) {
        return switch (type) {
            case PASSWORD_RESET_OTP ->
                    "For your security, never share this verification code. ClassHub will never ask for it.";
            case PASSWORD_CHANGED ->
                    "If you did not change your password, contact ClassHub support immediately.";
            default -> "";
        };
    }

    private static String ctaText(NotificationMessage message, String url) {
        if (url.isBlank()) {
            return "";
        }
        return "\n\n" + clean(message.actionLabel()) + ": " + url;
    }

    private static String ctaHtml(NotificationMessage message, String url) {
        if (url.isBlank()) {
            return "";
        }
        return "<p><a href=\"" + escape(url)
                + "\" style=\"display:inline-block;background:#17233c;color:#fff;padding:12px 18px;"
                + "text-decoration:none;border-radius:6px\">" + escape(clean(message.actionLabel())) + "</a></p>";
    }

    private static String recipientName(NotificationDeliveryRequest request) {
        return clean(request.recipientFirstName()).isBlank() ? "there" : clean(request.recipientFirstName());
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static String escape(String value) {
        return HtmlUtils.htmlEscape(clean(value));
    }

    public record EmailContent(String subject, String plainText, String html) {}
}
