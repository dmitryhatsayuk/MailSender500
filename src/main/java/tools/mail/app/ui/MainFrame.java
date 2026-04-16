package tools.mail.app.ui;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.mail.app.repository.RecipientRepository;
import tools.mail.app.service.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.time.LocalDateTime;
import java.util.Scanner;
import java.util.prefs.Preferences;

@Component
@RequiredArgsConstructor
public class MainFrame extends JFrame {

    private final ImportService importService;
    private final EmailTemplateService templateService;
    private final MailDispatcher mailDispatcher;
    private final RecipientRepository repository;

    private final Preferences prefs = Preferences.userNodeForPackage(MainFrame.class);
    private File selectedBaseFile;

    private final JProgressBar progressBar = new JProgressBar(0, 500);
    private final JLabel statusLabel = new JLabel("Статус: Готов", SwingConstants.CENTER);
    private final JLabel smtpIndicator = new JLabel("SMTP: ОЖИДАНИЕ", SwingConstants.CENTER);
    private final JLabel counterLabel = new JLabel("Отправлено сегодня: 0 / 500", SwingConstants.CENTER);

    private final JLabel baseCheck = new JLabel("Выбран: 0");
    private final JLabel emlCheck = new JLabel("❌ Не выбрано");
    private final JLabel attachCheck = new JLabel("—");

    private final JButton btnStart = new JButton("СТАРТ");
    private final JButton btnPause = new JButton("ПАУЗА");
    private final JButton btnImport = new JButton("Импорт");

    private boolean blinkState = false;
    private boolean finishDialogShown = false;

    @PostConstruct
    public void init() {
        setTitle("MailSender 500");
        setupIcon();
        setSize(550, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(new EmptyBorder(20, 20, 20, 20));

        // Центрирование служебных элементов
        smtpIndicator.setAlignmentX(java.awt.Component.CENTER_ALIGNMENT);
        statusLabel.setAlignmentX(java.awt.Component.CENTER_ALIGNMENT);
        counterLabel.setAlignmentX(java.awt.Component.CENTER_ALIGNMENT);
        progressBar.setAlignmentX(java.awt.Component.CENTER_ALIGNMENT);

        // --- 1. СТРОКА БАЗЫ (ВЫБОР + ИМПОРТ + СТАТУС СПРАВА) ---
        JPanel baseRow = new JPanel(new BorderLayout(10, 0));
        baseRow.setMaximumSize(new Dimension(550, 35));

        JButton btnSelectBase = new JButton("1. Выбрать базу (.txt)");
        btnSelectBase.addActionListener(e -> selectBaseFile());

        JPanel baseEastPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        baseCheck.setPreferredSize(new Dimension(100, 25));
        btnImport.setEnabled(false);
        btnImport.addActionListener(e -> performImport());

        baseEastPanel.add(btnImport);
        baseEastPanel.add(baseCheck);

        baseRow.add(btnSelectBase, BorderLayout.CENTER);
        baseRow.add(baseEastPanel, BorderLayout.EAST);

        mainPanel.add(baseRow);
        mainPanel.add(Box.createVerticalStrut(15));

        // --- 2. СТРОКА ПИСЬМА ---
        mainPanel.add(createFileRow("2. Выбрать письмо (.eml)", emlCheck, e -> selectEmlFile()));
        mainPanel.add(Box.createVerticalStrut(10));

        // --- 3. СТРОКА ВЛОЖЕНИЯ ---
        mainPanel.add(createFileRow("3. Вложение (опция)", attachCheck, e -> selectAttachFile()));

        mainPanel.add(Box.createVerticalStrut(25));
        smtpIndicator.setFont(new Font("SansSerif", Font.BOLD, 14));
        mainPanel.add(smtpIndicator);
        mainPanel.add(Box.createVerticalStrut(15));

        // --- 4. УПРАВЛЕНИЕ ---
        JPanel controlPanel = new JPanel(new GridLayout(1, 2, 15, 0));
        controlPanel.setMaximumSize(new Dimension(400, 45));

        btnStart.setEnabled(false);
        btnStart.setBackground(new Color(0, 122, 255));
        btnStart.setForeground(Color.WHITE);
        btnStart.addActionListener(e -> {
            finishDialogShown = false;
            mailDispatcher.runDistribution();
        });

        btnPause.addActionListener(e -> mailDispatcher.setPaused(!mailDispatcher.isPaused()));

        controlPanel.add(btnStart);
        controlPanel.add(btnPause);
        mainPanel.add(controlPanel);

        mainPanel.add(Box.createVerticalStrut(25));

        // --- 5. ПРОГРЕСС ---
        progressBar.setMaximum(mailDispatcher.getDailyLimit());
        progressBar.setStringPainted(true);
        progressBar.setMaximumSize(new Dimension(500, 22));

        mainPanel.add(progressBar);
        mainPanel.add(Box.createVerticalStrut(15));
        mainPanel.add(counterLabel);
        mainPanel.add(Box.createVerticalStrut(10));
        mainPanel.add(statusLabel);

        add(mainPanel);

        loadLastFiles();
        new Timer(500, e -> refreshUi()).start();
        setVisible(true);
    }

    private void selectBaseFile() {
        JFileChooser fc = new JFileChooser(prefs.get("BASE_DIR", "."));
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            selectedBaseFile = fc.getSelectedFile();
            int count = countEmailsInFile(selectedBaseFile);
            baseCheck.setText("<html><font color='gray'>Выбран: " + count + "</font></html>");
            btnImport.setEnabled(true);
        }
    }

    private void performImport() {
        try {
            ImportService.ImportResult res = importService.importFromTextFile(selectedBaseFile);
            prefs.put("BASE", selectedBaseFile.getAbsolutePath());
            prefs.put("BASE_DIR", selectedBaseFile.getParent());
            btnImport.setEnabled(false);
            baseCheck.setText("<html><b><font color='#28a745'>✓ Готов: " + res.totalInDb() + "</font></b></html>");
            checkReady();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Ошибка импорта");
        }
    }

    private int countEmailsInFile(File file) {
        try (Scanner s = new Scanner(file)) {
            int count = 0;
            while (s.hasNextLine()) {
                String line = s.nextLine();
                if (!line.trim().isEmpty()) {
                    count += line.split(";").length;
                }
            }
            return count;
        } catch (Exception e) { return 0; }
    }

    private void selectEmlFile() {
        JFileChooser fc = new JFileChooser(prefs.get("EML_DIR", "."));
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                File f = fc.getSelectedFile();
                templateService.loadTemplate(f);
                prefs.put("EML", f.getAbsolutePath());
                prefs.put("EML_DIR", f.getParent());
                checkReady();
            } catch (Exception ex) { JOptionPane.showMessageDialog(this, "Ошибка шаблона"); }
        }
    }

    private void selectAttachFile() {
        JFileChooser fc = new JFileChooser(prefs.get("ATTACH_DIR", "."));
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File f = fc.getSelectedFile();
            templateService.setAttachmentFile(f);
            prefs.put("ATTACH", f.getAbsolutePath());
            prefs.put("ATTACH_DIR", f.getParent());
            attachCheck.setText("<html><font color='#28a745'>✓ " + truncate(f.getName()) + "</font></html>");
        }
    }

    private String truncate(String name) {
        return name.length() > 15 ? name.substring(0, 12) + "..." : name;
    }

    private void refreshUi() {
        int limit = mailDispatcher.getDailyLimit();
        long sent = repository.countByStatusAndSentAtAfter("SENT", LocalDateTime.now().minusDays(1));
        progressBar.setValue((int) sent);
        counterLabel.setText(String.format("Отправлено сегодня: %d / %d (Всего в базе: %d)", sent, limit, repository.count()));

        boolean isPaused = mailDispatcher.isPaused();
        btnPause.setText(isPaused ? "ПРОДОЛЖИТЬ" : "ПАУЗА");
        btnPause.setEnabled(mailDispatcher.isRunning());
        btnStart.setEnabled(!mailDispatcher.isRunning() && repository.count() > 0 && templateService.getEmlFile() != null);

        if (isPaused) statusLabel.setText("Статус: НА ПАУЗЕ");
        else statusLabel.setText(mailDispatcher.isRunning() ? "Статус: Рассылка..." : "Статус: Готов");

        if (mailDispatcher.isFinished() && !finishDialogShown) {
            finishDialogShown = true;
            JOptionPane.showMessageDialog(this, "Рассылка завершена успешно!");
        }

        updateSmtpIndicator(isPaused);
    }

    private void updateSmtpIndicator(boolean isPaused) {
        MailDispatcher.SmtpStatus s = mailDispatcher.getSmtpStatus();
        switch (s) {
            case WAITING -> { smtpIndicator.setText("SMTP: ОЖИДАНИЕ"); smtpIndicator.setForeground(Color.GRAY); }
            case OK -> { smtpIndicator.setText("SMTP: OK"); smtpIndicator.setForeground(new Color(40, 167, 69)); }
            case ERROR -> {
                smtpIndicator.setText("SMTP: ОШИБКА СЕРВЕРА");
                if (isPaused) {
                    blinkState = !blinkState;
                    smtpIndicator.setForeground(blinkState ? Color.RED : new Color(240, 240, 240));
                } else smtpIndicator.setForeground(Color.RED);
            }
        }
    }

    private JPanel createFileRow(String title, JLabel check, java.awt.event.ActionListener action) {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setMaximumSize(new Dimension(550, 35));
        JButton btn = new JButton(title);
        btn.addActionListener(action);
        row.add(btn, BorderLayout.CENTER);

        JPanel eastPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        check.setPreferredSize(new Dimension(115, 25));
        eastPanel.add(check);

        row.add(eastPanel, BorderLayout.EAST);
        return row;
    }

    private void loadLastFiles() {
        try {
            String b = prefs.get("BASE", null);
            if (b != null && new File(b).exists()) {
                selectedBaseFile = new File(b);
                baseCheck.setText("<html><b><font color='#28a745'>✓ Готов: " + repository.count() + "</font></b></html>");
            }
            String e = prefs.get("EML", null);
            if (e != null && new File(e).exists()) {
                templateService.loadTemplate(new File(e));
                emlCheck.setText("<html><font color='#28a745'>✓ Загружено</font></html>");
            }
            String a = prefs.get("ATTACH", null);
            if (a != null && new File(a).exists()) {
                File f = new File(a);
                templateService.setAttachmentFile(f);
                attachCheck.setText("<html><font color='#28a745'>✓ " + truncate(f.getName()) + "</font></html>");
            }
            checkReady();
        } catch (Exception ignored) {}
    }

    private void checkReady() {
        boolean he = templateService.getEmlFile() != null;
        if (he) emlCheck.setText("<html><font color='#28a745'>✓ Загружено</font></html>");
    }

    private void setupIcon() {
        try {
            java.net.URL url = getClass().getResource("/icon.png");
            if (url != null) setIconImage(new ImageIcon(url).getImage());
        } catch (Exception ignored) {}
    }

    interface FileProcessor { void process(File f) throws Exception; }
}
