package com.bdcyberninja.gui;

import com.bdcyberninja.core.PortScanner;
import com.bdcyberninja.core.PortScanner.Result;
import com.bdcyberninja.core.PortScanner.ScanMode;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class ScannerGUI extends JFrame {

    private final JTextField targetField = new JTextField("127.0.0.1");
    private final JComboBox<ScanMode> modeBox = new JComboBox<>(ScanMode.values());
    private final JButton startBtn = new JButton("Start Scan");
    private final JProgressBar progress = new JProgressBar();
    private final JTextArea output = new JTextArea();

    private PortScanner scanner;

    public ScannerGUI() {
        super("Advanced Port Scanner (Swing)");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));
        ((JComponent) getContentPane()).setBorder(new EmptyBorder(10, 10, 10, 10));

        // Dark mode colors
        Color bgColor = new Color(20, 20, 20);
        Color fgColor = new Color(200, 255, 200);
        Color btnColor = new Color(0, 255, 255);
        Color textAreaBg = new Color(30, 30, 30);

        getContentPane().setBackground(bgColor);

        // ===== HEADER SECTION =====
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(bgColor);

        JLabel title = new JLabel("Advance Network Port Scanner", SwingConstants.CENTER);
        title.setForeground(Color.GREEN);
        title.setFont(new Font("Monospaced", Font.BOLD, 22));

        JLabel subtitle = new JLabel("Created by Noob_Builders", SwingConstants.CENTER);
        subtitle.setForeground(new Color(180, 180, 180));
        subtitle.setFont(new Font("Monospaced", Font.PLAIN, 12));

        JLabel tagline = new JLabel("Enter a target IP Address to uncover potential open ports of various protocols in the network", SwingConstants.CENTER);
        tagline.setForeground(new Color(130, 130, 130));
        tagline.setFont(new Font("Monospaced", Font.PLAIN, 11));

        Box headerBox = Box.createVerticalBox();
        headerBox.add(title);
        headerBox.add(Box.createVerticalStrut(5));
        headerBox.add(subtitle);
        headerBox.add(Box.createVerticalStrut(3));
        headerBox.add(tagline);

        header.add(headerBox, BorderLayout.CENTER);

        // ===== INPUT SECTION =====
        JPanel topPanel = new JPanel(new BorderLayout(5, 5));
        topPanel.setBackground(bgColor);

        JPanel leftPanel = new JPanel(new GridLayout(1, 2, 5, 5));
        leftPanel.setBackground(bgColor);
        targetField.setBackground(Color.BLACK);
        targetField.setForeground(fgColor);
        targetField.setCaretColor(fgColor);

        modeBox.setBackground(Color.DARK_GRAY);
        modeBox.setForeground(fgColor);

        leftPanel.add(targetField);
        leftPanel.add(modeBox);
        topPanel.add(leftPanel, BorderLayout.CENTER);

        startBtn.setBackground(btnColor);
        startBtn.setForeground(Color.BLACK);
        topPanel.add(startBtn, BorderLayout.EAST);

        // ===== COMBINE HEADER + INPUT =====
        Box northSection = Box.createVerticalBox();
        northSection.add(header);
        northSection.add(Box.createVerticalStrut(10));
        northSection.add(topPanel);
        add(northSection, BorderLayout.NORTH);

        // ===== PROGRESS BAR =====
        progress.setStringPainted(true);
        progress.setForeground(Color.GREEN);
        progress.setBackground(Color.DARK_GRAY);
        add(progress, BorderLayout.SOUTH);

        // ===== OUTPUT TEXT AREA =====
        output.setEditable(false);
        output.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        output.setBackground(textAreaBg);
        output.setForeground(fgColor);
        DefaultCaret caret = (DefaultCaret) output.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
        JScrollPane scrollPane = new JScrollPane(output);
        scrollPane.getViewport().setBackground(textAreaBg);
        add(scrollPane, BorderLayout.CENTER);

        // ===== Button Action =====
        startBtn.addActionListener(e -> doScan());

        setSize(900, 600);
        setLocationRelativeTo(null);
    }

    private void doScan() {
        String target = targetField.getText().trim();
        ScanMode mode = (ScanMode) modeBox.getSelectedItem();

        output.setText("");
        progress.setValue(0);
        startBtn.setEnabled(false);

        scanner = new PortScanner();

        SwingWorker<List<Result>, String> worker = new SwingWorker<>() {
            @Override
            protected List<Result> doInBackground() throws Exception {
                InetAddress addr;
                try {
                    addr = InetAddress.getByName(target);
                } catch (UnknownHostException e) {
                    publish("Invalid host: " + target);
                    cancel(true);
                    return null;
                }

                return scanner.scan(addr, mode, new PortScanner.ProgressCallback() {
                    @Override
                    public void update(int current, int total) {
                        setProgress((int) ((current * 100.0) / total));
                    }

                    @Override
                    public void done(Duration elapsed) {
                        publish(String.format("\nScan finished in %d ms\n\n", elapsed.toMillis()));
                    }
                });
            }

            @Override
            protected void process(List<String> chunks) {
                for (String s : chunks) output.append(s);
            }

            @Override
            protected void done() {
                startBtn.setEnabled(true);
                try {
                    List<Result> res = get();
                    if (res != null) {
                        res.stream().filter(Result::open)
                                .sorted((a, b) -> Integer.compare(a.port(), b.port()))
                                .forEach(r -> output.append(String.format("%5d/%-3s  %s\n",
                                        r.port(), r.proto(),
                                        r.banner() == null ? "open" : r.banner().trim())));
                    }
                } catch (InterruptedException | ExecutionException e) {
                    output.append("\nScan aborted.\n");
                }
                scanner.shutdown();
            }
        };

        worker.addPropertyChangeListener(evt -> {
            if ("progress".equals(evt.getPropertyName())) {
                int p = (Integer) evt.getNewValue();
                progress.setValue(p);
                progress.setString(p + "%");
            }
        });

        worker.execute();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ScannerGUI().setVisible(true));
    }
}
