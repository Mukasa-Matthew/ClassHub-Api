package com.classhub.academicclass;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

@Component
public class JoinCodeGenerator {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int LENGTH = 6;

    private final SecureRandom random = new SecureRandom();

    public String generateUnique(AcademicClassRepository repository) {
        for (int attempt = 0; attempt < 50; attempt++) {
            String code = generate();
            if (!repository.existsByJoinCodeIgnoreCase(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Unable to generate unique join code");
    }

    public String generate() {
        char[] chars = new char[LENGTH];
        for (int i = 0; i < LENGTH; i++) {
            chars[i] = ALPHABET.charAt(random.nextInt(ALPHABET.length()));
        }
        return new String(chars);
    }

    public static String normalize(String joinCode) {
        if (joinCode == null) {
            return "";
        }
        return joinCode.trim().toUpperCase();
    }
}
