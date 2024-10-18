package com.ritense;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;


import com.github.erosb.jsonsKema.FormatValidationPolicy;
import com.github.erosb.jsonsKema.JsonParser;
import com.github.erosb.jsonsKema.JsonValue;
import com.github.erosb.jsonsKema.Schema;
import com.github.erosb.jsonsKema.SchemaLoader;
import com.github.erosb.jsonsKema.ValidationFailure;
import com.github.erosb.jsonsKema.Validator;
import com.github.erosb.jsonsKema.ValidatorConfig;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

public class InwonerplanValidator {
    public static void main(String[] args) {
        // Create the main frame
        JFrame frame = new JFrame("Inwonerplan Editor");
        frame.setSize(1400, 1000);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        // Create a JSplitPane to divide the screen
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerLocation(500);  // Split into two halves

        // Create the left panel with a text area
        JPanel leftPanel = new JPanel(new BorderLayout());
        JTextArea leftTextArea = new JTextArea();
        leftTextArea.setEditable(true);
        leftTextArea.setText(readContent());
        JScrollPane textScrollPane = new JScrollPane(leftTextArea);  // Adding scroll capability
        leftPanel.add(textScrollPane, BorderLayout.CENTER);
        splitPane.setLeftComponent(leftPanel);

        // Create a button to format the JSON in the text area
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new FlowLayout());
        JButton formatButton = new JButton("Format");
        JButton validateButton = new JButton("Validate");
        buttonPanel.add(formatButton);
        buttonPanel.add(validateButton);
        leftPanel.add(buttonPanel, BorderLayout.SOUTH);


        // Create the right panel (empty)
        JPanel rightPanel = new JPanel(new BorderLayout());
        JPanel innerRightPanel = new JPanel(new BorderLayout());

        JTree jsonTree = new JTree();
        jsonTree.setVisible(false);
        JScrollPane treeScrollPane = new JScrollPane(jsonTree);
        //rightPanel.add(treeScrollPane, BorderLayout.CENTER);

        JTextArea rightTextArea = new JTextArea();
        rightTextArea.setEditable(false);
        rightTextArea.setText("");
        JScrollPane rightTextScrollPane = new JScrollPane(rightTextArea);  // Adding scroll capability
        rightTextScrollPane.setVisible(true);
        //rightPanel.add(rightTextScrollPane, BorderLayout.CENTER);
        rightPanel.add(innerRightPanel, BorderLayout.CENTER);


        //treeScrollPane.setVisible(false);
        JButton visualizeButton = new JButton("Visualize JSON");
        rightPanel.add(visualizeButton, BorderLayout.SOUTH);

        splitPane.setRightComponent(rightPanel);

        // Action listener to format the text in the text area
        formatButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String unformattedJson = leftTextArea.getText();
                String formattedJson = formatJson(unformattedJson);
                leftTextArea.setText(formattedJson);
            }
        });

        visualizeButton.addActionListener(e -> {
            String jsonText = leftTextArea.getText();
            if (jsonText != null && !jsonText.isEmpty()) {
                try {
                    // Convert the JSON string into a JSONObject
                    JSONObject jsonObject = new JSONObject(new JSONTokener(jsonText));

                    // Convert the JSONObject into a tree structure
                    DefaultMutableTreeNode root = jsonToTree(jsonObject, "Object");

                    // Set the tree model to display
                    jsonTree.setModel(new DefaultTreeModel(root.getRoot()));

                    jsonTree.setVisible(true);
                    // Expand all nodes in the tree
                    expandAllNodes(jsonTree, 0, jsonTree.getRowCount());

                    treeScrollPane.setVisible(true);

                    innerRightPanel.removeAll();
                    innerRightPanel.add(treeScrollPane);
                    innerRightPanel.revalidate();

                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(frame, "Invalid JSON provided!", "Error", JOptionPane.ERROR_MESSAGE);
                    ex.printStackTrace();
                }
            }
        });

        // Action listener for Validate JSON button to validate JSON against schema
        validateButton.addActionListener(e -> {
            String jsonText = leftTextArea.getText();
            if (jsonText != null && !jsonText.isEmpty()) {
                try {

                    JsonValue schemaJson = new JsonParser(readFileFromResources("schemas/inwonerplan.schema.json")).parse();
                    Schema schema = new SchemaLoader(schemaJson).load();

                    Validator validator = Validator.create(schema, new ValidatorConfig(FormatValidationPolicy.DEPENDS_ON_VOCABULARY));
                    JsonValue instance = new JsonParser(jsonText).parse();
                    ValidationFailure failure = validator.validate(instance);
                    if(failure==null) {

                        rightTextArea.setText("JSON validated successfully!");
                        innerRightPanel.removeAll();
                        innerRightPanel.add(rightTextScrollPane);
                        innerRightPanel.revalidate();
                    }
                    else {
                        System.out.println(failure.toString());

                        rightTextArea.setText(failure.toString());
                        innerRightPanel.removeAll();
                        innerRightPanel.add(rightTextScrollPane);
                        innerRightPanel.revalidate();
                    }
                }
                catch (Exception ex) {
                    JOptionPane.showMessageDialog(frame, "Exception occured " + ex.getMessage(), "Validation Error", JOptionPane.ERROR_MESSAGE);

                }
            } else {
                JOptionPane.showMessageDialog(frame, "Please enter valid JSON text!", "Error", JOptionPane.ERROR_MESSAGE);
            }
        });



        // Add the split pane to the frame
        frame.add(splitPane, BorderLayout.CENTER);

        // Set the frame to be visible
        frame.setVisible(true);
    }


    private static String readContent(){
        String content = readFileFromResources("inwonerplan.json");
        if (content != null) {
            return content;
            //return formatJson(content);
        } else {
            return "File not found or could not be loaded.";
        }
    }

    private static String readFileFromResources(String filename) {
        StringBuilder content = new StringBuilder();
        try {
            // Get the file as an InputStream from the resource folder
            InputStream inputStream = InwonerplanValidator.class.getClassLoader().getResourceAsStream(filename);
            if (inputStream == null) {
                System.err.println("File not found: " + filename);
                return null;
            }

            // Read the file content
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

    public static String formatJson(String unformattedJson) {
        try {
            // Create ObjectMapper instance
            ObjectMapper objectMapper = new ObjectMapper();

            // Read the unformatted JSON string into a generic Object (Map/POJO)
            Object json = objectMapper.readValue(unformattedJson, Object.class);

            // Create ObjectWriter for pretty printing
            ObjectWriter writer = objectMapper.writerWithDefaultPrettyPrinter();

            // Write the formatted JSON string
            return writer.writeValueAsString(json);
        } catch (Exception e) {
            e.printStackTrace();
            return "Invalid JSON provided!";
        }
    }

    // Helper function to visualize the JSON object in a tree format
    public static DefaultMutableTreeNode jsonToTree(JSONObject jsonObject, String key) {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode(key);

        // Iterate through the keys of the JSONObject
        for (Iterator<String> it = jsonObject.keys(); it.hasNext(); ) {
            String currentKey = it.next();
            Object value = jsonObject.get(currentKey);

            if (value instanceof JSONObject) {
                // Recursively add JSONObject nodes
                root.add(jsonToTree((JSONObject) value, currentKey));
            } else if (value instanceof JSONArray) {
                // Handle JSONArray separately
                root.add(jsonArrayToTree((JSONArray) value, currentKey));
            } else {
                // Add primitive values (string, numbers, etc.)
                root.add(new DefaultMutableTreeNode(currentKey + ": " + value.toString()));
            }
        }

        return root;
    }

    // Helper function to visualize a JSONArray in the tree
    public static DefaultMutableTreeNode jsonArrayToTree(JSONArray array, String key) {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode(key);

        for (int i = 0; i < array.length(); i++) {
            Object value = array.get(i);

            if (value instanceof JSONObject) {
                // Recursively add JSONObject nodes
                root.add(jsonToTree((JSONObject) value, "[" + i + "]"));
            } else if (value instanceof JSONArray) {
                // Recursively add JSONArray nodes
                root.add(jsonArrayToTree((JSONArray) value, "[" + i + "]"));
            } else {
                // Add primitive values (string, numbers, etc.)
                root.add(new DefaultMutableTreeNode("[" + i + "]: " + value.toString()));
            }
        }

        return root;
    }

    public static void expandAllNodes(JTree tree, int startingIndex, int rowCount) {
        for (int i = startingIndex; i < rowCount; i++) {
            tree.expandRow(i);
        }

        // If new nodes have been added after expanding, ensure they are expanded as well
        if (tree.getRowCount() != rowCount) {
            expandAllNodes(tree, rowCount, tree.getRowCount());
        }
    }
}