package com.ritense;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.formdev.flatlaf.FlatDarculaLaf;
import com.formdev.flatlaf.FlatIntelliJLaf;
import com.github.erosb.jsonsKema.FormatValidationPolicy;
import com.github.erosb.jsonsKema.JsonParser;
import com.github.erosb.jsonsKema.JsonValue;
import com.github.erosb.jsonsKema.Schema;
import com.github.erosb.jsonsKema.SchemaLoader;
import com.github.erosb.jsonsKema.ValidationFailure;
import com.github.erosb.jsonsKema.Validator;
import com.github.erosb.jsonsKema.ValidatorConfig;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.Theme;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.*;
import javax.swing.event.CaretEvent;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class InwonerplanValidator {

    static ObjectMapper objectMapper = new ObjectMapper();

    private static final int MAX_TREE_DEPTH = 50;
    private static final int MAX_TREE_EXPAND_DEPTH = 3;

    private JFrame frame;
    private RSyntaxTextArea objectTextArea;
    private RSyntaxTextArea schemaTextArea;
    private JTextArea rightTextArea;
    private JTree jsonTree;
    private JScrollPane treeScrollPane;
    private JTabbedPane rightTabPanel;
    private JButton formatButton;
    private JButton validateButton;
    private JButton sanitizeButton;
    private JButton visualizeButton;

    // File tracking
    private File currentFile;
    private boolean modified;

    // Status bar
    private JLabel statusFileLabel;
    private JLabel statusCursorLabel;
    private JLabel statusMessageLabel;

    // Theme tracking
    private boolean darkTheme = false;

    public static void main(String[] args) {
        objectMapper.findAndRegisterModules();
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        FlatIntelliJLaf.setup();

        SwingUtilities.invokeLater(() -> new InwonerplanValidator().createAndShowGUI());
    }

    private void createAndShowGUI() {
        frame = new JFrame("Inwonerplan Editor — Untitled");
        frame.setSize(1400, 1000);
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                doExit();
            }
        });
        frame.setLayout(new BorderLayout());

        // Menu bar
        frame.setJMenuBar(createMenuBar());

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerLocation(500);

        // Left panel with tabs
        JPanel leftPanel = new JPanel(new BorderLayout());
        JTabbedPane leftTabPanel = new JTabbedPane();
        leftPanel.add(leftTabPanel, BorderLayout.CENTER);

        // Object pane — RSyntaxTextArea with JSON highlighting
        objectTextArea = createSyntaxTextArea(true);
        objectTextArea.setText(readInwonerplanJson());
        objectTextArea.setCaretPosition(0);
        objectTextArea.discardAllEdits();
        RTextScrollPane objectScrollPane = new RTextScrollPane(objectTextArea);
        leftTabPanel.add("Object", objectScrollPane);

        // Schema pane — read-only
        schemaTextArea = createSyntaxTextArea(false);
        schemaTextArea.setText(readInwonerplanSchema());
        schemaTextArea.setCaretPosition(0);
        schemaTextArea.setEditable(false);
        RTextScrollPane schemaScrollPane = new RTextScrollPane(schemaTextArea);
        leftTabPanel.add("Schema", schemaScrollPane);

        splitPane.setLeftComponent(leftPanel);

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout());
        formatButton = new JButton("Format");
        validateButton = new JButton("Validate");
        sanitizeButton = new JButton("Sanitize");
        buttonPanel.add(formatButton);
        buttonPanel.add(validateButton);
        buttonPanel.add(sanitizeButton);
        leftPanel.add(buttonPanel, BorderLayout.SOUTH);

        // Right panel
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightTabPanel = new JTabbedPane();

        jsonTree = new JTree();
        jsonTree.setVisible(false);
        treeScrollPane = new JScrollPane(jsonTree);
        rightTabPanel.add("JSON Tree", treeScrollPane);

        rightTextArea = new JTextArea();
        rightTextArea.setEditable(false);
        JScrollPane rightTextScrollPane = new JScrollPane(rightTextArea);
        rightTabPanel.add("Validation", rightTextScrollPane);
        rightPanel.add(rightTabPanel, BorderLayout.CENTER);

        visualizeButton = new JButton("Visualize JSON");
        rightPanel.add(visualizeButton, BorderLayout.SOUTH);

        splitPane.setRightComponent(rightPanel);

        // Wire button actions
        formatButton.addActionListener(e -> doFormat());
        visualizeButton.addActionListener(e -> doVisualize());
        validateButton.addActionListener(e -> doValidate());
        sanitizeButton.addActionListener(e -> doSanitize());

        // Track modifications
        objectTextArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { markModified(); }
            @Override public void removeUpdate(DocumentEvent e) { markModified(); }
            @Override public void changedUpdate(DocumentEvent e) { markModified(); }
        });

        // Cursor position tracking for status bar
        objectTextArea.addCaretListener(this::updateCursorPosition);

        // Status bar
        JPanel statusBar = createStatusBar();

        frame.add(splitPane, BorderLayout.CENTER);
        frame.add(statusBar, BorderLayout.SOUTH);

        modified = false;
        frame.setVisible(true);
    }

    private RSyntaxTextArea createSyntaxTextArea(boolean editable) {
        RSyntaxTextArea textArea = new RSyntaxTextArea();
        textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JSON);
        textArea.setCodeFoldingEnabled(true);
        textArea.setAntiAliasingEnabled(true);
        textArea.setTabSize(2);
        textArea.setEditable(editable);
        applySyntaxTheme(textArea);
        return textArea;
    }

    private void applySyntaxTheme(RSyntaxTextArea textArea) {
        try {
            String themePath = darkTheme
                    ? "/org/fife/ui/rsyntaxtextarea/themes/dark.xml"
                    : "/org/fife/ui/rsyntaxtextarea/themes/idea.xml";
            Theme theme = Theme.load(getClass().getResourceAsStream(themePath));
            theme.apply(textArea);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ---- Menu Bar ----

    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        // File menu
        JMenu fileMenu = new JMenu("File");
        fileMenu.setMnemonic(KeyEvent.VK_F);

        JMenuItem newItem = new JMenuItem("New");
        newItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        newItem.addActionListener(e -> doNew());

        JMenuItem openItem = new JMenuItem("Open...");
        openItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        openItem.addActionListener(e -> doOpen());

        JMenuItem saveItem = new JMenuItem("Save");
        saveItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        saveItem.addActionListener(e -> doSave());

        JMenuItem saveAsItem = new JMenuItem("Save As...");
        saveAsItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx() | InputEvent.SHIFT_DOWN_MASK));
        saveAsItem.addActionListener(e -> doSaveAs());

        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.addActionListener(e -> doExit());

        fileMenu.add(newItem);
        fileMenu.add(openItem);
        fileMenu.addSeparator();
        fileMenu.add(saveItem);
        fileMenu.add(saveAsItem);
        fileMenu.addSeparator();
        fileMenu.add(exitItem);

        // Edit menu
        JMenu editMenu = new JMenu("Edit");
        editMenu.setMnemonic(KeyEvent.VK_E);

        JMenuItem undoItem = new JMenuItem("Undo");
        undoItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        undoItem.addActionListener(e -> objectTextArea.undoLastAction());

        JMenuItem redoItem = new JMenuItem("Redo");
        redoItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Y, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        redoItem.addActionListener(e -> objectTextArea.redoLastAction());

        JMenuItem findItem = new JMenuItem("Find/Replace...");
        findItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        findItem.addActionListener(e -> {
            // RSyntaxTextArea has built-in Ctrl+H for find/replace; trigger the action
            org.fife.ui.rtextarea.SearchEngine.find(objectTextArea,
                    new org.fife.ui.rtextarea.SearchContext());
        });

        JMenuItem formatItem = new JMenuItem("Format JSON");
        formatItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx() | InputEvent.SHIFT_DOWN_MASK));
        formatItem.addActionListener(e -> doFormat());

        editMenu.add(undoItem);
        editMenu.add(redoItem);
        editMenu.addSeparator();
        editMenu.add(findItem);
        editMenu.addSeparator();
        editMenu.add(formatItem);

        // Tools menu
        JMenu toolsMenu = new JMenu("Tools");
        toolsMenu.setMnemonic(KeyEvent.VK_T);

        JMenuItem validateItem = new JMenuItem("Validate");
        validateItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0));
        validateItem.addActionListener(e -> doValidate());

        JMenuItem sanitizeItem = new JMenuItem("Sanitize");
        sanitizeItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F6, 0));
        sanitizeItem.addActionListener(e -> doSanitize());

        JMenuItem visualizeItem = new JMenuItem("Visualize Tree");
        visualizeItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F7, 0));
        visualizeItem.addActionListener(e -> doVisualize());

        toolsMenu.add(validateItem);
        toolsMenu.add(sanitizeItem);
        toolsMenu.add(visualizeItem);

        // View menu
        JMenu viewMenu = new JMenu("View");
        viewMenu.setMnemonic(KeyEvent.VK_V);

        JCheckBoxMenuItem darkModeItem = new JCheckBoxMenuItem("Dark Theme");
        darkModeItem.addActionListener(e -> toggleTheme(darkModeItem.isSelected()));

        JCheckBoxMenuItem wordWrapItem = new JCheckBoxMenuItem("Word Wrap");
        wordWrapItem.addActionListener(e -> {
            boolean wrap = wordWrapItem.isSelected();
            objectTextArea.setLineWrap(wrap);
            schemaTextArea.setLineWrap(wrap);
        });

        viewMenu.add(darkModeItem);
        viewMenu.add(wordWrapItem);

        // Help menu
        JMenu helpMenu = new JMenu("Help");
        helpMenu.setMnemonic(KeyEvent.VK_H);

        JMenuItem aboutItem = new JMenuItem("About");
        aboutItem.addActionListener(e -> JOptionPane.showMessageDialog(frame,
                "Inwonerplan Editor\nVersion 1.0.2\n\nJSON Schema validator and editor\nfor Gemeente Utrecht",
                "About", JOptionPane.INFORMATION_MESSAGE));

        helpMenu.add(aboutItem);

        menuBar.add(fileMenu);
        menuBar.add(editMenu);
        menuBar.add(toolsMenu);
        menuBar.add(viewMenu);
        menuBar.add(helpMenu);
        return menuBar;
    }

    // ---- Status Bar ----

    private JPanel createStatusBar() {
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, UIManager.getColor("Separator.foreground")),
                BorderFactory.createEmptyBorder(2, 8, 2, 8)));

        statusFileLabel = new JLabel("Untitled");
        statusCursorLabel = new JLabel("Ln 1, Col 1");
        statusMessageLabel = new JLabel("Ready");

        statusCursorLabel.setHorizontalAlignment(SwingConstants.CENTER);
        statusMessageLabel.setHorizontalAlignment(SwingConstants.RIGHT);

        statusBar.add(statusFileLabel, BorderLayout.WEST);
        statusBar.add(statusCursorLabel, BorderLayout.CENTER);
        statusBar.add(statusMessageLabel, BorderLayout.EAST);
        return statusBar;
    }

    private void updateCursorPosition(CaretEvent e) {
        int pos = objectTextArea.getCaretPosition();
        int line = objectTextArea.getDocument().getDefaultRootElement().getElementIndex(pos);
        int col = pos - objectTextArea.getDocument().getDefaultRootElement().getElement(line).getStartOffset();
        statusCursorLabel.setText("Ln " + (line + 1) + ", Col " + (col + 1));
    }

    private void setStatusMessage(String message) {
        statusMessageLabel.setText(message);
    }

    // ---- File Operations ----

    private void doNew() {
        if (!confirmSaveIfModified()) return;
        objectTextArea.setText("");
        objectTextArea.discardAllEdits();
        currentFile = null;
        modified = false;
        updateTitle();
        setStatusMessage("New file");
    }

    private void doOpen() {
        if (!confirmSaveIfModified()) return;
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("JSON Files", "json"));
        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            try {
                String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                objectTextArea.setText(content);
                objectTextArea.setCaretPosition(0);
                objectTextArea.discardAllEdits();
                currentFile = file;
                modified = false;
                updateTitle();
                statusFileLabel.setText(file.getName());
                setStatusMessage("Opened " + file.getName());
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(frame,
                        "Error reading file: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void doSave() {
        if (currentFile == null) {
            doSaveAs();
        } else {
            saveToFile(currentFile);
        }
    }

    private void doSaveAs() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("JSON Files", "json"));
        if (currentFile != null) {
            chooser.setSelectedFile(currentFile);
        }
        if (chooser.showSaveDialog(frame) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            if (!file.getName().endsWith(".json")) {
                file = new File(file.getAbsolutePath() + ".json");
            }
            saveToFile(file);
        }
    }

    private void saveToFile(File file) {
        try {
            Files.writeString(file.toPath(), objectTextArea.getText(), StandardCharsets.UTF_8);
            currentFile = file;
            modified = false;
            updateTitle();
            statusFileLabel.setText(file.getName());
            setStatusMessage("Saved " + file.getName());
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(frame,
                    "Error saving file: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void doExit() {
        if (!confirmSaveIfModified()) return;
        frame.dispose();
        System.exit(0);
    }

    private boolean confirmSaveIfModified() {
        if (!modified) return true;
        int result = JOptionPane.showConfirmDialog(frame,
                "You have unsaved changes. Save before continuing?",
                "Unsaved Changes", JOptionPane.YES_NO_CANCEL_OPTION);
        if (result == JOptionPane.YES_OPTION) {
            doSave();
            return !modified; // if save was cancelled, modified is still true
        }
        return result != JOptionPane.CANCEL_OPTION;
    }

    private void markModified() {
        if (!modified) {
            modified = true;
            updateTitle();
        }
    }

    private void updateTitle() {
        String name = currentFile != null ? currentFile.getName() : "Untitled";
        String mod = modified ? " *" : "";
        frame.setTitle("Inwonerplan Editor — " + name + mod);
    }

    // ---- Theme Switching ----

    private void toggleTheme(boolean dark) {
        darkTheme = dark;
        try {
            if (dark) {
                FlatDarculaLaf.setup();
            } else {
                FlatIntelliJLaf.setup();
            }
            SwingUtilities.updateComponentTreeUI(frame);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        applySyntaxTheme(objectTextArea);
        applySyntaxTheme(schemaTextArea);
    }

    // ---- Action Methods ----

    private void doFormat() {
        String text = objectTextArea.getText();
        formatButton.setEnabled(false);
        frame.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                return formatJson(text);
            }

            @Override
            protected void done() {
                try {
                    objectTextArea.setText(get());
                    setStatusMessage("Formatted");
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(frame,
                            "Error formatting JSON: " + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                } finally {
                    formatButton.setEnabled(true);
                    frame.setCursor(Cursor.getDefaultCursor());
                }
            }
        }.execute();
    }

    private void doVisualize() {
        String jsonText = objectTextArea.getText();
        if (jsonText == null || jsonText.isEmpty()) return;

        visualizeButton.setEnabled(false);
        frame.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<DefaultMutableTreeNode, Void>() {
            @Override
            protected DefaultMutableTreeNode doInBackground() throws Exception {
                JsonNode rootNode = objectMapper.readTree(jsonText);
                return jsonToTree(rootNode, "Object", 0);
            }

            @Override
            protected void done() {
                try {
                    DefaultMutableTreeNode root = get();
                    jsonTree.setModel(new DefaultTreeModel(root));
                    jsonTree.setVisible(true);
                    expandNodes(jsonTree, MAX_TREE_EXPAND_DEPTH);
                    treeScrollPane.setVisible(true);
                    rightTabPanel.setSelectedIndex(0);
                    setStatusMessage("Tree visualized");
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(frame,
                            "Invalid JSON provided!",
                            "Error", JOptionPane.ERROR_MESSAGE);
                    ex.printStackTrace();
                } finally {
                    visualizeButton.setEnabled(true);
                    frame.setCursor(Cursor.getDefaultCursor());
                }
            }
        }.execute();
    }

    private void doValidate() {
        String jsonText = objectTextArea.getText();
        if (jsonText == null || jsonText.isEmpty()) {
            JOptionPane.showMessageDialog(frame,
                    "Please enter valid JSON text!",
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        validateButton.setEnabled(false);
        frame.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        String schemaText = schemaTextArea.getText();
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                List<String> results = new ArrayList<>();

                JsonValue schemaJson = new JsonParser(schemaText).parse();
                Schema schema = new SchemaLoader(schemaJson).load();
                Validator validator = Validator.create(schema,
                        new ValidatorConfig(FormatValidationPolicy.DEPENDS_ON_VOCABULARY));
                JsonValue instance = new JsonParser(jsonText).parse();
                ValidationFailure failure = validator.validate(instance);

                if (failure == null) {
                    results.add("JSON validated successfully against schema!");
                } else {
                    results.add(failure.toString());
                }

                results.addAll(InwonerplanDomainValidator.validateInwonerPlan(objectMapper, jsonText));

                return results.stream().collect(Collectors.joining("\n"));
            }

            @Override
            protected void done() {
                try {
                    String result = get();
                    rightTextArea.setText(result);
                    rightTabPanel.setSelectedIndex(1);
                    long errorCount = result.lines().count();
                    if (result.contains("validated successfully")) {
                        setStatusMessage("Validation passed");
                    } else {
                        setStatusMessage(errorCount + " validation issue(s)");
                    }
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    JOptionPane.showMessageDialog(frame,
                            "Exception occurred: " + cause.getMessage(),
                            "Validation Error", JOptionPane.ERROR_MESSAGE);
                } finally {
                    validateButton.setEnabled(true);
                    frame.setCursor(Cursor.getDefaultCursor());
                }
            }
        }.execute();
    }

    private void doSanitize() {
        String text = objectTextArea.getText();
        sanitizeButton.setEnabled(false);
        frame.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                return InwonerplanSanitizer.ontdubbelInwonerplan(objectMapper, text);
            }

            @Override
            protected void done() {
                try {
                    objectTextArea.setText(get());
                    setStatusMessage("Sanitized");
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    JOptionPane.showMessageDialog(frame,
                            cause.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                } finally {
                    sanitizeButton.setEnabled(true);
                    frame.setCursor(Cursor.getDefaultCursor());
                }
            }
        }.execute();
    }

    // ---- Resource Reading ----

    private static String readInwonerplanJson() {
        String content = readFileFromResources("inwonerplan.json");
        return content != null ? content : "File not found or could not be loaded.";
    }

    private static String readInwonerplanSchema() {
        String content = readFileFromResources("schemas/inwonerplan.schema.json");
        return content != null ? content : "File not found or could not be loaded.";
    }

    private static String readFileFromResources(String filename) {
        StringBuilder content = new StringBuilder();
        try {
            InputStream inputStream = InwonerplanValidator.class.getClassLoader().getResourceAsStream(filename);
            if (inputStream == null) {
                System.err.println("File not found: " + filename);
                return null;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return content.toString();
    }

    // ---- JSON Formatting ----

    public static String formatJson(String unformattedJson) {
        try {
            Object json = objectMapper.readValue(unformattedJson, Object.class);
            ObjectWriter writer = objectMapper.writerWithDefaultPrettyPrinter();
            return writer.writeValueAsString(json);
        } catch (Exception e) {
            e.printStackTrace();
            return "Invalid JSON provided!";
        }
    }

    // ---- Tree Building ----

    private static DefaultMutableTreeNode jsonToTree(JsonNode node, String key, int depth) {
        if (depth > MAX_TREE_DEPTH) {
            return new DefaultMutableTreeNode(key + ": [depth limit reached]");
        }

        if (node.isObject()) {
            DefaultMutableTreeNode treeNode = new DefaultMutableTreeNode(key);
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                treeNode.add(jsonToTree(field.getValue(), field.getKey(), depth + 1));
            }
            return treeNode;
        } else if (node.isArray()) {
            DefaultMutableTreeNode treeNode = new DefaultMutableTreeNode(key);
            for (int i = 0; i < node.size(); i++) {
                treeNode.add(jsonToTree(node.get(i), "[" + i + "]", depth + 1));
            }
            return treeNode;
        } else {
            return new DefaultMutableTreeNode(key + ": " + node.asText());
        }
    }

    private static void expandNodes(JTree tree, int maxExpandDepth) {
        int row = 0;
        while (row < tree.getRowCount()) {
            TreePath path = tree.getPathForRow(row);
            if (path != null && path.getPathCount() <= maxExpandDepth) {
                tree.expandRow(row);
            }
            row++;
        }
    }
}
