package com.offerhunt.auth.domain.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@Profile("!test")
public class SmtpPasswordResetMailService implements PasswordResetMailService {

    private static final Logger log = LoggerFactory.getLogger(SmtpPasswordResetMailService.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final String subject;

    public SmtpPasswordResetMailService(
        JavaMailSender mailSender,
        @Value("${app.password-reset.from:no-reply@offerhunt.local}") String fromAddress,
        @Value("${app.password-reset.subject:Восстановление пароля}") String subject
    ) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.subject = subject;
    }

    @Override
    public void sendResetLink(String email, String url) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setFrom(fromAddress);
        message.setSubject(subject);

        StringBuilder body = new StringBuilder();
        body.append("Вы запросили восстановление пароля в OfferHunt.\n\n")
            .append("Для сброса пароля перейдите по ссылке:\n")
            .append(url)
            .append("\n\n")
            .append("Если вы не запрашивали восстановление, просто игнорируйте это письмо.");

        message.setText(body.toString());

        try {
            mailSender.send(message);
            log.info("Password reset email sent via SMTP to {}", email);
        } catch (MailException ex) {
            log.error("Failed to send password reset email to {}", email, ex);
        }
    }
}
