package tools.mail.app.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.mail.app.model.Recipient;
import tools.mail.app.repository.RecipientRepository;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Arrays;

@Service
@RequiredArgsConstructor
public class ImportService {

    private final RecipientRepository repository;

    @Transactional
    public void importFromTextFile(File file) throws Exception {
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                // Разбиваем строку по точке с запятой
                String[] emails = line.split(";");

                Arrays.stream(emails)
                        .map(String::trim)
                        .filter(email -> !email.isEmpty() && email.contains("@"))
                        .forEach(email -> {
                            // Проверяем, нет ли уже такого email в базе, чтобы не слать дважды
                            if (repository.findAll().stream().noneMatch(r -> r.getEmail().equalsIgnoreCase(email))) {
                                Recipient recipient = Recipient.builder()
                                        .email(email)
                                        .status("PENDING")
                                        .build();
                                repository.save(recipient);
                            }
                        });
            }
        }
    }
}
