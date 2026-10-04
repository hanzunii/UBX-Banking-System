package com.atm.admin;

import javax.swing.*;
import javax.swing.border.AbstractBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

public class LoginFrame extends JFrame {

    private final AdminService service;
    private JTextField accountField;
    private JPasswordField pinField;
    private JTextField otpField;
    private JLabel statusLabel;
    private JLabel otpHintLabel;
    private JButton loginBtn;
    private JButton verifyBtn;
    private JPanel loginPanel;
    private JPanel mfaPanel;
    private String pendingMfaToken;

    private static final Color PRIMARY = new Color(23, 66, 204);
    private static final Color PRIMARY_LIGHT = new Color(59, 130, 246);
    private static final Color BG = new Color(241, 244, 249);
    private static final Color CARD = Color.WHITE;
    private static final Color TEXT = new Color(15, 23, 42);
    private static final Color MUTED = new Color(100, 116, 139);
    private static final Color SUCCESS = new Color(16, 163, 74);
    private static final Color DANGER = new Color(220, 38, 38);
    private static final Color BORDER = new Color(226, 232, 240);
    private static final Color FIELD_BG = new Color(248, 250, 252);

    public LoginFrame(AdminService service) {
        this.service = service;
        initUI();
    }

    private void initUI() {
        setTitle("UBX - Admin Desktop");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(500, 700);
        setResizable(false);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());
        getContentPane().setBackground(BG);

        add(createHeader(), BorderLayout.NORTH);
        add(createBody(), BorderLayout.CENTER);
    }

    private JPanel createHeader() {
        JPanel header = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setPaint(new GradientPaint(0, 0, PRIMARY, getWidth(), getHeight(), PRIMARY_LIGHT));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
        header.setPreferredSize(new Dimension(0, 130));
        header.setLayout(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0;
        g.gridy = 0;
        // small badge
        JLabel badge = new JLabel("UBX");
        badge.setFont(new Font("SansSerif", Font.BOLD, 16));
        badge.setForeground(PRIMARY);
        badge.setOpaque(true);
        badge.setBackground(Color.WHITE);
        badge.setBorder(BorderFactory.createEmptyBorder(6, 18, 6, 18));
        header.add(badge, g);
        g.gridy = 1;
        JLabel title = new JLabel("Admin Desktop");
        title.setFont(new Font("SansSerif", Font.BOLD, 26));
        title.setForeground(Color.WHITE);
        header.add(title, g);
        g.gridy = 2;
        JLabel caption = new JLabel("Bank operations console");
        caption.setFont(new Font("SansSerif", Font.PLAIN, 12));
        caption.setForeground(new Color(214, 225, 255));
        header.add(caption, g);
        return header;
    }

    private JPanel createBody() {
        JPanel body = new JPanel(new GridBagLayout());
        body.setBackground(BG);
        body.setBorder(new EmptyBorder(22, 34, 30, 34));

        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
                new RoundedLineBorder(BORDER, 16, 1),
                new EmptyBorder(28, 30, 24, 30)
        ));
        card.setMaximumSize(new Dimension(430, 2000));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        // ----- Login panel -----
        loginPanel = new JPanel();
        loginPanel.setLayout(new BoxLayout(loginPanel, BoxLayout.Y_AXIS));
        loginPanel.setOpaque(false);
        loginPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel heading = new JLabel("Sign in to manage accounts");
        heading.setFont(new Font("SansSerif", Font.BOLD, 18));
        heading.setForeground(TEXT);
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        loginPanel.add(heading);
        loginPanel.add(Box.createVerticalStrut(6));

        JLabel sub = new JLabel("Enter your administrator credentials below.");
        sub.setFont(new Font("SansSerif", Font.PLAIN, 13));
        sub.setForeground(MUTED);
        sub.setAlignmentX(Component.LEFT_ALIGNMENT);
        loginPanel.add(sub);
        loginPanel.add(Box.createVerticalStrut(20));

        loginPanel.add(createLabel("ACCOUNT NUMBER"));
        loginPanel.add(Box.createVerticalStrut(6));
        accountField = new JTextField();
        styleField(accountField);
        loginPanel.add(accountField);
        loginPanel.add(Box.createVerticalStrut(4));
        loginPanel.add(createHelpLabel("8-digit account number, e.g. 10000003"));
        loginPanel.add(Box.createVerticalStrut(16));

        loginPanel.add(createLabel("PIN"));
        loginPanel.add(Box.createVerticalStrut(6));
        pinField = new JPasswordField();
        styleField(pinField);
        pinField.setEchoChar('\u2022');
        loginPanel.add(pinField);
        loginPanel.add(Box.createVerticalStrut(4));
        loginPanel.add(createHelpLabel("4-digit PIN"));
        loginPanel.add(Box.createVerticalStrut(22));

        loginBtn = createGradientButton("Sign In", PRIMARY, PRIMARY_LIGHT);
        loginBtn.addActionListener(e -> doLogin());
        loginPanel.add(loginBtn);
        loginPanel.add(Box.createVerticalStrut(18));

        JLabel demoBox = new JLabel();
        demoBox.setOpaque(true);
        demoBox.setBackground(new Color(239, 246, 255));
        demoBox.setBorder(BorderFactory.createCompoundBorder(
                new RoundedLineBorder(new Color(191, 219, 254), 10, 1),
                new EmptyBorder(10, 14, 10, 14)
        ));
        demoBox.setText("<html><b style=\"color:#1D4ED8\">Demo admin account</b><br>" +
                "Account: <b>10000003</b> &nbsp;&nbsp;\u00b7&nbsp;&nbsp; PIN: <b>1122</b></html>");
        demoBox.setFont(new Font("SansSerif", Font.PLAIN, 12));
        demoBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        demoBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 62));
        loginPanel.add(demoBox);
        loginPanel.add(Box.createVerticalStrut(12));

        statusLabel = new JLabel(" ");
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        loginPanel.add(statusLabel);

        card.add(loginPanel);

        // ----- MFA panel (hidden) -----
        mfaPanel = new JPanel();
        mfaPanel.setLayout(new BoxLayout(mfaPanel, BoxLayout.Y_AXIS));
        mfaPanel.setOpaque(false);
        mfaPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        mfaPanel.setVisible(false);

        JLabel mfaTitle = new JLabel("Two-Factor Verification");
        mfaTitle.setFont(new Font("SansSerif", Font.BOLD, 18));
        mfaTitle.setForeground(TEXT);
        mfaTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        mfaPanel.add(mfaTitle);
        mfaPanel.add(Box.createVerticalStrut(6));

        otpHintLabel = new JLabel("Enter the 6-digit OTP code.");
        otpHintLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));
        otpHintLabel.setForeground(MUTED);
        otpHintLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        mfaPanel.add(otpHintLabel);
        mfaPanel.add(Box.createVerticalStrut(20));

        mfaPanel.add(createLabel("OTP CODE"));
        mfaPanel.add(Box.createVerticalStrut(6));
        otpField = new JTextField();
        styleField(otpField);
        mfaPanel.add(otpField);
        mfaPanel.add(Box.createVerticalStrut(4));
        JLabel otpHelp = createHelpLabel("6-digit verification code");
        mfaPanel.add(otpHelp);
        mfaPanel.add(Box.createVerticalStrut(20));

        verifyBtn = createGradientButton("Verify & Sign In", SUCCESS, new Color(34, 197, 94));
        verifyBtn.addActionListener(e -> doVerify());
        mfaPanel.add(verifyBtn);

        card.add(mfaPanel);

        body.add(card);
        return body;
    }

    private void doLogin() {
        String acct = accountField.getText().trim();
        String pin = new String(pinField.getPassword()).trim();
        if (acct.isEmpty() || pin.isEmpty()) {
            setStatus("Please fill in all fields", DANGER);
            return;
        }

        loginBtn.setEnabled(false);
        loginBtn.setText("Signing in...");
        setStatus("Connecting to server...", MUTED);

        new SwingWorker<AdminService.LoginStep1Result, Void>() {
            @Override
            protected AdminService.LoginStep1Result doInBackground() throws Exception {
                return service.login(acct, pin);
            }

            @Override
            protected void done() {
                try {
                    AdminService.LoginStep1Result result = get();
                    pendingMfaToken = result.mfaToken;
                    loginPanel.setVisible(false);
                    mfaPanel.setVisible(true);
                    otpHintLabel.setText("OTP sent! Demo OTP: " + result.demoOtp);
                    setStatus("Enter the OTP to complete sign in.", SUCCESS);
                    otpField.requestFocusInWindow();
                    getRootPane().setDefaultButton(verifyBtn);
                } catch (Exception ex) {
                    String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                    setStatus("Login failed: " + msg, DANGER);
                    loginBtn.setEnabled(true);
                    loginBtn.setText("Sign In");
                }
            }
        }.execute();
    }

    private void doVerify() {
        String otp = otpField.getText().trim();
        if (otp.isEmpty()) {
            setStatus("Enter the OTP code", DANGER);
            return;
        }
        verifyBtn.setEnabled(false);
        verifyBtn.setText("Verifying...");

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                service.verifyMfa(pendingMfaToken, otp);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    dispose();
                    SwingUtilities.invokeLater(() -> {
                        DashboardFrame dashboard = new DashboardFrame(service);
                        dashboard.setVisible(true);
                    });
                } catch (Exception ex) {
                    String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                    setStatus("Verification failed: " + msg, DANGER);
                    verifyBtn.setEnabled(true);
                    verifyBtn.setText("Verify & Sign In");
                }
            }
        }.execute();
    }

    private void setStatus(String msg, Color color) {
        statusLabel.setText(msg);
        statusLabel.setForeground(color);
    }

    // ---------- Style helpers ----------

    private void styleField(JComponent field) {
        field.setBackground(FIELD_BG);
        field.setOpaque(true);
        field.setBorder(new RoundedLineBorder(BORDER, 10, 1));
        field.setFont(new Font("SansSerif", Font.PLAIN, 15));
        if (field instanceof JTextComponent) {
            ((JTextComponent) field).setCaretColor(TEXT);
        }
        field.setForeground(TEXT);
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        field.setPreferredSize(new Dimension(0, 42));
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
    }

    private JLabel createLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("SansSerif", Font.BOLD, 12));
        label.setForeground(TEXT);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private JLabel createHelpLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("SansSerif", Font.PLAIN, 12));
        label.setForeground(MUTED);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private JButton createGradientButton(String text, Color c1, Color c2) {
        GradientButton btn = new GradientButton(text, c1, c2);
        btn.setAlignmentX(Component.LEFT_ALIGNMENT);
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        btn.setPreferredSize(new Dimension(0, 46));
        return btn;
    }

    // ---------- Custom components ----------

    private static class RoundedLineBorder extends AbstractBorder {
        private final Color color;
        private final int radius;
        private final int thickness;

        RoundedLineBorder(Color color, int radius, int thickness) {
            this.color = color;
            this.radius = radius;
            this.thickness = thickness;
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(10, 12, 10, 12);
        }

        @Override
        public boolean isBorderOpaque() {
            return false;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.setStroke(new BasicStroke(thickness));
            g2.draw(new RoundRectangle2D.Float(x + thickness / 2f, y + thickness / 2f,
                    w - thickness, h - thickness, radius, radius));
            g2.dispose();
        }
    }

    private static class GradientButton extends JButton {
        private final Color c1;
        private final Color c2;
        private boolean hover;

        GradientButton(String text, Color c1, Color c2) {
            super(text);
            this.c1 = c1;
            this.c2 = c2;
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setForeground(Color.WHITE);
            setFont(new Font("SansSerif", Font.BOLD, 15));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new java.awt.event.MouseAdapter() {
                @Override public void mouseEntered(java.awt.event.MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(java.awt.event.MouseEvent e) { hover = false; repaint(); }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (!isEnabled()) {
                g2.setColor(new Color(150, 163, 184));
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 12, 12));
                g2.dispose();
                super.paintComponent(g);
                return;
            }
            Color start = hover ? darker(c1) : c1;
            Color end = hover ? darker(c2) : c2;
            g2.setPaint(new GradientPaint(0, 0, start, getWidth(), getHeight(), end));
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 12, 12));
            g2.dispose();
            super.paintComponent(g);
        }

        private static Color darker(Color c) {
            return new Color(Math.max(0, c.getRed() - 18),
                    Math.max(0, c.getGreen() - 18),
                    Math.max(0, c.getBlue() - 18));
        }

        @Override
        public void setEnabled(boolean enabled) {
            super.setEnabled(enabled);
            repaint();
        }
    }
}