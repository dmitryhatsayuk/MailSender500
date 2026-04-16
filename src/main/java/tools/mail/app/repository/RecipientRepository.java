package tools.mail.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import tools.mail.app.model.Recipient;
import java.time.LocalDateTime;
import java.util.List;

public interface RecipientRepository extends JpaRepository<Recipient, Long> {

    // Найти следующее письмо для отправки
    Recipient findFirstByStatusOrderByIdAsc(String status);

    // Посчитать, сколько отправили за сегодня (для лимита в 500)
    long countByStatusAndSentAtAfter(String status, LocalDateTime date);

    List<Recipient> findAllByStatus(String status);
}
