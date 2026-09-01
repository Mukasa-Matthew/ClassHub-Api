package com.classhub.support;

import com.classhub.academicclass.AcademicClass;
import com.classhub.academicclass.AcademicClassRepository;
import com.classhub.academicclass.AcademicClassStatus;
import com.classhub.academicclass.ClassMembership;
import com.classhub.academicclass.ClassMembershipRepository;
import com.classhub.academicclass.JoinCodeGenerator;
import com.classhub.academicclass.MembershipRole;
import com.classhub.academicclass.MembershipStatus;
import com.classhub.courseunit.CourseUnit;
import com.classhub.user.User;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class ClassMembershipTestSupport {

    private final AcademicClassRepository academicClassRepository;
    private final ClassMembershipRepository membershipRepository;
    private final JoinCodeGenerator joinCodeGenerator;

    public ClassMembershipTestSupport(
            AcademicClassRepository academicClassRepository,
            ClassMembershipRepository membershipRepository,
            JoinCodeGenerator joinCodeGenerator) {
        this.academicClassRepository = academicClassRepository;
        this.membershipRepository = membershipRepository;
        this.joinCodeGenerator = joinCodeGenerator;
    }

    public AcademicClass defaultClass() {
        return academicClassRepository.findAllByOrderByNameAsc().get(0);
    }

    public CourseUnit newCourseUnit(
            String internalCode,
            String code,
            String name,
            String normalizedName,
            String lecturerName,
            String description,
            boolean active) {
        return new CourseUnit(
                defaultClass(),
                internalCode,
                code,
                name,
                normalizedName,
                lecturerName,
                description,
                active);
    }

    public CourseUnit newCourseUnit(
            AcademicClass academicClass,
            String internalCode,
            String code,
            String name,
            String normalizedName,
            String lecturerName,
            String description,
            boolean active) {
        return new CourseUnit(
                academicClass,
                internalCode,
                code,
                name,
                normalizedName,
                lecturerName,
                description,
                active);
    }

    public ClassMembership activateStudent(AcademicClass academicClass, User user) {
        ClassMembership membership = membershipRepository
                .findByAcademicClassIdAndUserId(academicClass.getId(), user.getId())
                .orElseGet(() -> new ClassMembership(
                        academicClass,
                        user,
                        MembershipRole.STUDENT,
                        MembershipStatus.PENDING,
                        Instant.now()));
        membership.approve(user, Instant.now());
        return membershipRepository.saveAndFlush(membership);
    }

    public ClassMembership activateClassRep(AcademicClass academicClass, User user) {
        ClassMembership membership = membershipRepository
                .findByAcademicClassIdAndUserId(academicClass.getId(), user.getId())
                .orElseGet(() -> new ClassMembership(
                        academicClass,
                        user,
                        MembershipRole.CLASS_REP,
                        MembershipStatus.PENDING,
                        Instant.now()));
        membership.assignAsClassRep(user, Instant.now());
        return membershipRepository.saveAndFlush(membership);
    }
}
