package com.classhub.courseunit;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class CourseUnitInternalCodeGenerator {

    private final JdbcTemplate jdbcTemplate;

    public CourseUnitInternalCodeGenerator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public String nextCode() {
        Long sequenceValue =
                jdbcTemplate.queryForObject("SELECT nextval('course_unit_internal_code_seq')", Long.class);
        if (sequenceValue == null) {
            throw new IllegalStateException("Failed to allocate course unit internal code");
        }
        return format(sequenceValue);
    }

    static String format(long sequenceValue) {
        if (sequenceValue < 0 || sequenceValue > 999999) {
            throw new IllegalStateException("Course unit internal code sequence out of range");
        }
        return "CU-" + String.format("%06d", sequenceValue);
    }
}
