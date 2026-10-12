package com.classhub.academicclass;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.classhub.common.exception.ApplicationException;
import com.classhub.notification.DeliveryStatus;
import com.classhub.notification.Notification;
import com.classhub.notification.NotificationChannel;
import com.classhub.notification.NotificationDelivery;
import com.classhub.notification.NotificationDeliveryRepository;
import com.classhub.notification.NotificationPreference;
import com.classhub.notification.NotificationPreferenceRepository;
import com.classhub.notification.NotificationRepository;
import com.classhub.notification.NotificationType;
import com.classhub.support.ClassMembershipTestSupport;
import com.classhub.support.PostgresTestcontainersConfiguration;
import com.classhub.user.CreateUserCommand;
import com.classhub.user.User;
import com.classhub.user.UserRole;
import com.classhub.user.UserService;
import com.classhub.user.UserStatus;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "classhub.notifications.email.enabled=true",
        "classhub.notifications.whatsapp.enabled=true"
})
@Import(PostgresTestcontainersConfiguration.class)
@Transactional
class ClassMembershipNotificationIntegrationTest {

    private static final String PASSWORD = "MembershipNotify1!";

    @Autowired
    private UserService userService;

    @Autowired
    private AcademicClassRepository academicClassRepository;

    @Autowired
    private ClassMembershipService membershipService;

    @Autowired
    private ClassRepMembershipService classRepMembershipService;

    @Autowired
    private ClassMembershipRepository membershipRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationDeliveryRepository deliveryRepository;

    @Autowired
    private NotificationPreferenceRepository preferenceRepository;

    @Autowired
    private ClassMembershipTestSupport membershipTestSupport;

    private AcademicClass academicClass;
    private User classRep;

    @BeforeEach
    void setUp() {
        academicClass = academicClassRepository.saveAndFlush(new AcademicClass(
                "BSIT Year 3",
                "Bachelor of Science in Information Technology",
                "BSIT",
                3,
                2,
                2026,
                "JN" + UUID.randomUUID().toString().substring(0, 4).toUpperCase(),
                AcademicClassStatus.ACTIVE));
        classRep = createUser("Rep", "User", uniqueEmail("rep"), "+256755032436");
        membershipTestSupport.activateClassRep(academicClass, classRep);
    }

    @Test
    void pendingJoinQueuesAllEligibleChannelsWithAcademicClassInformation() {
        User student = createUser("Pending", "Student", uniqueEmail("pending"), "+256700000001");
        preferenceRepository.saveAndFlush(new NotificationPreference(student, true, true));

        ClassMembershipResponse response = membershipService.joinExistingUser(
                student, new JoinClassRequest(academicClass.getJoinCode()));

        Notification notification = notificationFor(response.membershipId(), NotificationType.CLASS_JOIN_REQUESTED);
        assertThat(notification.getRecipient().getId()).isEqualTo(student.getId());
        assertThat(notification.getMessage()).isEqualTo(
                "Your request to join Bachelor of Science in Information Technology — Year 3, "
                        + "Semester 2, Academic Year 2026 has been submitted and is awaiting Class Rep approval.");
        assertThat(deliveriesFor(notification))
                .extracting(NotificationDelivery::getChannel)
                .containsExactlyInAnyOrder(NotificationChannel.IN_APP, NotificationChannel.EMAIL, NotificationChannel.WHATSAPP);
        assertThat(deliveriesFor(notification)).allMatch(d -> d.getStatus() == DeliveryStatus.PENDING);
    }

    @Test
    void approvalQueuesWelcomeOnceAndRetriedApprovalDoesNotDuplicateIt() {
        User student = createUser("Approved", "Student", uniqueEmail("approved"), "+256700000002");
        preferenceRepository.saveAndFlush(new NotificationPreference(student, true, true));
        ClassMembershipResponse pending = membershipService.joinExistingUser(
                student, new JoinClassRequest(academicClass.getJoinCode()));

        classRepMembershipService.approve(classRep.getId(), pending.membershipId());

        Notification approved = notificationFor(pending.membershipId(), NotificationType.CLASS_JOIN_APPROVED);
        assertThat(approved.getMessage()).isEqualTo(
                "Welcome to ClassHub. You have been approved to join "
                        + "Bachelor of Science in Information Technology — Year 3, Semester 2, Academic Year 2026.");
        assertThat(deliveriesFor(approved)).hasSize(3);

        assertThatThrownBy(() -> classRepMembershipService.approve(classRep.getId(), pending.membershipId()))
                .isInstanceOf(ApplicationException.class);
        assertThat(notificationsFor(pending.membershipId(), NotificationType.CLASS_JOIN_APPROVED)).hasSize(1);
        assertThat(deliveriesFor(approved)).hasSize(3);
    }

    @Test
    void missingOptionalPhoneSkipsWhatsappWhileRequiredEmailStillQueues() {
        User student = createUser("NoPhone", "Student", uniqueEmail("no-phone"), null);
        preferenceRepository.saveAndFlush(new NotificationPreference(student, true, true));

        ClassMembershipResponse pending = membershipService.joinExistingUser(
                student, new JoinClassRequest(academicClass.getJoinCode()));

        Notification notification = notificationFor(pending.membershipId(), NotificationType.CLASS_JOIN_REQUESTED);
        NotificationDelivery email = deliveryFor(notification, NotificationChannel.EMAIL);
        NotificationDelivery whatsapp = deliveryFor(notification, NotificationChannel.WHATSAPP);
        assertThat(email.getStatus()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(whatsapp.getStatus()).isEqualTo(DeliveryStatus.SKIPPED);
        assertThat(whatsapp.getLastErrorCode()).isEqualTo("NO_CONTACT");
    }

    @Test
    void rejectionDeactivationAndReactivationUseDistinctAccurateLifecycleMessages() {
        User rejectedStudent = createUser("Rejected", "Student", uniqueEmail("rejected"), "+256700000003");
        preferenceRepository.saveAndFlush(new NotificationPreference(rejectedStudent, false, false));
        ClassMembershipResponse rejectedPending = membershipService.joinExistingUser(
                rejectedStudent, new JoinClassRequest(academicClass.getJoinCode()));
        classRepMembershipService.reject(classRep.getId(), rejectedPending.membershipId());

        Notification rejected = notificationFor(rejectedPending.membershipId(), NotificationType.CLASS_JOIN_REJECTED);
        assertThat(rejected.getMessage())
                .contains("was not approved", "Bachelor of Science in Information Technology", "Year 3",
                        "Semester 2", "Academic Year 2026")
                .doesNotContain("You joined the class", "null");
        assertThat(deliveriesFor(rejected)).allMatch(delivery -> delivery.getStatus() == DeliveryStatus.PENDING);

        User activeStudent = createUser("Lifecycle", "Student", uniqueEmail("lifecycle"), "+256700000004");
        ClassMembershipResponse activePending = membershipService.joinExistingUser(
                activeStudent, new JoinClassRequest(academicClass.getJoinCode()));
        classRepMembershipService.approve(classRep.getId(), activePending.membershipId());
        classRepMembershipService.deactivate(classRep.getId(), activePending.membershipId());
        Notification deactivated = notificationFor(
                activePending.membershipId(), NotificationType.CLASS_MEMBER_DEACTIVATED);
        assertThat(deactivated.getMessage()).contains("has been deactivated", "Academic Year 2026");

        classRepMembershipService.reactivate(classRep.getId(), activePending.membershipId());
        Notification reactivated = notificationFor(
                activePending.membershipId(), NotificationType.CLASS_MEMBER_REACTIVATED);
        assertThat(reactivated.getMessage()).contains("has been reactivated", "access the class again");
    }

    private User createUser(String firstName, String lastName, String email, String phone) {
        return userService.create(new CreateUserCommand(
                firstName,
                lastName,
                email,
                phone,
                PASSWORD,
                firstName.equals("Rep") ? UserRole.CLASS_REP : UserRole.STUDENT,
                UserStatus.ACTIVE,
                true,
                firstName.equals("Rep") ? null : "REG-" + UUID.randomUUID()));
    }

    private Notification notificationFor(UUID membershipId, NotificationType type) {
        return notificationsFor(membershipId, type).getFirst();
    }

    private List<Notification> notificationsFor(UUID membershipId, NotificationType type) {
        return notificationRepository.findAll().stream()
                .filter(n -> n.getReferenceId().equals(membershipId))
                .filter(n -> n.getType() == type)
                .toList();
    }

    private List<NotificationDelivery> deliveriesFor(Notification notification) {
        return deliveryRepository.findAll().stream()
                .filter(d -> d.getNotification().getId().equals(notification.getId()))
                .toList();
    }

    private NotificationDelivery deliveryFor(Notification notification, NotificationChannel channel) {
        return deliveriesFor(notification).stream()
                .filter(d -> d.getChannel() == channel)
                .findFirst()
                .orElseThrow();
    }

    private static String uniqueEmail(String prefix) {
        return prefix + "." + UUID.randomUUID() + "@example.com";
    }
}
