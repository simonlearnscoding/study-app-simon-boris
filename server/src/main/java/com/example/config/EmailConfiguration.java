package com.example.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

import org.springframework.beans.factory.annotation.Value;

@Configuration
public class EmailConfiguration {
  @Value("${spring.mail.username}")
  private String emailUsername;

  @Value("${spring.mail.password}")
  private String emailPassword;

  @Bean
  public JavaMailSender javaMailSender() {
    JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
    mailSender.setHost("smtp.gmail.com");
    mailSender.setPort(587);
    mailSender.setUsername(emailUsername);
    mailSender.setPassword(emailPassword);

    Properties props = mailSender.getJavaMailProperties();
    props.put("mail.transport.protocol", "smtp");
    props.put("mail.smtp.auth", "true"); // Enable SMTP authentication, which mean
    props.put("mail.smtp.starttls.enable", "true"); // Enable secure connection
    props.put("mail.debug", "true"); // Enable debug output for troubleshooting
    return mailSender;

  }
}
