package com.classhub.auth;

import com.classhub.notification.Notification;
import com.classhub.notification.NotificationChannel;
import com.classhub.notification.NotificationDelivery;
import com.classhub.notification.NotificationDeliveryRepository;
import com.classhub.notification.NotificationRepository;
import com.classhub.notification.NotificationType;
import com.classhub.user.User;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PasswordResetNotificationService {

    public static final String REFERENCE_TYPE = "PASSWORD_RESET_CHALLENGE";
    private final NotificationRepository notificationRepository;
    private final NotificationDeliveryRepository deliveryRepository;

    public PasswordResetNotificationService(
            NotificationRepository notificationRepository,
            NotificationDeliveryRepository deliveryRepository) {
        this.notificationRepository = notificationRepository;
        this.deliveryRepository = deliveryRepository;
    }

    public void queueOtp(User user, PasswordResetChallenge challenge) {
        queue(user, challenge, NotificationType.PASSWORD_RESET_OTP,
                "ClassHub password reset code",
                "Use the 6-digit verification code sent with this message. It expires in 10 minutes. "
                        + "Do not share this code. If you did not request a password reset, ignore this message.",
                "OTP:" + challenge.getRequestedAt().toEpochMilli());
    }

    public void queuePasswordChanged(User user, PasswordResetChallenge challenge) {
        queue(user, challenge, NotificationType.PASSWORD_CHANGED,
                "Your ClassHub password was changed",
                "Your ClassHub password was changed. If you did not make this change, contact ClassHub support immediately.",
                "CHANGED:" + challenge.getResetUsedAt().toEpochMilli());
    }

    private void queue(
            User user,
            PasswordResetChallenge challenge,
            NotificationType type,
            String title,
            String message,
            String occurrenceKey) {
        Notification notification = notificationRepository.saveAndFlush(new Notification(
                user, type, title, message, challenge.getId(), REFERENCE_TYPE, occurrenceKey));
        List<NotificationDelivery> deliveries = new ArrayList<>();
        if (user.isEmailVerified() && user.getEmail() != null && !user.getEmail().isBlank()) {
            deliveries.add(new NotificationDelivery(notification, user, NotificationChannel.EMAIL));
        }
        if (user.getPhoneNumber() != null && !user.getPhoneNumber().isBlank()) {
            deliveries.add(new NotificationDelivery(notification, user, NotificationChannel.WHATSAPP));
        }
        if (!deliveries.isEmpty()) {
            deliveryRepository.saveAll(deliveries);
            deliveryRepository.flush();
        }
    }
}
