package tools.mail.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import tools.mail.app.model.Recipient;
import java.time.LocalDateTime;
import java.util.List;

public interface RecipientRepository extends JpaRepository<Recipient, Long> {

    // ДОБАВЬ ЭТУ СТРОКУ (исправляет твою ошибку):
    Recipient findByEmailIgnoreCase(String email);

    @Query("SELECT r FROM Recipient r WHERE UPPER(r.status) = 'PENDING' ORDER BY r.id ASC")
    List<Recipient> findNextToProcess();

    long countByStatusAndSentAtAfter(String status, LocalDateTime date);
}
