package com.classhub.auth;

import com.classhub.notification.Notification;
import com.classhub.notification.NotificationChannel;
import com.classhub.notification.NotificationDelivery;
import com.classhub.notification.NotificationDeliveryRepository;
import com.classhub.notification.NotificationRepository;
import com.classhub.notification.NotificationType;
import com.classhub.user.User;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ClassRepSetupNotificationService {

    public static final String REFERENCE_TYPE = "CLASS_REP_ACCOUNT_SETUP";
    private static final String MESSAGE =
            "Your ClassHub account has been created. Complete your account setup using this secure link.";

    private final NotificationRepository notificationRepository;
    private final NotificationDeliveryRepository deliveryRepository;

    public ClassRepSetupNotificationService(
            NotificationRepository notificationRepository,
            NotificationDeliveryRepository deliveryRepository) {
        this.notificationRepository = notificationRepository;
        this.deliveryRepository = deliveryRepository;
    }

    public void queue(User user, ClassRepAccountSetup setup) {
        Notification notification = notificationRepository.saveAndFlush(new Notification(
                user,
                NotificationType.ACCOUNT_SETUP,
                "Complete your ClassHub account setup",
                MESSAGE,
                setup.getId(),
                REFERENCE_TYPE,
                "ISSUED:" + setup.getIssuedAt().toEpochMilli()));
        deliveryRepository.saveAll(List.of(
                new NotificationDelivery(notification, user, NotificationChannel.EMAIL),
                new NotificationDelivery(notification, user, NotificationChannel.WHATSAPP)));
        deliveryRepository.flush();
    }
}
