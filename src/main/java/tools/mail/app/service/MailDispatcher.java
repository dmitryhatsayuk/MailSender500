package tools.mail.app.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import tools.mail.app.model.Recipient;
import tools.mail.app.repository.RecipientRepository;

import jakarta.mail.internet.MimeMessage;
import java.time.LocalDateTime;
import java.io.FileInputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailDispatcher {

    private final JavaMailSender mailSender;
    private final RecipientRepository repository;
    private final EmailTemplateService templateService;

    private boolean running = false;

    @Async
    public void runDistribution() {
        running = true;

        while (running) {
            // 1. Проверяем лимит (500 писем за последние 24 часа)
            long sentToday = repository.countByStatusAndSentAtAfter("SENT", LocalDateTime.now().minusDays(1));
            if (sentToday >= 500) {
                log.info("Дневной лимит достигнут. Остановка на сегодня.");
                break;
            }

            // 2. Берем следующего адресата
            Recipient recipient = repository.findFirstByStatusOrderByIdAsc("PENDING");
            if (recipient == null) {
                log.info("Рассылка завершена. Очередь пуста.");
                break;
            }

            try {
                sendEmail(recipient);
                recipient.setStatus("SENT");
                recipient.setSentAt(LocalDateTime.now());
            } catch (Exception e) {
                recipient.setStatus("ERROR");
                recipient.setErrorMessage(e.getMessage());
            }

            repository.save(recipient);

            // 3. Пауза 30 секунд
            try {
                Thread.sleep(30000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        running = false;
    }

    private void sendEmail(Recipient recipient) throws Exception {
        // Создаем письмо на основе загруженного EML
        MimeMessage message = mailSender.createMimeMessage();
        // Используем данные из EML файла
        MimeMessage source = new MimeMessage(null, new FileInputStream(templateService.getEmlFile()));

        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setTo(recipient.getEmail());
        helper.setSubject(templateService.getSubject());
        helper.setText(templateService.getHtmlContent(), true);

        // Тут можно добавить логику копирования вложений из source в message,
        // но для начала проверим простую отправку

        mailSender.send(message);
    }

    public void stop() { this.running = false; }
}
