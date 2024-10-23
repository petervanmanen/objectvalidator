package com.ritense;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


import com.fasterxml.jackson.databind.SerializationFeature;
import com.github.erosb.jsonsKema.FormatValidationPolicy;
import com.github.erosb.jsonsKema.JsonParser;
import com.github.erosb.jsonsKema.JsonValue;
import com.github.erosb.jsonsKema.Schema;
import com.github.erosb.jsonsKema.SchemaLoader;
import com.github.erosb.jsonsKema.ValidationFailure;
import com.github.erosb.jsonsKema.Validator;
import com.github.erosb.jsonsKema.ValidatorConfig;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTimeZone;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

public class InwonerplanValidator {
    static ObjectMapper objectMapper = new ObjectMapper();

    public static void main(String[] args) {
        objectMapper.findAndRegisterModules();
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
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
        JTabbedPane leftTabPanel = new JTabbedPane();
        leftPanel.add(leftTabPanel, BorderLayout.CENTER);

        final JTextPane objectTextPane = new JTextPane();
        objectTextPane.setText(readInwonerplanJson());
        JScrollPane scrollPane = new JScrollPane(objectTextPane);
        TextLineNumber objectLineNumber = new TextLineNumber(objectTextPane, 3);
        objectLineNumber.setUpdateFont(false);
        float fontSize = objectTextPane.getFont().getSize() - 6;
        Font font = objectTextPane.getFont().deriveFont( fontSize );
        scrollPane.setRowHeaderView( objectLineNumber );
        leftTabPanel.add("Object", scrollPane);

        /*
        JTextArea schemaTextArea = new JTextArea();
        schemaTextArea.setEditable(true);
        schemaTextArea.setText(readInwonerplanSchema());
        JScrollPane schemaTextScrollPane = new JScrollPane(schemaTextArea);  // Adding scroll capability
        leftTabPanel.add("Schema",schemaTextScrollPane);*/
        final JTextPane schemaTextPane = new JTextPane();
        schemaTextPane.setText(readInwonerplanSchema());
        JScrollPane schemaScrollPane = new JScrollPane(schemaTextPane);
        TextLineNumber schemaLineNumber = new TextLineNumber(schemaTextPane, 3);
        schemaLineNumber.setUpdateFont(false);
        fontSize = schemaTextPane.getFont().getSize() - 6;
        font = schemaTextPane.getFont().deriveFont( fontSize );
        schemaScrollPane.setRowHeaderView( schemaLineNumber );
        leftTabPanel.add("Schema", schemaScrollPane);


        splitPane.setLeftComponent(leftPanel);

        // Create a button to format the JSON in the text area
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new FlowLayout());
        JButton formatButton = new JButton("Format");
        JButton validateButton = new JButton("Validate");
        JButton sanitize = new JButton("Sanitize");
        buttonPanel.add(formatButton);
        buttonPanel.add(validateButton);
        buttonPanel.add(sanitize);
        leftPanel.add(buttonPanel, BorderLayout.SOUTH);

        // Create the right panel (empty)
        JPanel rightPanel = new JPanel(new BorderLayout());
        JTabbedPane rightTabPanel = new JTabbedPane();

        JTree jsonTree = new JTree();
        jsonTree.setVisible(false);
        JScrollPane treeScrollPane = new JScrollPane(jsonTree);
        rightTabPanel.add("JSON Tree",treeScrollPane);

        JTextArea rightTextArea = new JTextArea();
        rightTextArea.setEditable(false);
        rightTextArea.setText("");
        JScrollPane rightTextScrollPane = new JScrollPane(rightTextArea);  // Adding scroll capability
        rightTextScrollPane.setVisible(true);
        rightTabPanel.add("Validation",rightTextScrollPane);
        rightPanel.add(rightTabPanel, BorderLayout.CENTER);

        JButton visualizeButton = new JButton("Visualize JSON");
        rightPanel.add(visualizeButton, BorderLayout.SOUTH);

        splitPane.setRightComponent(rightPanel);

        // Action listener to format the text in the text area
        formatButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String unformattedJson = objectTextPane.getText();
                String formattedJson = formatJson(unformattedJson);
                objectTextPane.setText(formattedJson);

                unformattedJson = schemaTextPane.getText();
                formattedJson = formatJson(unformattedJson);
                schemaTextPane.setText(formattedJson);
            }
        });

        visualizeButton.addActionListener(e -> {
            String jsonText = objectTextPane.getText();
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

                    rightTabPanel.setSelectedIndex(0);

                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(frame, "Invalid JSON provided!", "Error", JOptionPane.ERROR_MESSAGE);
                    ex.printStackTrace();
                }
            }
        });

        // Action listener for Validate JSON button to validate JSON against schema
        validateButton.addActionListener(e -> {
            String jsonText = objectTextPane.getText();
            if (jsonText != null && !jsonText.isEmpty()) {
                try {

                    JsonValue schemaJson = new JsonParser(schemaTextPane.getText().toString()).parse();
                    Schema schema = new SchemaLoader(schemaJson).load();

                    Validator validator = Validator.create(schema, new ValidatorConfig(FormatValidationPolicy.DEPENDS_ON_VOCABULARY));
                    JsonValue instance = new JsonParser(jsonText).parse();
                    ValidationFailure failure = validator.validate(instance);
                    if(failure==null) {

                        rightTextArea.setText("JSON validated successfully against schema!");
                        rightTabPanel.setSelectedIndex(1);
                    }
                    else {
                        System.out.println(failure.toString());

                        rightTextArea.setText(failure.toString());
                        rightTabPanel.setSelectedIndex(1);
                    }
                    ArrayList<String> arrayList = new ArrayList();
                    arrayList.add(rightTextArea.getText());
                    arrayList.addAll(validateInwonerPlan(jsonText));

                    rightTextArea.setText(arrayList.stream().collect(Collectors.joining("\n")));


                }
                catch (Exception ex) {
                    JOptionPane.showMessageDialog(frame, "Exception occured " + ex.getMessage(), "Validation Error", JOptionPane.ERROR_MESSAGE);

                }
            } else {
                JOptionPane.showMessageDialog(frame, "Please enter valid JSON text!", "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        sanitize.addActionListener(e -> {

            objectTextPane.setText(ontdubbelInwonerplan(objectTextPane.getText()));

        });

        // Add the split pane to the frame
        frame.add(splitPane, BorderLayout.CENTER);

        // Set the frame to be visible
        frame.setVisible(true);
    }


    private static String readInwonerplanJson(){
        String content = readFileFromResources("inwonerplan.json");
        if (content != null) {
            return content;
        } else {
            return "File not found or could not be loaded.";
        }
    }

    private static String readInwonerplanSchema(){
        String content = readFileFromResources("schemas/inwonerplan.schema.json");
        if (content != null) {
            return content;
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


    private static ArrayList<String> validateInwonerPlan(String inwonerplan){
        ArrayList<String> response = new ArrayList<>();
        InwonerplanSchema inwonerplanObj;
        try {

            inwonerplanObj = objectMapper.readValue(inwonerplan, InwonerplanSchema.class);
        } catch (JsonProcessingException e) {
            response.add(e.getMessage());
            return response;
        }

        //elk inwonerplan moet een zaak hebben
        if(StringUtils.isEmpty(inwonerplanObj.getZaaknummer()))
            response.add("Zaaknummer is empty!");

        if(StringUtils.isEmpty(inwonerplanObj.getInwonerprofielId()))
            response.add("Inwonerprofiel is empty!");

        if(inwonerplanObj.getInwonerplan().getDoelen().size()<=0){
            response.add("Een inwonerplan moet minimaal 1 doel hebben");
        }

        response.add("Validatie ok");
        return response;
    }

    /*
    functie om dubbel aanbod, activiteiten en subdoelen op te schonen
     */
    private static String ontdubbelInwonerplan(String inwonerplan){
        String inwonerplanJson = cleanupJson(inwonerplan);
        InwonerplanSchema inwonerplanObj = null;
        try {
            inwonerplanObj = objectMapper.readValue(inwonerplanJson, InwonerplanSchema.class);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(new JFrame(), e.getMessage(), "Error",
                    JOptionPane.ERROR_MESSAGE);
        }

        for(Doel doel: inwonerplanObj.getInwonerplan().getDoelen()){
            for(Subdoel s: doel.getSubdoelen()){
                ArrayList<Aanbod> newAanbodList = new ArrayList<>();
                for(Aanbod currentAanbod: s.getAanbod()){
                    if(!aanbodListContains(newAanbodList,currentAanbod)){
                        newAanbodList.add(currentAanbod);
                    }
                }
                s.setAanbod(newAanbodList);
                ArrayList<Activiteit> newActiviteitenList = new ArrayList<>();
                for(Activiteit currentActiviteit:s.getActiviteiten()){
                    if(!activiteitListContains(newActiviteitenList,currentActiviteit)){
                        newActiviteitenList.add(currentActiviteit);
                    }
                }
                s.setActiviteiten(newActiviteitenList);
            }
        }

        try {
            return objectMapper.writeValueAsString(inwonerplanObj);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private static boolean aanbodListContains(ArrayList<Aanbod> aanbodList,Aanbod aanbod){
        for(Aanbod aanbod2: aanbodList){
            if(aanbodEquals(aanbod2,aanbod)){
                return true;
            }
        }
        return false;
    }
    private static boolean subdoelEquals(Subdoel subdoel1, Subdoel subdoel2){

        return true;
    }
    private static boolean aanbodEquals(Aanbod a1, Aanbod a2){
        if(a1==null||a2==null){
            return false;
        }
        if(!a1.getCodeAanbod().equalsIgnoreCase(a2.getCodeAanbod())){
            return false;
        }

        //if(ChronoUnit.MINUTES.between(a1.getBegindatum().toGregorianCalendar().toZonedDateTime(), a2.getBegindatum().toGregorianCalendar().toZonedDateTime()) > 1){
        if(ChronoUnit.MINUTES.between(a1.getBegindatum(), a2.getBegindatum()) > 1){
            return false;
        }

        if(StringUtils.compareIgnoreCase(a1.getCodeRedenStatusAanbod(), a2.getCodeRedenStatusAanbod())>0){
            return false;
        }

        if(StringUtils.compareIgnoreCase(a1.getCodeResultaatAanbod(), a2.getCodeResultaatAanbod())>0){
            return false;
        }
        return true;
    }

    private static boolean activiteitListContains(ArrayList<Activiteit> activiteitList,Activiteit activiteit){
        for(Activiteit activiteit2: activiteitList){
            if(activiteitEquals(activiteit2,activiteit)){
                return true;
            }
        }
        return false;
    }
    private static boolean activiteitEquals(Activiteit a1, Activiteit a2){
        if(a1==null||a2==null){
            return false;
        }
        if( a1.getUuid().equalsIgnoreCase(a2.getUuid())) {
            return true;
        }
        if(a1.getCodeAanbod().equalsIgnoreCase(a2.getCodeAanbod()) && a1.getCodeAanbodactiviteit().equalsIgnoreCase(a2.getCodeAanbodactiviteit())) {
            return true;
        }
        return false;
    }

    private static String cleanupJson(String inwonerplan) {

        String inwonerplanJson = inwonerplan.replaceAll("(None)","null");
        inwonerplanJson = inwonerplanJson.replaceAll("True","true");


        inwonerplanJson = trimDateTimeNanoseconds(inwonerplanJson);
        inwonerplanJson = inwonerplanJson.replaceAll("\\[GMT\\]","");
        //er staan data geregistreerd op exact 00:00:00.000 ; dat is geen geldig format dus omzetten naar 1 seconden later
        inwonerplanJson = inwonerplanJson.replaceAll("00:00:00.00000Z","00:00:01.001Z");
        inwonerplanJson = inwonerplanJson.replaceAll("\\+\\d\\d:?\\d\\d","Z");

        return inwonerplanJson;
    }

    public static String trimDateTimeNanoseconds(String input) {
        // Regex pattern to match the datetime string with the nanoseconds part
        String regex = "(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\d*(Z?)";
        // Compile the pattern
        Pattern pattern = Pattern.compile(regex);
        // Create a matcher to find occurrences of the pattern
        Matcher matcher = pattern.matcher(input);

        // Replace all occurrences, keeping only the first three digits of the nanoseconds and appending 'Z'
        return matcher.replaceAll("$1Z");
    }

}