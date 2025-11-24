package org.example;

import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;

public class SwingUI {
    private static final String PROPERTIES_PATH = "jira.properties";

    private static JFrame frame;
    private static JTable table;
    private static DefaultTableModel model;
    private static String selectedCsvPath = null;
    private static JComboBox<String> commandBox;
    private static JComboBox<String> delimiterBox;

    private static JPanel mappingPanel;
    private static List<String> currentHeaders = new ArrayList<>();
    private static List<JComboBox<String>> mappingCombos = new ArrayList<>();
    private static boolean refreshing = false;

    private static final String[] JIRA_FIELDS = new String[]{"summary", "description", "component", "parent", "label", "estimate", "skip"};

    public static void launch() {
        SwingUtilities.invokeLater(() -> {

            frame = new JFrame("Jira Automation");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(1400, 600);
            frame.setLayout(new BorderLayout());

            JPanel topBar = new JPanel(new BorderLayout());
            JPanel topRight = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            JButton btnSettings = new JButton("⚙");
            btnSettings.setToolTipText("Configurações do Jira");
            topRight.add(btnSettings);
            topBar.add(topRight, BorderLayout.NORTH);
            frame.add(topBar, BorderLayout.NORTH);

            model = new DefaultTableModel();
            table = new JTable(model);
            frame.add(new JScrollPane(table), BorderLayout.CENTER);

            JPanel bottom = new JPanel(new BorderLayout());

            JPanel leftBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
            JPanel centerBar = new JPanel(new FlowLayout(FlowLayout.CENTER));
            JPanel rightBar = new JPanel(new FlowLayout(FlowLayout.RIGHT));

            JButton btnChooseCsv = new JButton("Escolher CSV");
            JButton btnRun = new JButton("Executar");
            commandBox = new JComboBox<>(new String[]{"Sub-Task", "UserStory"});
            delimiterBox = new JComboBox<>(new String[]{";", ",", "|", "TAB"});
            delimiterBox.setSelectedItem(";");

            leftBar.add(new JLabel("Issue Type:"));
            leftBar.add(commandBox);
            leftBar.add(new JLabel("Delimitador:"));
            leftBar.add(delimiterBox);

            centerBar.add(btnChooseCsv);
            rightBar.add(btnRun);

            bottom.add(leftBar, BorderLayout.WEST);
            bottom.add(centerBar, BorderLayout.CENTER);
            bottom.add(rightBar, BorderLayout.EAST);

            frame.add(bottom, BorderLayout.SOUTH);

            mappingPanel = new JPanel(new BorderLayout());
            mappingPanel.add(new JLabel("", SwingConstants.CENTER), BorderLayout.NORTH);
            frame.add(mappingPanel, BorderLayout.EAST);

            btnChooseCsv.addActionListener(evt -> chooseCsvFile(frame));
            btnRun.addActionListener(evt -> runAutomation());
            btnSettings.addActionListener(evt -> showPropertiesDialog());

            delimiterBox.addActionListener(e -> {
                if (selectedCsvPath != null && !selectedCsvPath.isEmpty()) {
                    loadCsvToTable(selectedCsvPath);
                }
            });

            frame.setVisible(true);
        });
    }

    private static void chooseCsvFile(JFrame parent) {
        JFileChooser chooser = new JFileChooser();
        int result = chooser.showOpenDialog(parent);
        if (result == JFileChooser.APPROVE_OPTION) {
            selectedCsvPath = chooser.getSelectedFile().getAbsolutePath();
            loadCsvToTable(selectedCsvPath);
        }
    }

    private static char getSelectedDelimiterChar() {
        String sel = (String) delimiterBox.getSelectedItem();
        if (sel == null) return ';';
        switch (sel) {
            case ",":
                return ',';
            case "|":
                return '|';
            case "TAB":
                return '\t';
            case ";":
            default:
                return ';';
        }
    }

    private static void loadCsvToTable(String path) {
        try {
            char delimiter = getSelectedDelimiterChar();

            CSVReader reader = new CSVReaderBuilder(new FileReader(path))
                    .withCSVParser(new CSVParserBuilder().withSeparator(delimiter).build())
                    .build();

            List<String[]> rows = reader.readAll();
            if (rows.isEmpty()) {
                JOptionPane.showMessageDialog(null, "CSV vazio.");
                return;
            }

            String[] header = rows.get(0);
            currentHeaders = Arrays.asList(header);

            model.setColumnIdentifiers(header);
            model.setRowCount(0);

            rows.remove(0);
            for (String[] r : rows) {
                model.addRow(r);
            }

            buildMappingUI(header);

        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Erro ao carregar CSV: " + e.getMessage());
        }
    }

    private static void buildMappingUI(String[] header) {
        mappingPanel.removeAll();
        mappingCombos.clear();

        JPanel inner = new JPanel();
        inner.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 6, 4, 6);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel title = new JLabel("Mapeamento De → Para", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        mappingPanel.add(title, BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(inner);
        scroll.setPreferredSize(new Dimension(300, 0));
        mappingPanel.add(scroll, BorderLayout.CENTER);

        Set<String> used = new HashSet<>();

        for (int i = 0; i < header.length; i++) {
            String headerName = header[i] == null ? "" : header[i].trim();

            gbc.gridx = 0;
            gbc.gridy = i;
            JLabel lbl = new JLabel(headerName);
            inner.add(lbl, gbc);

            gbc.gridx = 1;
            JComboBox<String> combo = new JComboBox<>(JIRA_FIELDS);

            String match = findMatchingOption(headerName);
            String matchLower = match == null ? null : match.toLowerCase(Locale.ROOT);
            if (match != null && !used.contains(matchLower) && !"skip".equals(matchLower)) {
                combo.setSelectedItem(match);
                used.add(matchLower);
            } else {
                combo.setSelectedItem("skip");
            }

            mappingCombos.add(combo);
            inner.add(combo, gbc);

            combo.addItemListener(e -> {
                if (e.getStateChange() == ItemEvent.SELECTED && !refreshing) {
                    refreshMappingChoices();
                }
            });
        }

        refreshMappingChoices();

        mappingPanel.revalidate();
        mappingPanel.repaint();
    }

    private static String findMatchingOption(String headerName) {
        if (headerName == null) return null;
        for (String opt : JIRA_FIELDS) {
            if (headerName.equalsIgnoreCase(opt)) {
                return opt;
            }
        }
        return null;
    }

    private static void refreshMappingChoices() {
        refreshing = true;
        try {
            Set<String> taken = new HashSet<>();
            for (JComboBox<String> combo : mappingCombos) {
                String sel = selLower(combo);
                if (sel != null && !"skip".equals(sel)) {
                    taken.add(sel);
                }
            }

            for (JComboBox<String> combo : mappingCombos) {
                String current = selLower(combo);
                DefaultComboBoxModel<String> newModel = new DefaultComboBoxModel<>();
                for (String opt : JIRA_FIELDS) {
                    String lo = opt.toLowerCase(Locale.ROOT);
                    if ("skip".equals(lo) || lo.equals(current) || !taken.contains(lo)) {
                        newModel.addElement(opt);
                    }
                }

                if (!modelEquals(combo.getModel(), newModel)) {
                    combo.setModel(newModel);
                }

                if (current == null) {
                    combo.setSelectedItem("skip");
                } else {
                    combo.setSelectedItem(current);
                }
            }
        } finally {
            refreshing = false;
        }
    }

    private static String selLower(JComboBox<String> combo) {
        Object selObj = combo.getSelectedItem();
        return selObj == null ? null : selObj.toString().toLowerCase(Locale.ROOT);
    }

    private static boolean modelEquals(ComboBoxModel<String> a, DefaultComboBoxModel<String> b) {
        int sizeA = a.getSize();
        int sizeB = b.getSize();
        if (sizeA != sizeB) return false;
        for (int i = 0; i < sizeA; i++) {
            Object ai = a.getElementAt(i);
            Object bi = b.getElementAt(i);
            if (!Objects.equals(ai, bi)) return false;
        }
        return true;
    }

    private static int indexFor(String field) {
        String target = field.toLowerCase(Locale.ROOT);
        for (int i = 0; i < mappingCombos.size(); i++) {
            Object selObj = mappingCombos.get(i).getSelectedItem();
            if (selObj != null) {
                String sel = selObj.toString().toLowerCase(Locale.ROOT);
                if (sel.equals(target)) return i;
            }
        }
        return -1;
    }

    private static String safeGet(String[] row, int idx) {
        if (row == null || idx < 0 || idx >= row.length) return "";
        return row[idx] == null ? "" : row[idx];
    }

    private static void runAutomation() {
        if (selectedCsvPath == null) {
            JOptionPane.showMessageDialog(null, "Selecione um CSV antes!");
            return;
        }

        String cmd = (String) commandBox.getSelectedItem();
        boolean isUserStory = "UserStory".equalsIgnoreCase(cmd);

        int summaryIdx = indexFor("summary");
        int descriptionIdx = indexFor("description");
        int componentIdx = indexFor("component");

        if (summaryIdx < 0 || descriptionIdx < 0 || componentIdx < 0) {
            JOptionPane.showMessageDialog(null, "Mapeie 'summary', 'description' e 'component' antes de executar.");
            return;
        }

        int parentIdx = -1, labelIdx = -1, estimateIdx = -1;
        if (!isUserStory) {
            parentIdx = indexFor("parent");
            labelIdx = indexFor("label");
            estimateIdx = indexFor("estimate");

            if (parentIdx < 0 || labelIdx < 0 || estimateIdx < 0) {
                JOptionPane.showMessageDialog(null, "Para Sub-Task, mapeie 'parent', 'label' e 'estimate' além dos campos obrigatórios.");
                return;
            }
        }

        try {
            Properties props = loadProperties(PROPERTIES_PATH);

            String email = props.getProperty("jira.email");
            String token = props.getProperty("jira.token");
            String domain = props.getProperty("jira.domain");
            String projectKey = props.getProperty("jira.project");

            JiraClient client = new JiraClient(domain, email, token, projectKey);

            char delimiter = getSelectedDelimiterChar();
            CSVReader reader = new CSVReaderBuilder(new FileReader(selectedCsvPath))
                    .withCSVParser(new CSVParserBuilder().withSeparator(delimiter).build())
                    .build();

            List<String[]> rows = reader.readAll();
            if (!rows.isEmpty()) {
                rows.remove(0);
            }

            int count = 0;

            for (String[] row : rows) {
                String summary = safeGet(row, summaryIdx);
                String description = safeGet(row, descriptionIdx);
                String component = safeGet(row, componentIdx);

                if (isUserStory) {
                    client.createStory(summary, description, component);
                } else {
                    String parentKey = safeGet(row, parentIdx);
                    String label = safeGet(row, labelIdx);
                    String estimateStr = safeGet(row, estimateIdx);
                    int estimate = parseIntOrZero(estimateStr);

                    client.createSubTask(
                            parentKey,
                            summary,
                            description,
                            component,
                            label,
                            estimate
                    );
                }
                count++;
            }

            JOptionPane.showMessageDialog(null, count + " tickets criados com sucesso!");

        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Erro ao executar automação: " + e.getMessage());
        }
    }

    private static int parseIntOrZero(String s) {
        try {
            if (s == null) return 0;
            s = s.trim().replace(",", ".");
            if (s.isEmpty()) return 0;
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException nfe) {
                double d = Double.parseDouble(s);
                return (int) Math.round(d);
            }
        } catch (Exception ex) {
            return 0;
        }
    }

    private static Properties loadProperties(String path) throws Exception {
        Properties props = new Properties();
        try (var reader = Files.newBufferedReader(Path.of(path))) {
            props.load(reader);
        }
        return props;
    }

    private static void showPropertiesDialog() {
        Properties props;
        try {
            props = loadProperties(PROPERTIES_PATH);
        } catch (Exception e) {
            props = new Properties();
        }

        String domain = props.getProperty("jira.domain", "");
        String project = props.getProperty("jira.project", "");
        String email = props.getProperty("jira.email", "");
        String token = props.getProperty("jira.token", "");

        JDialog dialog = new JDialog(frame, "Configurações do Jira", true);
        dialog.setSize(650, 300);
        dialog.setLocationRelativeTo(frame);

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 10, 8, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        JTextField domainField = new JTextField(domain, 25);
        JTextField projectField = new JTextField(project, 25);
        JTextField emailField = new JTextField(email, 25);
        JPasswordField tokenField = new JPasswordField(token, 25);

        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Domain (ex: seu-dominio.atlassian.net):"), gbc);
        gbc.gridx = 1;
        panel.add(domainField, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        panel.add(new JLabel("Project Key (ex: ABC):"), gbc);
        gbc.gridx = 1;
        panel.add(projectField, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        panel.add(new JLabel("Email:"), gbc);
        gbc.gridx = 1;
        panel.add(emailField, gbc);

        gbc.gridx = 0; gbc.gridy = 3;
        panel.add(new JLabel("Token:"), gbc);
        gbc.gridx = 1;
        panel.add(tokenField, gbc);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnSave = new JButton("Salvar");
        JButton btnCancel = new JButton("Cancelar");
        buttons.add(btnCancel);
        buttons.add(btnSave);

        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2;
        panel.add(buttons, gbc);

        dialog.setContentPane(panel);

        btnCancel.addActionListener(e -> dialog.dispose());

        btnSave.addActionListener(e -> {
            try {
                String newDomain = domainField.getText().trim();
                String newProject = projectField.getText().trim();
                String newEmail = emailField.getText().trim();
                String newToken = new String(tokenField.getPassword()).trim();
                Properties propsToSave = new Properties();
                propsToSave.setProperty("jira.domain", newDomain);
                propsToSave.setProperty("jira.project", newProject);
                propsToSave.setProperty("jira.email", newEmail);
                propsToSave.setProperty("jira.token", newToken);

                saveProperties(propsToSave, PROPERTIES_PATH);

                JOptionPane.showMessageDialog(dialog, "Configurações salvas com sucesso.");
                dialog.dispose();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, "Erro ao salvar configurações: " + ex.getMessage());
            }
        });

        dialog.setVisible(true);
    }

    private static void saveProperties(Properties props, String path) throws Exception {
        try (var writer = Files.newBufferedWriter(Path.of(path))) {
            props.store(writer, "Jira settings - updated by Jira Automation UI");
        }
    }
}
