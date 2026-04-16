package tools.mail.app;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync // Важно для фоновой рассылки!
public class MailSender500Application {

    public static void main(String[] args) {
        // Настройка headless = false нужна, чтобы Swing мог рисовать окна
        SpringApplicationBuilder builder = new SpringApplicationBuilder(MailSender500Application.class);
        builder.headless(false);
        builder.run(args);
    }
}
