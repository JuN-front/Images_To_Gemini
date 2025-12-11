package com.example;

import com.change_vision.jude.api.inf.ui.IPluginExtraTabView;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class ImagesToGPT extends JPanel implements IPluginExtraTabView {

    private static final int MAX_IMAGES = 5;

    private JTextArea promptTextArea;
    private JTextArea responseTextArea;

    private JButton pasteImageButton;
    private JButton removeLastImageButton;
    private JButton clearImagesButton;
    private JButton sendButton;

    private JLabel imageCountLabel;
    private JPanel imagesContainerPanel;

    private final List<BufferedImage> images = new ArrayList<>();
    private String apiKey;

    public ImagesToGPT() {
        loadApiKey();
        initComponents();
    }

    // ===================== UI SETUP =====================

    private void initComponents() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        //
        // Top: Prompt area
        //
        JPanel topPanel = new JPanel(new BorderLayout(5, 5));
        JPanel promptPanel = new JPanel(new BorderLayout(5, 5));
        promptPanel.add(new JLabel("Prompt:"), BorderLayout.NORTH);

        promptTextArea = new JTextArea(3, 60);
        promptTextArea.setLineWrap(true);
        promptTextArea.setWrapStyleWord(true);
        JScrollPane promptScroll = new JScrollPane(promptTextArea);
        promptPanel.add(promptScroll, BorderLayout.CENTER);

        topPanel.add(promptPanel, BorderLayout.CENTER);

        //
        // Center: images panel
        //
        JPanel centerPanel = new JPanel(new BorderLayout(5, 5));

        JPanel imagesPanel = new JPanel(new BorderLayout(5, 5));
        imagesPanel.add(new JLabel("Pasted images (max " + MAX_IMAGES + "):"), BorderLayout.NORTH);

        imagesContainerPanel = new JPanel();
        imagesContainerPanel.setLayout(new BoxLayout(imagesContainerPanel, BoxLayout.Y_AXIS));
        JScrollPane imagesScroll = new JScrollPane(imagesContainerPanel);
        imagesScroll.setPreferredSize(new Dimension(400, 220));
        imagesPanel.add(imagesScroll, BorderLayout.CENTER);

        JPanel imagesButtonsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        pasteImageButton = new JButton("Paste image from clipboard");
        removeLastImageButton = new JButton("Remove last image");
        clearImagesButton = new JButton("Clear all images");
        imageCountLabel = new JLabel();
        updateImageCountLabel();

        imagesButtonsPanel.add(pasteImageButton);
        imagesButtonsPanel.add(removeLastImageButton);
        imagesButtonsPanel.add(clearImagesButton);
        imagesButtonsPanel.add(Box.createHorizontalStrut(20));
        imagesButtonsPanel.add(imageCountLabel);

        imagesPanel.add(imagesButtonsPanel, BorderLayout.SOUTH);

        centerPanel.add(imagesPanel, BorderLayout.CENTER);

        //
        // Response area
        //
        JPanel responsePanel = new JPanel(new BorderLayout(5, 5));
        responsePanel.add(new JLabel("Response:"), BorderLayout.NORTH);
        responseTextArea = new JTextArea(8, 60);
        responseTextArea.setEditable(false);
        responseTextArea.setLineWrap(true);
        responseTextArea.setWrapStyleWord(true);
        responsePanel.add(new JScrollPane(responseTextArea), BorderLayout.CENTER);

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setTopComponent(centerPanel);
        splitPane.setBottomComponent(responsePanel);
        splitPane.setResizeWeight(0.5);

        //
        // Bottom: send button
        //
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        sendButton = new JButton("Send to Gemini");
        sendButton.setEnabled(false);
        bottomPanel.add(sendButton);

        //
        // Add everything
        //
        add(topPanel, BorderLayout.NORTH);
        add(splitPane, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        //
        // Actions
        //
        pasteImageButton.addActionListener(e -> pasteImageFromClipboard());
        removeLastImageButton.addActionListener(e -> removeLastImage());
        clearImagesButton.addActionListener(e -> clearAllImages());
        sendButton.addActionListener(e -> sendToGPT());

        // Ctrl+V shortcut to paste
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke("control V"), "pasteImage");
        getActionMap().put("pasteImage", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                pasteImageFromClipboard();
            }
        });

        updateSendButtonState();
    }

    // ===================== IMAGE HANDLING =====================

    private void pasteImageFromClipboard() {
        if (images.size() >= MAX_IMAGES) {
            JOptionPane.showMessageDialog(this,
                    "You already have " + MAX_IMAGES + " images. Remove some before pasting more.",
                    "Maximum reached",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        Transferable transferable = clipboard.getContents(null);

        if (transferable == null || !transferable.isDataFlavorSupported(DataFlavor.imageFlavor)) {
            JOptionPane.showMessageDialog(this,
                    "Clipboard does not contain an image.",
                    "No image",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        try {
            Image img = (Image) transferable.getTransferData(DataFlavor.imageFlavor);
            if (img == null) {
                JOptionPane.showMessageDialog(this,
                        "Clipboard image could not be read.",
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            BufferedImage buffered = new BufferedImage(
                    img.getWidth(null),
                    img.getHeight(null),
                    BufferedImage.TYPE_INT_ARGB
            );
            Graphics2D g2d = buffered.createGraphics();
            g2d.drawImage(img, 0, 0, null);
            g2d.dispose();

            images.add(buffered);
            addImageThumbnail(buffered);
            updateImageCountLabel();
            updateSendButtonState();

        } catch (UnsupportedFlavorException | IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "Could not paste image from clipboard: " + ex.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void addImageThumbnail(BufferedImage image) {
        ImageThumbnailPanel thumbnail = new ImageThumbnailPanel(image);
        thumbnail.setAlignmentX(Component.LEFT_ALIGNMENT);
        imagesContainerPanel.add(thumbnail);
        imagesContainerPanel.revalidate();
        imagesContainerPanel.repaint();
    }

    private void removeLastImage() {
        if (images.isEmpty()) {
            return;
        }
        images.remove(images.size() - 1);
        int count = imagesContainerPanel.getComponentCount();
        if (count > 0) {
            imagesContainerPanel.remove(count - 1);
            imagesContainerPanel.revalidate();
            imagesContainerPanel.repaint();
        }
        updateImageCountLabel();
        updateSendButtonState();
    }

    private void clearAllImages() {
        images.clear();
        imagesContainerPanel.removeAll();
        imagesContainerPanel.revalidate();
        imagesContainerPanel.repaint();
        updateImageCountLabel();
        updateSendButtonState();
    }

    private void updateImageCountLabel() {
        if (imageCountLabel != null) {
            imageCountLabel.setText("Images: " + images.size() + " / " + MAX_IMAGES);
        }
    }

    private void updateSendButtonState() {
        if (sendButton != null) {
            sendButton.setEnabled(!images.isEmpty());
        }
    }

    // Thumbnail panel
    private static class ImageThumbnailPanel extends JPanel {
        private final BufferedImage image;

        public ImageThumbnailPanel(BufferedImage image) {
            this.image = image;
            setPreferredSize(new Dimension(400, 150));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 180));
            setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
            setBackground(new Color(245, 245, 245));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (image == null) return;

            int w = getWidth();
            int h = getHeight();
            int imgW = image.getWidth();
            int imgH = image.getHeight();

            double scale = Math.min((w - 10) / (double) imgW, (h - 10) / (double) imgH);
            if (scale <= 0) scale = 1.0;

            int drawW = (int) (imgW * scale);
            int drawH = (int) (imgH * scale);
            int x = (w - drawW) / 2;
            int y = (h - drawH) / 2;

            g.drawImage(image, x, y, drawW, drawH, null);
        }
    }

    // ===================== GEMINI CALL =====================

    private void sendToGPT() {
        if (images.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Please paste at least one image before sending.",
                    "No images",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        if (apiKey == null || apiKey.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "API key is not loaded. Check config.properties.",
                    "API key error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        sendButton.setEnabled(false);
        responseTextArea.setText("Processing...");

        new Thread(() -> {
            try {
                String userPrompt = promptTextArea.getText();

                String fullPrompt =
                "When you draw conceptual diagrams, follow these rules:\n" +
                "- Use single-line arrows only.\n" +
                "- Use formats like: A --include--> B, A --> B, A <-- B.\n" +
                "- Do NOT use stacked ASCII art with lines containing only '^', '/', '|' etc.\n" +
                "- Avoid mermaid, PlantUML, or other diagram languages.\n" +
                "- Prefer readable plain text relationships over complex ASCII diagrams.\n\n"
                + userPrompt;

                List<String> base64Images = new ArrayList<>();
                for (BufferedImage img : images) {
                    base64Images.add(encodeImageToBase64(img));
                }

                // Build Gemini "parts" array: optional text + multiple images
                StringBuilder partsBuilder = new StringBuilder();
                boolean first = true;

                if (fullPrompt != null && !fullPrompt.trim().isEmpty()) {
                    partsBuilder.append("{\"text\": ");
                    partsBuilder.append(escapeJson(fullPrompt));
                    partsBuilder.append("}");
                    first = false;
                }

                for (String base64 : base64Images) {
                    if (!first) {
                        partsBuilder.append(",\n          ");
                    }
                    partsBuilder.append("{\"inlineData\": {\"mimeType\": \"image/png\", \"data\": \"");
                    partsBuilder.append(base64);
                    partsBuilder.append("\"}}");
                    first = false;
                }

                String jsonPayload = "{\n" +
                        "  \"contents\": [\n" +
                        "    {\n" +
                        "      \"parts\": [\n" +
                        "          " + partsBuilder.toString() + "\n" +
                        "      ]\n" +
                        "    }\n" +
                        "  ]\n" +
                        "}";

                String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/"
                        + "gemini-3-pro-preview:generateContent?key="
                        + URLEncoder.encode(apiKey, StandardCharsets.UTF_8.toString());
                String response = postJson(endpoint, jsonPayload, apiKey);

                SwingUtilities.invokeLater(() -> {
                String formatted = extractGeminiText(response);
                formatted = postProcessGeminiText(formatted);   // <<< new line
                responseTextArea.setText(formatted);
                sendButton.setEnabled(true);
                });

            } catch (Exception ex) {
                ex.printStackTrace();
                SwingUtilities.invokeLater(() -> {
                    responseTextArea.setText("Error: " + ex.getMessage());
                    sendButton.setEnabled(true);
                });
            }
        }).start();
    }

    private String encodeImageToBase64(BufferedImage image) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        byte[] bytes = baos.toByteArray();
        return Base64.getEncoder().encodeToString(bytes);
    }

    // Escape Java string as JSON string (returns value with quotes)
    private String escapeJson(String text) {
        if (text == null) return "\"\"";
        return "\"" + text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r") + "\"";
    }

    // Extract candidates[0].content.parts[0].text from Gemini response
    // Better extractor for Gemini: decodes \n, \t, \" and \*uXXXX like*\ \u003c, \u003e
private String extractGeminiText(String response) {
    try {
        int candidatesIdx = response.indexOf("\"candidates\"");
        if (candidatesIdx == -1) return response;

        int textIdx = response.indexOf("\"text\"", candidatesIdx);
        if (textIdx == -1) return response;

        int colonIdx = response.indexOf(":", textIdx);
        if (colonIdx == -1) return response;

        int startQuote = response.indexOf("\"", colonIdx + 1);
        if (startQuote == -1) return response;

        StringBuilder sb = new StringBuilder();
        boolean escaped = false;

        for (int i = startQuote + 1; i < response.length(); i++) {
            char c = response.charAt(i);

            if (!escaped) {
                if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    // end of the JSON string
                    break;
                } else {
                    sb.append(c);
                }
            } else {
                // we are after a backslash: handle escapes
                if (c == 'u' && i + 4 < response.length()) {
                    // Unicode escape: //uXXXX
                    String hex = response.substring(i + 1, i + 5);
                    try {
                        int codePoint = Integer.parseInt(hex, 16);
                        sb.append((char) codePoint);
                        i += 4; // skip the 4 hex digits
                    } catch (NumberFormatException e) {
                        // Fallback: keep the escape as-is if parsing fails
                        sb.append('\\').append('u').append(hex);
                        i += 4;
                    }
                } else {
                    switch (c) {
                        case 'n': sb.append('\n'); break;
                        case 't': sb.append('\t'); break;
                        case 'r': sb.append('\r'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        default:
                            // Unknown escape, keep the character
                            sb.append(c);
                            break;
                    }
                }
                escaped = false;
            }
        }

        String content = sb.toString().trim();
        return content.isEmpty() ? response : content;
    } catch (Exception e) {
        // If anything goes wrong, just show the raw response for debugging
        return response;
    }
}

// Clean up some unwanted tokens from Gemini output
private String postProcessGeminiText(String text) {
    if (text == null) return "";

    String result = text;

    // Remove language label in fenced code blocks: ```mermaid -> ```
    result = result.replace("```mermaid", "```");
    result = result.replace("```text", "```");

    // Remove standalone 'mermaid' lines
    result = result.replaceAll("(?m)^\\s*mermaid\\s*$", "");
    result = result.replaceAll("(?m)^\\s*text\\s*$", "");

    // Remove lines like 'graph TD' or 'graphTD'
    result = result.replaceAll("(?m)^\\s*graph\\s*TD\\s*$", "");
    result = result.replaceAll("\\bgraph\\s*TD\\b", "");
    result = result.replaceAll("\\bgraphTD\\b", "");

    // Remove UC labels like UC1, UC2, UC15, etc.
    result = result.replaceAll("\\bUC\\d+\\b", "");
    
    // (Optional) drop arrow-body lines like "| ^ |", "| / |"
    result = result.replaceAll("(?m)^\\s*\\|\\s*[\\^/\\\\]\\s*\\|\\s*$", "");

    // Collapse multiple spaces that may be left after removals
    result = result.replaceAll(" {2,}", " ");

    // Tidy up empty lines created by removals
    result = result.replaceAll("(?m)^[ \\t]+$", "");
    result = result.trim();

    return result;
}

    private String postJson(String endpoint, String json, String apiKey) throws IOException {
        URL url = new URL(endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        // No Authorization header for Gemini; key is in the URL
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(json.getBytes("UTF-8"));
        }

        int status = conn.getResponseCode();
        InputStream is = (status < 400) ? conn.getInputStream() : conn.getErrorStream();
        BufferedReader in = new BufferedReader(new InputStreamReader(is));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null) {
            response.append(line).append("\n");
        }
        in.close();
        return response.toString();
    }

    // ===================== IPluginExtraTabView =====================

    @Override
    public String getTitle() {
        return "Images to Gemini";
    }

    @Override
    public String getDescription() {
        return "Paste up to five screenshots with a prompt and send them to Gemini.";
    }

    @Override
    public Component getComponent() {
        return this;
    }

    @Override
    public void activated() {
        // Nothing special
    }

    @Override
    public void deactivated() {
        // Nothing special
    }

    @Override
    public void addSelectionListener(com.change_vision.jude.api.inf.ui.ISelectionListener listener) {
        // Not used
    }

    // ===================== API KEY LOADING =====================

    private void loadApiKey() {
        InputStream in = null;
        try {
            // Try context class loader
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if (cl != null) {
                in = cl.getResourceAsStream("config.properties");
            }

            // Try this class's classloader
            if (in == null) {
                in = ImagesToGPT.class.getClassLoader().getResourceAsStream("config.properties");
            }

            // Try absolute resource
            if (in == null) {
                in = ImagesToGPT.class.getResourceAsStream("/config.properties");
            }

            if (in == null) {
                JOptionPane.showMessageDialog(this,
                        "Could not find config.properties on the classpath.\n" +
                        "Make sure it is in the resources folder and included in the plugin JAR.",
                        "API key error",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            java.util.Properties props = new java.util.Properties();
            props.load(in);
            apiKey = props.getProperty("gemini.api.key");

            if (apiKey == null || apiKey.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "Property 'gemini.api.key' not found or empty in config.properties.",
                        "API key error",
                        JOptionPane.ERROR_MESSAGE);
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                    "Could not load API key: " + e.getMessage(),
                    "API key error",
                    JOptionPane.ERROR_MESSAGE);
        } finally {
            if (in != null) {
                try { in.close(); } catch (IOException ignored) {}
            }
        }
    }
}