package com.atm.admin;

import javax.swing.*;
import javax.swing.border.AbstractBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.util.List;

public class DashboardFrame extends JFrame {

    private final AdminService service;
    private DefaultTableModel pendingModel;
    private JTable pendingTable;
    private DefaultTableModel activeModel;
    private JTable activeTable;
    private JLabel statusLabel;
    private JLabel countLabel;
    private JLabel activeCountLabel;
    private JButton refreshBtn;
    private JButton approveBtn;
    private JButton rejectBtn;
    private JButton creditBtn;

    private static final Color PRIMARY = new Color(23, 66, 204);
    private static final Color PRIMARY_LIGHT = new Color(59, 130, 246);
    private static final Color BG = new Color(241, 244, 249);
    private static final Color CARD = Color.WHITE;
    private static final Color TEXT = new Color(15, 23, 42);
    private static final Color MUTED = new Color(100, 116, 139);
    private static final Color SUCCESS = new Color(16, 163, 74);
    private static final Color SUCCESS_LIGHT = new Color(34, 197, 94);
    private static final Color DANGER = new Color(220, 38, 38);
    private static final Color DANGER_LIGHT = new Color(239, 68, 68);
    private static final Color WARN = new Color(245, 158, 11);
    private static final Color BORDER = new Color(226, 232, 240);

    public DashboardFrame(AdminService service) {
        this.service = service;
        initUI();
        refreshAll();
    }

    private void initUI() {
        setTitle("UBX - Admin Dashboard");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(980, 720);
        setMinimumSize(new Dimension(820, 580));
        setLocationRelativeTo(null);
        getContentPane().setBackground(BG);

        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(BG);
        root.add(createTopBar(), BorderLayout.NORTH);
        root.add(createContent(), BorderLayout.CENTER);
        setContentPane(root);
    }

    // ---------- Top bar ----------
    private JPanel createTopBar() {
        JPanel bar = new JPanel() {
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
        bar.setPreferredSize(new Dimension(0, 64));
        bar.setLayout(new BorderLayout());

        // Left: brand
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        left.setOpaque(false);
        JLabel badge = new JLabel("UBX");
        badge.setFont(new Font("SansSerif", Font.BOLD, 13));
        badge.setForeground(PRIMARY);
        badge.setOpaque(true);
        badge.setBackground(Color.WHITE);
        badge.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));
        left.add(badge);
        JLabel brand = new JLabel("Admin Console");
        brand.setFont(new Font("SansSerif", Font.BOLD, 17));
        brand.setForeground(Color.WHITE);
        left.add(brand);
        bar.add(left, BorderLayout.WEST);

        // Right: user info + actions
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        right.setOpaque(false);

        JLabel chip = new JLabel("ADMIN");
        chip.setFont(new Font("SansSerif", Font.BOLD, 11));
        chip.setOpaque(true);
        chip.setBackground(new Color(236, 253, 245));
        chip.setForeground(SUCCESS);
        chip.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));
        right.add(chip);

        JLabel userLabel = new JLabel(service.getHolderName() + " \u00b7 " + service.getAccountNumber());
        userLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));
        userLabel.setForeground(new Color(224, 233, 255));
        right.add(userLabel);

        JButton logoutBtn = new JButton("Sign out");
        logoutBtn.setFont(new Font("SansSerif", Font.BOLD, 13));
        logoutBtn.setBackground(Color.WHITE);
        logoutBtn.setForeground(PRIMARY);
        logoutBtn.setOpaque(true);
        logoutBtn.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
        logoutBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        logoutBtn.addActionListener(e -> doLogout());
        right.add(logoutBtn);

        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    // ---------- Content ----------
    private JPanel createContent() {
        JPanel content = new JPanel(new BorderLayout(0, 16));
        content.setBackground(BG);
        content.setBorder(new EmptyBorder(20, 28, 20, 28));

        // Header row
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JPanel headerLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        headerLeft.setOpaque(false);
        JLabel h2 = new JLabel("Admin Dashboard");
        h2.setFont(new Font("SansSerif", Font.BOLD, 18));
        h2.setForeground(TEXT);
        headerLeft.add(h2);
        countLabel = new JLabel("0 pending");
        countLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        countLabel.setOpaque(true);
        countLabel.setBackground(new Color(255, 247, 237));
        countLabel.setForeground(WARN);
        countLabel.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));
        headerLeft.add(countLabel);
        header.add(headerLeft, BorderLayout.WEST);

        refreshBtn = createActionButton("  Refresh", PRIMARY, PRIMARY_LIGHT);
        refreshBtn.addActionListener(e -> refreshAll());
        header.add(refreshBtn, BorderLayout.EAST);
        content.add(header, BorderLayout.NORTH);

        // Tabs: Pending Approvals | Approved Accounts
        JTabbedPane tabs = new JTabbedPane();
        tabs.setBackground(BG);
        tabs.setOpaque(true);
        tabs.setFont(new Font("SansSerif", Font.BOLD, 13));
        tabs.addTab("Pending Approvals", createPendingCard());
        tabs.addTab("Approved Accounts", createActiveCard());
        content.add(tabs, BorderLayout.CENTER);

        // Bottom status bar
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setOpaque(false);
        statusBar.setBorder(new EmptyBorder(0, 4, 0, 4));
        statusLabel = new JLabel("Ready");
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        statusLabel.setForeground(MUTED);
        statusBar.add(statusLabel, BorderLayout.WEST);

        JLabel hint = new JLabel("Approve pending accounts, then credit funds into approved accounts.");
        hint.setFont(new Font("SansSerif", Font.ITALIC, 12));
        hint.setForeground(MUTED);
        statusBar.add(hint, BorderLayout.EAST);
        content.add(statusBar, BorderLayout.SOUTH);

        return content;
    }

    private JPanel createPendingCard() {
        JPanel card = new JPanel(new BorderLayout(0, 12));
        card.setBackground(CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
                new RoundedLineBorder(BORDER, 14, 1),
                new EmptyBorder(18, 20, 18, 20)
        ));

        JLabel info = new JLabel("<html>New registrations are placed in an approval queue. "
                + "<b>Approve</b> an account to activate it. Once approved, the account "
                + "moves to the <b>Approved Accounts</b> tab where funds can be credited.</html>");
        info.setFont(new Font("SansSerif", Font.PLAIN, 12));
        info.setForeground(MUTED);
        card.add(info, BorderLayout.NORTH);

        String[] columns = {"Account", "Name", "Status", "Balance", "Created"};
        pendingModel = new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
        };
        pendingTable = createStyledTable(pendingModel, columns);
        JScrollPane scroll = wrapTable(pendingTable);
        card.add(scroll, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        actions.setOpaque(false);
        actions.setBorder(new EmptyBorder(8, 0, 0, 0));

        approveBtn = createActionButton("Approve", SUCCESS, SUCCESS_LIGHT);
        approveBtn.addActionListener(e -> doApprove());
        actions.add(approveBtn);

        rejectBtn = createActionButton("Reject", DANGER, DANGER_LIGHT);
        rejectBtn.addActionListener(e -> doReject());
        actions.add(rejectBtn);

        card.add(actions, BorderLayout.SOUTH);
        return card;
    }

    private JPanel createActiveCard() {
        JPanel card = new JPanel(new BorderLayout(0, 12));
        card.setBackground(CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
                new RoundedLineBorder(BORDER, 14, 1),
                new EmptyBorder(18, 20, 18, 20)
        ));

        JLabel info = new JLabel("<html>Approved (ACTIVE) customer accounts. Select an account and use "
                + "<b>Credit Funds</b> to record the cash the customer deposited at the branch.</html>");
        info.setFont(new Font("SansSerif", Font.PLAIN, 12));
        info.setForeground(MUTED);
        card.add(info, BorderLayout.NORTH);

        String[] columns = {"Account", "Name", "Status", "Balance", "Created"};
        activeModel = new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
        };
        activeTable = createStyledTable(activeModel, columns);
        JScrollPane scroll = wrapTable(activeTable);
        card.add(scroll, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        actions.setOpaque(false);
        actions.setBorder(new EmptyBorder(8, 0, 0, 0));

        creditBtn = createActionButton("Credit Funds", PRIMARY, PRIMARY_LIGHT);
        creditBtn.addActionListener(e -> doCredit());
        actions.add(creditBtn);

        JLabel counter = new JLabel();
        counter.setFont(new Font("SansSerif", Font.BOLD, 12));
        counter.setOpaque(true);
        counter.setBackground(new Color(236, 253, 245));
        counter.setForeground(SUCCESS);
        counter.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));
        activeCountLabel = counter;
        actions.add(counter);
        setActiveCount(0);

        card.add(actions, BorderLayout.SOUTH);
        return card;
    }

    private JTable createStyledTable(DefaultTableModel model, String[] columns) {
        JTable t = new JTable(model);
        t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        t.setRowHeight(46);
        t.setShowGrid(false);
        t.setIntercellSpacing(new Dimension(0, 0));
        t.setBackground(CARD);
        t.setFont(new Font("SansSerif", Font.PLAIN, 13));
        t.setSelectionBackground(new Color(219, 234, 254));
        t.setSelectionForeground(TEXT);

        JTableHeader tableHeader = t.getTableHeader();
        tableHeader.setBackground(new Color(248, 250, 252));
        tableHeader.setFont(new Font("SansSerif", Font.BOLD, 11));
        tableHeader.setForeground(MUTED);
        tableHeader.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER));
        tableHeader.setPreferredSize(new Dimension(0, 36));

        t.getColumnModel().getColumn(0).setPreferredWidth(120);
        t.getColumnModel().getColumn(1).setPreferredWidth(200);
        t.getColumnModel().getColumn(2).setPreferredWidth(100);
        t.getColumnModel().getColumn(3).setPreferredWidth(140);
        t.getColumnModel().getColumn(4).setPreferredWidth(180);

        DefaultTableCellRenderer center = new DefaultTableCellRenderer();
        center.setHorizontalAlignment(SwingConstants.CENTER);
        center.setVerticalAlignment(SwingConstants.CENTER);
        for (int i = 0; i < columns.length; i++) {
            t.getColumnModel().getColumn(i).setCellRenderer(center);
        }
        return t;
    }

    private JScrollPane wrapTable(JTable t) {
        JScrollPane scroll = new JScrollPane(t);
        scroll.setBorder(BorderFactory.createLineBorder(BORDER, 1));
        scroll.getViewport().setBackground(CARD);
        return scroll;
    }

    private void setActiveCount(int n) {
        activeCountLabel.setText(n + " approved");
    }

    // ---------- Actions ----------
    private void refreshAll() {
        refreshPending();
        refreshActive();
    }

    private void refreshPending() {
        refreshBtn.setEnabled(false);
        refreshBtn.setText("  Loading...");
        setStatus("Fetching pending accounts...");

        new SwingWorker<List<Account>, Void>() {
            @Override protected List<Account> doInBackground() throws Exception { return service.listPending(); }

            @Override
            protected void done() {
                try {
                    List<Account> list = get();
                    pendingModel.setRowCount(0);
                    for (Account a : list) {
                        pendingModel.addRow(toRow(a));
                    }
                    countLabel.setText(list.size() + " pending");
                    setStatus("Loaded " + list.size() + " pending account(s).");
                } catch (Exception ex) {
                    String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                    setStatus("Error: " + msg, DANGER);
                } finally {
                    refreshBtn.setEnabled(true);
                    refreshBtn.setText("  Refresh");
                }
            }
        }.execute();
    }

    private void refreshActive() {
        new SwingWorker<List<Account>, Void>() {
            @Override protected List<Account> doInBackground() throws Exception { return service.listActive(); }

            @Override
            protected void done() {
                try {
                    List<Account> list = get();
                    activeModel.setRowCount(0);
                    for (Account a : list) {
                        activeModel.addRow(toRow(a));
                    }
                    setActiveCount(list.size());
                } catch (Exception ex) {
                    String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                    setStatus("Error: " + msg, DANGER);
                    setActiveCount(0);
                }
            }
        }.execute();
    }

    private Object[] toRow(Account a) {
        return new Object[]{
                a.getAccountNumber(),
                a.getHolderName(),
                a.getStatus(),
                a.getFormattedBalance(),
                a.getCreatedAt() != null ? a.getCreatedAt().replace("T", " ") : "\u2014"
        };
    }

    private void doApprove() {
        int row = pendingTable.getSelectedRow();
        if (row < 0) { showWarning("Select a pending account first."); return; }
        String acct = (String) pendingModel.getValueAt(row, 0);
        String name = (String) pendingModel.getValueAt(row, 1);

        int confirm = JOptionPane.showConfirmDialog(this,
                "Approve account " + acct + " (" + name + ")?\n\nThis will activate the account.",
                "Confirm Approval", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;

        setButtonsEnabled(false);
        setStatus("Approving account " + acct + "...");

        new SwingWorker<Account, Void>() {
            @Override protected Account doInBackground() throws Exception { return service.approve(acct); }
            @Override
            protected void done() {
                try {
                    Account a = get();
                    setStatus("Account " + acct + " approved successfully (" + a.getStatus() + ").", SUCCESS);
                    refreshAll();
                } catch (Exception ex) {
                    String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                    setStatus("Approval failed: " + msg, DANGER);
                } finally { setButtonsEnabled(true); }
            }
        }.execute();
    }

    private void doReject() {
        int row = pendingTable.getSelectedRow();
        if (row < 0) { showWarning("Select a pending account first."); return; }
        String acct = (String) pendingModel.getValueAt(row, 0);
        String name = (String) pendingModel.getValueAt(row, 1);

        int confirm = JOptionPane.showConfirmDialog(this,
                "Reject account " + acct + " (" + name + ")?\n\nThis will permanently reject the registration.",
                "Confirm Rejection", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;

        setButtonsEnabled(false);
        setStatus("Rejecting account " + acct + "...");

        new SwingWorker<Account, Void>() {
            @Override protected Account doInBackground() throws Exception { return service.reject(acct); }
            @Override
            protected void done() {
                try {
                    get();
                    setStatus("Account " + acct + " rejected.", DANGER);
                    refreshAll();
                } catch (Exception ex) {
                    String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                    setStatus("Rejection failed: " + msg, DANGER);
                } finally { setButtonsEnabled(true); }
            }
        }.execute();
    }

    private void doCredit() {
        int row = activeTable.getSelectedRow();
        if (row < 0) { showWarning("Select an approved (ACTIVE) account first."); return; }
        String acct = (String) activeModel.getValueAt(row, 0);
        String name = (String) activeModel.getValueAt(row, 1);

        JPanel panel = new JPanel(new GridLayout(3, 2, 8, 10));
        panel.setBorder(new EmptyBorder(8, 8, 8, 8));
        panel.add(new JLabel("Account:"));
        panel.add(new JLabel(acct + " (" + name + ")"));
        panel.add(new JLabel("Amount (USD):"));
        JTextField amountField = new JTextField();
        panel.add(amountField);
        panel.add(new JLabel("Reference (optional):"));
        JTextField refField = new JTextField();
        refField.setText("Cash deposit at branch");
        panel.add(refField);

        int result = JOptionPane.showConfirmDialog(this, panel,
                "Credit Funds to " + acct, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.YES_OPTION) return;

        double amount;
        try {
            amount = Double.parseDouble(amountField.getText().trim());
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Invalid amount.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (amount <= 0) {
            JOptionPane.showMessageDialog(this, "Amount must be positive.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String ref = refField.getText().trim();
        setButtonsEnabled(false);
        setStatus("Crediting " + String.format("$%,.2f", amount) + " to " + acct + "...");

        new SwingWorker<Account, Void>() {
            @Override protected Account doInBackground() throws Exception { return service.credit(acct, amount, ref); }
            @Override
            protected void done() {
                try {
                    Account a = get();
                    setStatus(String.format("Credited $%,.2f to %s. New balance: %s", amount, acct, a.getFormattedBalance()), SUCCESS);
                    refreshAll();
                } catch (Exception ex) {
                    String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                    setStatus("Credit failed: " + msg, DANGER);
                } finally { setButtonsEnabled(true); }
            }
        }.execute();
    }

    private void doLogout() {
        int confirm = JOptionPane.showConfirmDialog(this,
                "Sign out of the admin dashboard?",
                "Sign Out", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        service.logout();
        dispose();
        SwingUtilities.invokeLater(() -> {
            LoginFrame login = new LoginFrame(service);
            login.setVisible(true);
        });
    }

    // ---------- Helpers ----------

    private void setStatus(String msg) { setStatus(msg, TEXT); }
    private void setStatus(String msg, Color color) {
        statusLabel.setForeground(color);
        statusLabel.setText(msg);
    }

    private void setButtonsEnabled(boolean enabled) {
        approveBtn.setEnabled(enabled);
        rejectBtn.setEnabled(enabled);
        creditBtn.setEnabled(enabled);
        refreshBtn.setEnabled(enabled);
    }

    private void showWarning(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Action Required", JOptionPane.WARNING_MESSAGE);
    }

    private JButton createActionButton(String text, Color c1, Color c2) {
        GradientButton btn = new GradientButton(text, c1, c2);
        btn.setPreferredSize(new Dimension(130, 36));
        btn.setMinimumSize(new Dimension(130, 36));
        btn.setMaximumSize(new Dimension(130, 36));
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
            return new Insets(12, 14, 12, 14);
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
            setFont(new Font("SansSerif", Font.BOLD, 13));
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
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 10, 10));
                g2.dispose();
                super.paintComponent(g);
                return;
            }
            Color start = hover ? darker(c1) : c1;
            Color end = hover ? darker(c2) : c2;
            g2.setPaint(new GradientPaint(0, 0, start, getWidth(), getHeight(), end));
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 10, 10));
            g2.dispose();
            super.paintComponent(g);
        }

        private static Color darker(Color c) {
            return new Color(Math.max(0, c.getRed() - 18),
                    Math.max(0, c.getGreen() - 18),
                    Math.max(0, c.getBlue() - 18));
        }
    }
}