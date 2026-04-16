package tools.mail.app.service;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.internet.MimeUtility;
import lombok.Getter;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;

@Service
@Getter
public class EmailTemplateService {

    private String subject;
    private String htmlContent;
    private File emlFile;

    public void loadTemplate(File file) throws Exception {
        this.emlFile = file;
        Properties props = System.getProperties();
        Session session = Session.getDefaultInstance(props, null);

        try (FileInputStream fis = new FileInputStream(file)) {
            MimeMessage message = new MimeMessage(session, fis);

            // Получаем "сырую" тему письма
            String rawSubject = message.getSubject();

            if (rawSubject != null) {
                // Пытаемся исправить кодировку:
                // Переводим из ISO-8859-1 (как её ошибочно видит Java) обратно в байты
                // и интерпретируем как UTF-8.
                try {
                    String decoded = new String(rawSubject.getBytes("ISO-8859-1"), "UTF-8");

                    // Если в строке все еще есть маркеры кодировки почты (например, =?UTF-8?),
                    // прогоняем через стандартный декодер
                    if (decoded.contains("=?")) {
                        this.subject = MimeUtility.decodeText(decoded);
                    } else {
                        this.subject = decoded;
                    }
                } catch (Exception e) {
                    // Если ручной метод упал, используем стандартный декодер как запасной вариант
                    this.subject = MimeUtility.decodeText(rawSubject);
                }
            } else {
                this.subject = "Без темы";
            }

            // Извлекаем тело письма (HTML)
            this.htmlContent = getTextFromMessage(message);
        }
    }

    private String getTextFromMessage(jakarta.mail.Part p) throws Exception {
        // Если нашли HTML часть
        if (p.isMimeType("text/html")) {
            return (String) p.getContent();
        }

        // Если письмо составное (multipart), ищем рекурсивно внутри частей
        if (p.isMimeType("multipart/*")) {
            MimeMultipart mp = (MimeMultipart) p.getContent();
            for (int i = 0; i < mp.getCount(); i++) {
                String s = getTextFromMessage(mp.getBodyPart(i));
                if (s != null) return s;
            }
        }

        // Обработка вложенных сообщений
        if (p.isMimeType("message/rfc822")) {
            return getTextFromMessage((jakarta.mail.Part) p.getContent());
        }

        return null;
    }
}
