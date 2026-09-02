package com.classhub.academicclass;

import java.util.UUID;

public record AcademicClassResponse(
        UUID id,
        String name,
        String programmeName,
        String programmeCode,
        int studyYear,
        int semester,
        int academicYear,
        AcademicClassStatus status,
        String joinCode) {

    static AcademicClassResponse from(AcademicClass academicClass, boolean includeJoinCode) {
        return new AcademicClassResponse(
                academicClass.getId(),
                academicClass.getName(),
                academicClass.getProgrammeName(),
                academicClass.getProgrammeCode(),
                academicClass.getStudyYear(),
                academicClass.getSemester(),
                academicClass.getAcademicYear(),
                academicClass.getStatus(),
                includeJoinCode ? academicClass.getJoinCode() : null);
    }
}
