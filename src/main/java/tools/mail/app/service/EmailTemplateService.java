package tools.mail.app.service;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeUtility;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;

@Service
@Getter
@Setter
public class EmailTemplateService {
    private String subject;
    private String htmlContent;
    private File emlFile;
    private File attachmentFile;

    public void loadTemplate(File file) throws Exception {
        this.emlFile = file;
        Properties props = System.getProperties();
        Session session = Session.getDefaultInstance(props, null);

        try (FileInputStream fis = new FileInputStream(file)) {
            MimeMessage message = new MimeMessage(session, fis);

            // ИСПРАВЛЕНИЕ КОДИРОВКИ ТЕМЫ
            String rawSubject = message.getSubject();
            if (rawSubject != null) {
                // Сначала пробуем стандартное декодирование
                String decoded = MimeUtility.decodeText(rawSubject);
                // Если получили кракозябры (проверяем наличие специфических символов), чиним принудительно
                if (isGarbage(decoded)) {
                    this.subject = new String(rawSubject.getBytes("ISO-8859-1"), "UTF-8");
                } else {
                    this.subject = decoded;
                }
            } else {
                this.subject = "Без темы";
            }

            this.htmlContent = getTextFromMessage(message);
        }
    }

    // Проверка, является ли строка "мусором" из-за кодировки
    private boolean isGarbage(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == 'Ð' || s.charAt(i) == 'â') return true;
        }
        return false;
    }

    private String getTextFromMessage(jakarta.mail.Part p) throws Exception {
        if (p.isMimeType("text/html")) return (String) p.getContent();
        if (p.isMimeType("multipart/*")) {
            jakarta.mail.internet.MimeMultipart mp = (jakarta.mail.internet.MimeMultipart) p.getContent();
            for (int i = 0; i < mp.getCount(); i++) {
                String s = getTextFromMessage(mp.getBodyPart(i));
                if (s != null) return s;
            }
        }
        return null;
    }
}
