package com.classhub.notification;

import com.classhub.academicclass.ClassMembershipRepository;
import com.classhub.academicclass.MembershipRole;
import com.classhub.academicclass.MembershipStatus;
import com.classhub.user.User;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class NotificationRecipientResolver {

    private final ClassMembershipRepository membershipRepository;

    public NotificationRecipientResolver(ClassMembershipRepository membershipRepository) {
        this.membershipRepository = membershipRepository;
    }

    public List<User> activeClassMembers(UUID classId) {
        return membershipRepository.findActiveMemberUsersByClassId(
                classId, MembershipStatus.ACTIVE, List.of(MembershipRole.STUDENT, MembershipRole.CLASS_REP));
    }
}
