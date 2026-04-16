package tools.mail.app.service;

import jakarta.mail.BodyPart;
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

            // 1. Декодируем тему письма (убирает кракозябры из заголовков)
            String rawSubject = message.getSubject();
            if (rawSubject != null) {
                this.subject = MimeUtility.decodeText(rawSubject);
            } else {
                this.subject = "Без темы";
            }

            // 2. Извлекаем HTML содержимое с учетом кодировки
            this.htmlContent = getTextFromMessage(message);
        }
    }

    private String getTextFromMessage(jakarta.mail.Part p) throws Exception {
        // Если это чистый HTML
        if (p.isMimeType("text/html")) {
            return (String) p.getContent();
        }

        // Если письмо составное (с вложениями или картинками)
        if (p.isMimeType("multipart/*")) {
            MimeMultipart mp = (MimeMultipart) p.getContent();
            for (int i = 0; i < mp.getCount(); i++) {
                String s = getTextFromMessage(mp.getBodyPart(i));
                if (s != null) return s;
            }
        }

        // Если это текстовое письмо, но мы ищем HTML
        if (p.isMimeType("message/rfc822")) {
            return getTextFromMessage((jakarta.mail.Part) p.getContent());
        }

        return null;
    }
}
