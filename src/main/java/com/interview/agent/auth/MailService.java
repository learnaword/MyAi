package com.interview.agent.auth;

import com.interview.agent.config.AppConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final AppConfig appConfig;

    public void sendPasswordResetCode(String toEmail, String code) {
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        String from = appConfig.getMail().getFrom();
        if (sender == null || !StringUtils.hasText(appConfig.getMail().getHost()) || !StringUtils.hasText(from)) {
            throw AuthException.mailNotConfigured();
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(toEmail);
        message.setSubject("InterviewAgent 密码重置验证码");
        message.setText("您的密码重置验证码是：" + code + "\n有效期 15 分钟。如非本人操作请忽略。");
        sender.send(message);
        log.info("[Mail] reset code sent to {}", toEmail);
    }
}
