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
        // Center: images + response (split pane)
        //
        JPanel centerPanel = new JPanel(new BorderLayout(5, 5));

        // Images area
        JPanel imagesPanel = new JPanel(new BorderLayout(5, 5));
        imagesPanel.add(new JLabel("Pasted images (max " + MAX_IMAGES + "):"), BorderLayout.NORTH);

        imagesContainerPanel = new JPanel();
        imagesContainerPanel.setLayout(new BoxLayout(imagesContainerPanel, BoxLayout.Y_AXIS));
        JScrollPane imagesScroll = new JScrollPane(imagesContainerPanel);
        imagesScroll.setPreferredSize(new Dimension(400, 220));
        imagesPanel.add(imagesScroll, BorderLayout.CENTER);

        // Buttons + counter under images
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

        // Response area
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
        sendButton = new JButton("Send to GPT");
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

        // Ctrl+V as a shortcut to paste image
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

    // === Image handling =====================================================

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

    // Panel to show a single image thumbnail
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
            if (image == null) {
                return;
            }
            int w = getWidth();
            int h = getHeight();
            int imgW = image.getWidth();
            int imgH = image.getHeight();

            double scale = Math.min((w - 10) / (double) imgW, (h - 10) / (double) imgH);
            if (scale <= 0) {
                scale = 1.0;
            }

            int drawW = (int) (imgW * scale);
            int drawH = (int) (imgH * scale);
            int x = (w - drawW) / 2;
            int y = (h - drawH) / 2;

            g.drawImage(image, x, y, drawW, drawH, null);
        }
    }

    // === GPT call ===========================================================

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
                String prompt = promptTextArea.getText();
                List<String> base64Images = new ArrayList<>();
                for (BufferedImage img : images) {
                    base64Images.add(encodeImageToBase64(img));
                }

                // Build content array: text + multiple images
                StringBuilder contentBuilder = new StringBuilder();
                contentBuilder.append("{\"type\": \"text\", \"text\": ");
                contentBuilder.append(escapeJson(prompt));
                contentBuilder.append("}");

                for (String base64 : base64Images) {
                    contentBuilder.append(",\n      ");
                    contentBuilder.append("{\"type\": \"image_url\", \"image_url\": {\"url\": \"data:image/png;base64,");
                    contentBuilder.append(base64);
                    contentBuilder.append("\"}}");
                }

                // JSON payload for Chat Completions with vision
                String jsonPayload = "{\n" +
                        "  \"model\": \"gpt-5-mini\",\n" +  // change model if you prefer
                        "  \"messages\": [\n" +
                        "    {\"role\": \"user\", \"content\": [\n" +
                        "      " + contentBuilder.toString() + "\n" +
                        "    ]}\n" +
                        "  ],\n" +
                        "  \"max_tokens\": 2048\n" +
                        "}";

                String endpoint = "https://api.openai.com/v1/chat/completions";
                String response = postJson(endpoint, jsonPayload, apiKey);

                SwingUtilities.invokeLater(() -> {
                    String formatted = extractRelevantContent(response);
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

    // === JSON helpers & HTTP ===============================================

    // Escape text as a JSON string (returns something already wrapped in quotes)
    private String escapeJson(String text) {
        if (text == null) return "\"\"";
        return "\"" + text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r") + "\"";
    }

    // Very simple extractor for choices[0].message.content
    private String extractRelevantContent(String response) {
        try {
            int choicesIdx = response.indexOf("\"choices\"");
            if (choicesIdx == -1) return response;
            int contentIdx = response.indexOf("\"content\"", choicesIdx);
            if (contentIdx == -1) return response;
            int colonIdx = response.indexOf(":", contentIdx);
            if (colonIdx == -1) return response;
            int startQuote = response.indexOf("\"", colonIdx + 1);
            if (startQuote == -1) return response;

            StringBuilder sb = new StringBuilder();
            for (int i = startQuote + 1; i < response.length(); i++) {
                char c = response.charAt(i);
                if (c == '"') break;
                if (c == '\\' && i + 1 < response.length()) {
                    char next = response.charAt(i + 1);
                    if (next == 'n') {
                        sb.append('\n');
                        i++;
                    } else if (next == 't') {
                        sb.append('\t');
                        i++;
                    } else if (next == '"') {
                        sb.append('"');
                        i++;
                    } else if (next == '\\') {
                        sb.append('\\');
                        i++;
                    } else {
                        sb.append(c);
                    }
                } else {
                    sb.append(c);
                }
            }
            String content = sb.toString().trim();
            return content.isEmpty() ? response : content;
        } catch (Exception e) {
            return response;
        }
    }

    private String postJson(String endpoint, String json, String apiKey) throws IOException {
        URL url = new URL(endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
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

    // === IPluginExtraTabView ===============================================

    @Override
    public String getTitle() {
        return "Images To GPT";
    }

    @Override
    public String getDescription() {
        return "Enter a prompt and paste up to five screenshots to send them to GPT with an API key.";
    }

    @Override
    public Component getComponent() {
        return this;
    }

    @Override
    public void activated() {
        // Nothing to do on activation in this version
    }

    @Override
    public void deactivated() {
        // Nothing special on deactivation
    }

    @Override
    public void addSelectionListener(com.change_vision.jude.api.inf.ui.ISelectionListener listener) {
        // Not used in this tab
    }

    // === API key loading ====================================================

    private void loadApiKey() {
        try (InputStream in = getClass().getResourceAsStream("/config.properties")) {
            if (in == null) {
                JOptionPane.showMessageDialog(this,
                        "Could not find config.properties for API key.",
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }
            java.util.Properties props = new java.util.Properties();
            props.load(in);
            apiKey = props.getProperty("openai.api.key");
            if (apiKey == null || apiKey.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "API key not found in config.properties.",
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                    "Could not load API key: " + e.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }
}
