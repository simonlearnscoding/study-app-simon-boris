package com.example.jwt;

import com.example.dto.LoginUserDTO;
import com.example.dto.RegisterUserDTO;
import com.example.dto.VerifyUserDTO;
import com.example.user.User;
import com.example.user.UserRepository;

import jakarta.mail.MessagingException;

import lombok.extern.slf4j.Slf4j;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Random;

@Slf4j
@Service
public class AuthenticationService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final EmailService emailService;

    public AuthenticationService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            EmailService emailService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.emailService = emailService;
    }

    public User signup(RegisterUserDTO input) {
        // 1. Check if user already exists
        Optional<User> existingUser = userRepository.findByEmail(input.getEmail());

        // 2. Handle existing user cases
        if (existingUser.isPresent()) {
            User user = existingUser.get();

            if (user.isEnabled()) {
                throw new RuntimeException("User with this email already exists and is verified");
            }

            // Resend verification if not expired yet
            if (user.getVerificationExpiration().isAfter(LocalDateTime.now())) {
                throw new RuntimeException(
                        "Verification email already sent. Please check your inbox");
            }

            // Generate new verification code if expired
            user.setVerificationCode(generateVerificationCode());
            user.setVerificationExpiration(LocalDateTime.now().plusHours(24));
            sendVerificationEmail(user);
            return userRepository.save(user);
        }

        // 3. Create new user
        User newUser =
                new User(
                        input.getName(),
                        input.getEmail(),
                        input.getUsername(),
                        passwordEncoder.encode(input.getPassword()));

        newUser.setVerificationCode(generateVerificationCode());
        newUser.setVerificationExpiration(LocalDateTime.now().plusHours(24));
        newUser.setEnabled(false);
        sendVerificationEmail(newUser);

        return userRepository.save(newUser);
    }

    private String generateVerificationCode() {
        Random random = new Random();
        int code = random.nextInt(999999) + 100000;
        return String.valueOf(code);
    }

    public CustomUserDetails authenticate(LoginUserDTO input) {

        User user =
                userRepository
                        .findByEmail(input.getEmail())
                        .orElseThrow(() -> new RuntimeException("User not found"));

        // Add to your authenticate() method
        if (!user.isEnabled()) {
            throw new RuntimeException("Account not verified - please check your email");
        }

        // 3. Authenticate credentials
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(input.getEmail(), input.getPassword()));
            System.out.println("Authentication successful for user: " + user);
            return new CustomUserDetails(user);
        } catch (BadCredentialsException e) {
            throw new RuntimeException("Invalid email or password");
        }
    }

    public void verifyUser(VerifyUserDTO input) {
        System.out.println("Verifying user with email: " + input.getEmail());
        Optional<User> optionalUser = userRepository.findByEmail(input.getEmail());
        if (optionalUser.isEmpty()) {
            throw new RuntimeException("User not found");
        }
        User user = optionalUser.get();
        if (user.getVerificationExpiration().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Verification code has expired");
        }
        if (user.getVerificationCode().equals(input.getVerificationCode())) {
            user.setEnabled(true);
            user.setVerificationCode(null);
            user.setVerificationExpiration(null);
            userRepository.save(user);
        } else {
            throw new RuntimeException("Invalid verification code");
        }
    }

    public void resendVerificationCode(String email) {
        Optional<User> optionalUser = userRepository.findByEmail(email);
        if (!optionalUser.isPresent()) {
            throw new RuntimeException("User does not exist");
        }
        User user = optionalUser.get();
        if (user.isEnabled()) {
            throw new RuntimeException("Account is already verified");
        }
        user.setVerificationCode(generateVerificationCode());
        user.setVerificationExpiration(LocalDateTime.now().plusHours(1));
    }

    public void sendVerificationEmail(User user) {
        String subject = "Please Verify Your Account";
        String verificationCode = user.getVerificationCode();

        String htmlContent =
                """
<!DOCTYPE html>
<html>
<head>
    <style>
        body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
        .container { max-width: 600px; margin: 0 auto; padding: 20px; }
        .header { background-color: #4a6fa5; padding: 20px; text-align: center; }
        .header h1 { color: white; margin: 0; }
        .content { padding: 30px; background-color: #f9f9f9; }
        .button {
            display: inline-block;
            padding: 12px 24px;
            background-color: #4a6fa5;
            color: white !important;
            text-decoration: none;
            border-radius: 4px;
            font-weight: bold;
        }
        .footer { margin-top: 20px; text-align: center; font-size: 12px; color: #777; }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1>Welcome to Our Service!</h1>
        </div>
        <div class="content">
            <p>Hello %s,</p>
            <p>Thank you for registering with us. Please verify your email address to complete your registration.</p>

            <p style="text-align: center; margin: 30px 0;">
                <a href="https://yourapp.com/verify?code=%s" class="button">
                    Verify Your Email
                </a>
            </p>

            <p>Or copy and paste this verification code in our application:</p>
            <p style="font-size: 18px; font-weight: bold; text-align: center;">%s</p>

            <p>If you didn't request this, please ignore this email.</p>
        </div>
        <div class="footer">
            <p>© 2023 Your Company. All rights reserved.</p>
        </div>
    </div>
</body>
</html>
"""
                        .formatted(user.getName(), verificationCode, verificationCode);

        try {
            emailService.sendVerificationEmail(user.getEmail(), subject, htmlContent);
        } catch (MessagingException e) {
            // Consider using a logger instead of printStackTrace
            log.error("Failed to send verification email to " + user.getEmail(), e);
        }
    }
}
