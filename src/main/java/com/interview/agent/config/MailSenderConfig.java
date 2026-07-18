package com.interview.agent.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.util.StringUtils;

import java.util.Properties;

@Configuration
public class MailSenderConfig {

    @Bean
    @ConditionalOnProperty(prefix = "app.mail", name = "host")
    public JavaMailSender javaMailSender(AppConfig appConfig) {
        AppConfig.MailProperties mail = appConfig.getMail();
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(mail.getHost());
        sender.setPort(mail.getPort() == null ? 587 : mail.getPort());
        if (StringUtils.hasText(mail.getUsername())) {
            sender.setUsername(mail.getUsername());
        }
        if (StringUtils.hasText(mail.getPassword())) {
            sender.setPassword(mail.getPassword());
        }
        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", StringUtils.hasText(mail.getUsername()));
        props.put("mail.smtp.starttls.enable", "true");
        return sender;
    }
}
