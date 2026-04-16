package tools.mail.app;

import com.formdev.flatlaf.FlatLightLaf;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.scheduling.annotation.EnableAsync;

import javax.swing.*;

@SpringBootApplication
@EnableAsync
public class MailSender500Application {

    public static void main(String[] args) {
        // Установка современной темы в стиле Apple/Material
        try {
            FlatLightLaf.setup();
            // Настраиваем глобальные скругления для компонентов (Apple style)
            UIManager.put("Button.arc", 15);
            UIManager.put("Component.arc", 15);
            UIManager.put("ProgressBar.arc", 15);
            UIManager.put("TextComponent.arc", 15);
        } catch (Exception e) {
            System.err.println("Не удалось загрузить тему оформления");
        }

        new SpringApplicationBuilder(MailSender500Application.class)
                .headless(false)
                .run(args);
    }
}
