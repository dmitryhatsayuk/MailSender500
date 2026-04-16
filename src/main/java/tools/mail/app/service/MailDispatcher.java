package tools.mail.app.service;

import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeUtility;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import tools.mail.app.model.Recipient;
import tools.mail.app.repository.RecipientRepository;

import java.io.FileInputStream;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailDispatcher {

    private final JavaMailSender mailSender;
    private final RecipientRepository repository;
    private final EmailTemplateService templateService;

    @Value("${spring.mail.from}")
    private String fromEmail;

    @Value("${spring.mail.daily-limit:500}")
    @Getter private int dailyLimit;

    public enum SmtpStatus { WAITING, OK, ERROR }
    @Getter private SmtpStatus smtpStatus = SmtpStatus.WAITING;

    @Getter private boolean running = false;
    @Getter private boolean paused = false;
    @Getter private boolean finished = false;

    @Async
    public void runDistribution() {
        if (templateService.getEmlFile() == null || running) return;
        running = true;
        paused = false;
        finished = false;

        while (running) {
            if (paused) {
                try { Thread.sleep(1000); continue; } catch (InterruptedException e) { break; }
            }

            // Проверка лимита и очереди
            long sentToday = repository.countByStatusAndSentAtAfter("SENT", LocalDateTime.now().minusDays(1));
            List<Recipient> queue = repository.findNextToProcess();

            if (queue.isEmpty() || sentToday >= dailyLimit) {
                if (queue.isEmpty()) finished = true;
                break;
            }

            Recipient recipient = queue.get(0);
            try {
                sendEmail(recipient);
                recipient.setStatus("SENT");
                recipient.setSentAt(LocalDateTime.now());
                recipient.setErrorMessage(null);

                // Если хоть одно письмо ушло, значит сервер работает
                smtpStatus = SmtpStatus.OK;
                log.info("Успешно: {}", recipient.getEmail());
            } catch (Exception e) {
                String errorMsg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
                recipient.setStatus("ERROR");
                recipient.setErrorMessage(e.getMessage());

                // Проверяем: это ошибка SMTP или просто "кривой" адрес?
                boolean isSmtpError = errorMsg.contains("authentication") ||
                        errorMsg.contains("timed out") ||
                        errorMsg.contains("connection") ||
                        errorMsg.contains("refused") ||
                        errorMsg.contains("proxy") ||
                        e instanceof MailAuthenticationException;

                if (isSmtpError) {
                    smtpStatus = SmtpStatus.ERROR;
                    this.paused = true; // СТАВИМ НА ПАУЗУ при проблемах с сервером
                    log.error("Критическая ошибка SMTP. Рассылка приостановлена: {}", e.getMessage());
                } else {
                    // Ошибка адреса (например, Illegal address). Не красим SMTP в красный и идем дальше.
                    log.warn("Проблема с адресом {}, письмо пропущено. SMTP в норме.", recipient.getEmail());
                    if (smtpStatus == SmtpStatus.WAITING) smtpStatus = SmtpStatus.OK;
                }
            }
            repository.save(recipient);

            try { Thread.sleep(30000); } catch (InterruptedException e) { break; }
        }
        running = false;
    }

    private void sendEmail(Recipient recipient) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(fromEmail);
        helper.setTo(recipient.getEmail());

        String subject = templateService.getSubject();
        message.setSubject(MimeUtility.encodeText(subject, "UTF-8", "B"));

        helper.setText(templateService.getHtmlContent(), true);
        if (templateService.getAttachmentFile() != null) {
            String encodedFileName = MimeUtility.encodeText(templateService.getAttachmentFile().getName(), "UTF-8", "B");
            helper.addAttachment(encodedFileName, templateService.getAttachmentFile());
        }
        mailSender.send(message);
    }

    public void setPaused(boolean paused) { this.paused = paused; }
}
