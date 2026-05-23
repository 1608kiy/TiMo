package com.timo.words.modules.auth.service;

import com.timo.words.common.BusinessException;
import com.timo.words.common.ResultCode;
import com.timo.words.modules.user.entity.User;
import com.timo.words.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ForgotPasswordService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate stringRedisTemplate;

    @Value("${spring.mail.username:}")
    private String fromEmail;

    @Value("${spring.mail.password:}")
    private String fromPassword;

    @Value("${custom.mail.smtp.host:}")
    private String customSmtpHost;

    @Value("${custom.mail.smtp.port:465}")
    private int customSmtpPort;

    @Value("${custom.mail.smtp.ssl:true}")
    private boolean customSmtpSsl;

    private static final String CODE_KEY_PREFIX = "pwd:reset:";
    private static final long CODE_TTL_MINUTES = 5;
    private static final int CODE_LENGTH = 6;
    private volatile JavaMailSender cachedMailSender;

    // 邮箱域名 → SMTP 配置 (host, port, 是否SSL)
    private static final Map<String, String[]> SMTP_PROVIDERS = new HashMap<>();
    static {
        SMTP_PROVIDERS.put("qq.com",      new String[]{"smtp.qq.com", "465", "true"});
        SMTP_PROVIDERS.put("foxmail.com", new String[]{"smtp.qq.com", "465", "true"});
        SMTP_PROVIDERS.put("163.com",     new String[]{"smtp.163.com", "465", "true"});
        SMTP_PROVIDERS.put("126.com",     new String[]{"smtp.126.com", "465", "true"});
        SMTP_PROVIDERS.put("yeah.net",    new String[]{"smtp.yeah.net", "465", "true"});
        SMTP_PROVIDERS.put("sina.com",    new String[]{"smtp.sina.com", "465", "true"});
        SMTP_PROVIDERS.put("sohu.com",    new String[]{"smtp.sohu.com", "465", "true"});
        SMTP_PROVIDERS.put("gmail.com",   new String[]{"smtp.gmail.com", "587", "false"});
        SMTP_PROVIDERS.put("outlook.com", new String[]{"smtp-mail.outlook.com", "587", "false"});
        SMTP_PROVIDERS.put("hotmail.com", new String[]{"smtp-mail.outlook.com", "587", "false"});
        SMTP_PROVIDERS.put("live.com",    new String[]{"smtp-mail.outlook.com", "587", "false"});
        // 阿里云企业邮箱
        SMTP_PROVIDERS.put("qiye.aliyun.com", new String[]{"smtp.qiye.aliyun.com", "465", "true"});
        // 腾讯企业邮箱
        SMTP_PROVIDERS.put("exmail.qq.com",   new String[]{"smtp.exmail.qq.com", "465", "true"});
    }

    private JavaMailSender buildMailSender() {
        if (cachedMailSender != null) return cachedMailSender;
        if (fromEmail == null || fromEmail.isBlank()) {
            throw new BusinessException(500, "系统邮箱未配置，请设置 MAIL_USERNAME 和 MAIL_PASSWORD 环境变量");
        }
        if (fromPassword == null || fromPassword.isBlank()) {
            throw new BusinessException(500, "系统邮箱密码未配置，请设置 MAIL_PASSWORD 环境变量");
        }

        String[] smtpConfig;

        // 优先使用自定义 SMTP（如阿里云邮件推送 DirectMail）
        if (customSmtpHost != null && !customSmtpHost.isBlank()) {
            smtpConfig = new String[]{customSmtpHost, String.valueOf(customSmtpPort), String.valueOf(customSmtpSsl)};
            log.info("使用自定义 SMTP: {}:{} (SSL={})", smtpConfig[0], smtpConfig[1], smtpConfig[2]);
        } else {
            String domain = fromEmail.substring(fromEmail.indexOf('@') + 1).toLowerCase();
            smtpConfig = SMTP_PROVIDERS.get(domain);

            // 自定义域名：用阿里云企业邮箱的 SMTP
            if (smtpConfig == null) {
                smtpConfig = new String[]{"smtp.qiye.aliyun.com", "465", "true"};
                log.info("自定义域名 {}，使用阿里云企业邮箱 SMTP", domain);
            }
        }

        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(smtpConfig[0]);
        sender.setPort(Integer.parseInt(smtpConfig[1]));
        sender.setUsername(fromEmail);
        sender.setPassword(fromPassword);
        sender.setDefaultEncoding("UTF-8");

        Properties props = sender.getJavaMailProperties();
        props.put("mail.smtp.auth", "true");
        if (Boolean.parseBoolean(smtpConfig[2])) {
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.socketFactory.port", smtpConfig[1]);
            props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
        } else {
            props.put("mail.smtp.starttls.enable", "true");
        }
        cachedMailSender = sender;
        return sender;
    }

    public void sendVerificationCode(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND));

        String code = generateCode();
        String key = CODE_KEY_PREFIX + email;
        stringRedisTemplate.opsForValue().set(key, code, CODE_TTL_MINUTES, TimeUnit.MINUTES);

        try {
            JavaMailSender mailSender = buildMailSender();
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(email);
            message.setSubject("TiMo背单词 - 密码重置验证码");
            message.setText("你的验证码是：" + code + "\n有效期5分钟，请勿泄露给他人。");
            mailSender.send(message);
            log.info("验证码已发送到 {}", email);
        } catch (Exception e) {
            log.error("邮件发送失败: {}，验证码 {} 已存入 Redis", e.getMessage(), code);
        }
    }

    public void resetPassword(String email, String code, String newPassword) {
        String key = CODE_KEY_PREFIX + email;
        String savedCode = stringRedisTemplate.opsForValue().get(key);

        if (savedCode == null || !savedCode.equals(code)) {
            throw new BusinessException(400, "验证码无效或已过期");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND));

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        stringRedisTemplate.delete(key);

        log.info("Password reset for {}", email);
    }

    private String generateCode() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }
}
