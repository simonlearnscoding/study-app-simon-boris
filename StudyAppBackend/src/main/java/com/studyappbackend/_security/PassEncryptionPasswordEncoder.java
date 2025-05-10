package com.studyappbackend._security;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import static com.studyappbackend._security.PassEncryption.generateSecurePassword;
import static com.studyappbackend._security.PassEncryption.verifyUserPassword;

@Component
public class PassEncryptionPasswordEncoder implements PasswordEncoder {

    @Override
    public String encode(CharSequence rawPassword) {
        throw new UnsupportedOperationException("Salt must be provided explicitly!");
    }

    public String encodeWithSalt(CharSequence rawPassword, String salt) {
        return generateSecurePassword(rawPassword.toString(), salt);
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        throw new UnsupportedOperationException("Use matchesWithSalt for explicit salt verification!");
    }

    public boolean matchesWithSalt(CharSequence rawPassword, String encodedPassword, String salt) {
        return verifyUserPassword(rawPassword.toString(), encodedPassword, salt);
    }
}

