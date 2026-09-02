package com.classhub.user;

public enum UserRole {
    SUPER_ADMIN,
    CLASS_REP,
    STUDENT;

    public boolean isStudentLike() {
        return this == STUDENT || this == CLASS_REP;
    }
}
