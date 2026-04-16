package tools.mail.app.ui;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.mail.app.repository.RecipientRepository;
import tools.mail.app.service.EmailTemplateService;
import tools.mail.app.service.ImportService;
import tools.mail.app.service.MailDispatcher;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class MainFrame extends JFrame {

    // Внедряем зависимости через конструктор (Lombok @RequiredArgsConstructor)
    private final ImportService importService;
    private final EmailTemplateService templateService;
    private final MailDispatcher mailDispatcher;
    private final RecipientRepository repository;

    // Элементы интерфейса
    private final JProgressBar progressBar = new JProgressBar(0, 500);
    private final JLabel statusLabel = new JLabel("Статус: Ожидание действий");
    private final JLabel counterLabel = new JLabel("Отправлено сегодня: 0 / 500");
    private final JButton btnStart = new JButton("СТАРТ РАССЫЛКИ");

    @PostConstruct
    public void init() {
        // Настройки окна
        setTitle("MailSender 500 - Панель управления");
        setSize(450, 350);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null); // Окно по центру

        // Основной контейнер с отступами
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new GridLayout(6, 1, 10, 10));
        mainPanel.setBorder(new EmptyBorder(20, 20, 20, 20));

        // 1. Кнопка выбора базы
        JButton btnLoadBase = new JButton("📁 1. Выбрать базу контактов (.txt)");
        btnLoadBase.addActionListener(e -> selectContactsFile());

        // 2. Кнопка выбора письма
        JButton btnLoadEml = new JButton("📧 2. Выбрать файл письма (.eml)");
        btnLoadEml.addActionListener(e -> selectEmlFile());

        // 3. Кнопка Старт
        btnStart.setFont(new Font("Arial", Font.BOLD, 14));
        btnStart.setBackground(new Color(46, 204, 113));
        btnStart.setForeground(Color.BLACK);
        btnStart.setEnabled(false); // Выключена, пока не загрузим файлы
        btnStart.addActionListener(e -> {
            btnStart.setEnabled(false);
            statusLabel.setText("Статус: Рассылка в процессе...");
            mailDispatcher.runDistribution();
        });

        // Настройка прогресс-бара
        progressBar.setStringPainted(true);
        progressBar.setForeground(new Color(52, 152, 219));

        // Добавляем компоненты на панель
        mainPanel.add(btnLoadBase);
        mainPanel.add(btnLoadEml);
        mainPanel.add(btnStart);
        mainPanel.add(progressBar);
        mainPanel.add(counterLabel);
        mainPanel.add(statusLabel);

        add(mainPanel);

        // Таймер для обновления UI раз в секунду
        Timer uiTimer = new Timer(1000, e -> updateStatistics());
        uiTimer.start();

        setVisible(true);
    }

    private void selectContactsFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Выберите текстовый файл с email");
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                File file = chooser.getSelectedFile();
                importService.importFromTextFile(file);
                JOptionPane.showMessageDialog(this, "База успешно загружена в БД!");
                checkReadyState();
            } catch (Exception ex) {
                showError("Ошибка загрузки базы: " + ex.getMessage());
            }
        }
    }

    private void selectEmlFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Выберите файл сообщения .eml");
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                templateService.loadTemplate(chooser.getSelectedFile());
                JOptionPane.showMessageDialog(this, "Шаблон письма принят: " + templateService.getSubject());
                checkReadyState();
            } catch (Exception ex) {
                showError("Ошибка парсинга EML: " + ex.getMessage());
            }
        }
    }

    private void updateStatistics() {
        // Считаем отправленные за последние 24 часа
        long sentCount = repository.countByStatusAndSentAtAfter("SENT", LocalDateTime.now().minusDays(1));
        long totalInQueue = repository.count(); // Всего записей

        progressBar.setValue((int) sentCount);
        counterLabel.setText(String.format("Отправлено сегодня: %d / 500 (Всего в базе: %d)", sentCount, totalInQueue));

        if (sentCount >= 500) {
            statusLabel.setText("Статус: Дневной лимит исчерпан!");
            btnStart.setEnabled(false);
        }
    }

    private void checkReadyState() {
        // Если в базе есть хоть кто-то и файл EML выбран — включаем кнопку
        if (repository.count() > 0 && templateService.getEmlFile() != null) {
            btnStart.setEnabled(true);
            statusLabel.setText("Статус: Готов к запуску");
        }
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Ошибка", JOptionPane.ERROR_MESSAGE);
    }
}
