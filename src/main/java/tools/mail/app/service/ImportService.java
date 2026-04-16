package tools.mail.app.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.mail.app.model.Recipient;
import tools.mail.app.repository.RecipientRepository;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ImportService {
    private final RecipientRepository repository;

    @Transactional
    public ImportResult importFromTextFile(File file) throws Exception {
        int newAdded = 0;
        int totalInFile = 0;

        // 1. Возвращаем ошибки в очередь
        List<Recipient> all = repository.findAll();
        for (Recipient r : all) {
            if ("ERROR".equalsIgnoreCase(r.getStatus())) {
                r.setStatus("PENDING");
                r.setErrorMessage(null);
                repository.save(r);
            }
        }

        // 2. Читаем файл
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] emails = line.split(";");
                for (String email : emails) {
                    String cleanEmail = email.trim();
                    if (!cleanEmail.isEmpty() && cleanEmail.contains("@")) {
                        totalInFile++;
                        // Добавляем только если такого email ВООБЩЕ нет в базе (ни SENT, ни PENDING)
                        if (repository.findByEmailIgnoreCase(cleanEmail) == null) {
                            repository.save(Recipient.builder().email(cleanEmail).status("PENDING").build());
                            newAdded++;
                        }
                    }
                }
            }
        }
        return new ImportResult(totalInFile, newAdded, (int) repository.count());
    }

    public record ImportResult(int totalInFile, int newlyAdded, int totalInDb) {}
}
