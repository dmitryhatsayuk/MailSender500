package tools.mail.app.service;

import jakarta.mail.BodyPart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import lombok.Getter;
import org.springframework.stereotype.Service;
import java.io.FileInputStream;
import java.io.File;
import java.util.Properties;

@Service
@Getter
public class EmailTemplateService {

    private String subject;
    private String htmlContent;
    private File emlFile;

    public void loadTemplate(File file) throws Exception {
        Properties props = System.getProperties();
        Session session = Session.getDefaultInstance(props, null);

        try (FileInputStream fis = new FileInputStream(file)) {
            MimeMessage message = new MimeMessage(session, fis);
            this.subject = message.getSubject();
            this.emlFile = file;
            this.htmlContent = getTextFromMessage(message);
        }
    }

    // Вспомогательный метод для извлечения HTML из сложной структуры EML
    private String getTextFromMessage(jakarta.mail.Part p) throws Exception {
        if (p.isMimeType("text/html")) {
            return (String) p.getContent();
        }
        if (p.isMimeType("multipart/*")) {
            MimeMultipart mp = (MimeMultipart) p.getContent();
            for (int i = 0; i < mp.getCount(); i++) {
                String s = getTextFromMessage(mp.getBodyPart(i));
                if (s != null) return s;
            }
        }
        return null;
    }
}
