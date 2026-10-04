package com.atm.admin;

import javax.swing.*;
import java.awt.*;

public class AdminApp {

    private static final String DEFAULT_SERVER = "http://localhost:8080";

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        // Set global font
        Font defaultFont = new Font("SansSerif", Font.PLAIN, 13);
        UIManager.put("Button.font", new Font("SansSerif", Font.BOLD, 13));
        UIManager.put("Label.font", defaultFont);
        UIManager.put("TextField.font", defaultFont);
        UIManager.put("Table.font", defaultFont);
        UIManager.put("OptionPane.messageFont", defaultFont);
        UIManager.put("OptionPane.buttonFont", new Font("SansSerif", Font.BOLD, 13));

        // Prompt for server URL
        String serverUrl = System.getProperty("atm.server", DEFAULT_SERVER);

        if (args.length > 0) {
            serverUrl = args[0];
        }

        AdminService service = new AdminService(serverUrl);

        // Check server health first
        final String url = serverUrl;
        SwingUtilities.invokeLater(() -> {
            // Show splash briefly
            JFrame splash = new JFrame();
            splash.setUndecorated(true);
            splash.setSize(320, 180);
            splash.setLocationRelativeTo(null);
            JPanel splashPanel = new JPanel(new GridBagLayout()) {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setPaint(new GradientPaint(0, 0, new Color(23, 66, 204), getWidth(), getHeight(), new Color(59, 130, 246)));
                    g2.fillRect(0, 0, getWidth(), getHeight());
                    g2.dispose();
                }
            };
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridx = 0; gbc.gridy = 0;
            JLabel logo = new JLabel("UBX");
            logo.setFont(new Font("SansSerif", Font.BOLD, 36));
            logo.setForeground(Color.WHITE);
            splashPanel.add(logo, gbc);
            gbc.gridy = 1;
            JLabel sub = new JLabel("Admin Desktop");
            sub.setFont(new Font("SansSerif", Font.PLAIN, 14));
            sub.setForeground(new Color(214, 225, 255));
            splashPanel.add(sub, gbc);
            gbc.gridy = 2;
            gbc.insets = new java.awt.Insets(10, 0, 0, 0);
            JLabel connecting = new JLabel("Connecting to " + url + "...");
            connecting.setFont(new Font("SansSerif", Font.PLAIN, 11));
            connecting.setForeground(new Color(180, 200, 240));
            splashPanel.add(connecting, gbc);
            splash.setContentPane(splashPanel);
            splash.setVisible(true);

            new SwingWorker<Boolean, Void>() {
                @Override protected Boolean doInBackground() { return service.checkHealth(); }

                @Override
                protected void done() {
                    splash.dispose();
                    try {
                        boolean ok = get();
                        if (!ok) {
                            JOptionPane.showMessageDialog(null,
                                    "Cannot connect to server at " + url + "\n\n"
                                    + "Make sure the API Gateway is running (port 8080).\n"
                                    + "You can start it with: cd api-gateway && ./gradlew bootRun",
                                    "Connection Error", JOptionPane.ERROR_MESSAGE);
                        }
                    } catch (Exception e) {
                        // ignore
                    }
                    LoginFrame login = new LoginFrame(service);
                    login.setVisible(true);
                }
            }.execute();
        });
    }
}
