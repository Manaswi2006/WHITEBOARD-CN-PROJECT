package client;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.*;
import java.net.Socket;
import java.util.HashSet;
import java.util.Set;

/**
 * Client app:
 * - Connects to server
 * - Shows modern GUI with whiteboard + chat + tools + polls
 * - Sends DRAW / CHAT / CLEAR / CURSOR / BOARD_LOCK / POLL_* to server
 * - Receives messages and updates UI in real time
 */
public class WhiteboardClient {

    private JFrame frame;
    private DrawPanel drawPanel;
    private JTextArea chatArea;
    private JTextField chatInput;

    private DefaultListModel<String> participantsModel;
    private JLabel boardStatusLabel;
    private JLabel userLabel;

    private JPanel teacherControlsPanel;
    private JToggleButton lockToggle;
    private JButton createPollButton;
    private JButton clearBoardButton;

    private PollPanel pollPanel;

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    private String username;

    private boolean isTeacher = false;
    private boolean boardLocked = false;
    private boolean suppressLockToggleEvent = false;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(
                        UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}
            new WhiteboardClient().start();
        });
    }

    public static void AsHost(int serverPort) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(
                        UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}
            new WhiteboardClient().startAsHost(serverPort);
        });
    }

    public void start() {
        // ---- connection dialog ----
        JTextField ipField = new JTextField("127.0.0.1");
        JTextField portField = new JTextField("5001");
        JTextField userField = new JTextField("Student");


        JPanel connectPanel = new JPanel(new GridLayout(0, 1, 6, 6));
        connectPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        connectPanel.add(new JLabel("Server IP:"));
        connectPanel.add(ipField);
        connectPanel.add(new JLabel("Server PORT:"));
        connectPanel.add(portField);
        connectPanel.add(new JLabel("Username:"));
        connectPanel.add(userField);

        int result = JOptionPane.showConfirmDialog(
                null, connectPanel, "Connect to Whiteboard Server",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result != JOptionPane.OK_OPTION) {
            System.exit(0);
        }

        String serverIp = ipField.getText().trim();
        String serverPort = portField.getText().trim();
        username = userField.getText().trim();
        if (username.isEmpty()) {
            username = "Student";
        }

        // ---- connect to server ----
        try {
            socket = new Socket(serverIp, Integer.parseInt(serverPort));
            in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(
                    new OutputStreamWriter(socket.getOutputStream()), true);

            // send JOIN message
            out.println("JOIN|" + username);

            // build UI
            initUI();

            // start background reader
            new Thread(this::listenToServer).start();

        } catch (IOException e) {
            JOptionPane.showMessageDialog(null,
                    "Could not connect to server: " + e.getMessage(),
                    "Connection Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }

    private void startAsHost(int port) {
        // ---- connection dialog ----

        JTextField userField = new JTextField("Teacher");

        JPanel connectPanel = new JPanel(new GridLayout(0, 1, 6, 6));
        connectPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        connectPanel.add(new JLabel("Username:"));
        connectPanel.add(userField);

        int result = JOptionPane.showConfirmDialog(
                null, connectPanel, "Join Whiteboard",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result != JOptionPane.OK_OPTION) {
            System.exit(0);
        }

        String serverIp = "127.0.0.1";
        username = userField.getText().trim();
        if (username.isEmpty()) {
            username = "Teacher";
        }

        // ---- connect to server ----
        try {
            socket = new Socket(serverIp, port);
            in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(
                    new OutputStreamWriter(socket.getOutputStream()), true);

            // send JOIN message
            out.println("JOIN|" + username);

            // build UI
            initUI();

            // start background reader
            new Thread(this::listenToServer).start();

        } catch (IOException e) {
            JOptionPane.showMessageDialog(null,
                    "Could not connect to server: " + e.getMessage(),
                    "Connection Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }


    // ---------------------------------------------------------
    // UI
    // ---------------------------------------------------------

    private void initUI() {
        frame = new JFrame("CollabBoard – Interactive Classroom | " + username);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1550, 930);
        frame.setMinimumSize(new Dimension(1340, 820));
        frame.setLocationRelativeTo(null);
        frame.getContentPane().setLayout(new BorderLayout());

        Color bgDark = new Color(12, 16, 24);             // minimal charcoal
        Color bgHeaderLeft = new Color(9, 11, 16);
        Color bgHeaderRight = new Color(29, 41, 57);
        Color accent = new Color(94, 234, 212);           // mint glow
        Color accentBright = new Color(248, 250, 252);
        Color textLight = new Color(229, 236, 246);
        Color boardSurface = new Color(249, 250, 252);

        // ---------- HEADER ----------
        JPanel header = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_RENDERING,
                        RenderingHints.VALUE_RENDER_QUALITY);
                GradientPaint gp = new GradientPaint(
                        0, 0, bgHeaderLeft,
                        getWidth(), getHeight(), bgHeaderRight);
                g2.setPaint(gp);
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        header.setLayout(new BorderLayout());
        header.setBorder(new EmptyBorder(10, 18, 10, 18));

        JLabel title = new JLabel("CollabBoard");
        title.setForeground(textLight);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));

        JLabel subtitle = new JLabel("Live collaborative whiteboard & class chat");
        subtitle.setForeground(new Color(191, 219, 254));
        subtitle.setFont(subtitle.getFont().deriveFont(13f));

        JPanel titleBox = new JPanel();
        titleBox.setOpaque(false);
        titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));
        titleBox.add(title);
        titleBox.add(Box.createVerticalStrut(2));
        titleBox.add(subtitle);

        userLabel = new JLabel(" Connected as " + username + "  ");
        userLabel.setForeground(textLight);
        userLabel.setFont(userLabel.getFont().deriveFont(Font.PLAIN, 13f));
        userLabel.setIcon(UIManager.getIcon("OptionPane.informationIcon"));

        header.add(titleBox, BorderLayout.WEST);
        header.add(userLabel, BorderLayout.EAST);

        frame.add(header, BorderLayout.NORTH);

        // ---------- DRAW PANEL ----------
        drawPanel = new DrawPanel();
        drawPanel.setPreferredSize(new Dimension(1220, 820));
        drawPanel.setBackground(boardSurface);
        drawPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 230, 240), 1, true),
                new EmptyBorder(4, 4, 4, 4)
        ));

        // send lines to server
        drawPanel.setDrawListener((x1, y1, x2, y2, color, stroke) -> {
            if (out != null) {
                int rgb = color.getRGB();
                out.println("DRAW|" + x1 + "|" + y1 + "|" + x2 + "|" + y2 + "|" + rgb + "|" + stroke);
            }
        });

        // send cursor updates
        drawPanel.setCursorListener((x, y) -> {
            if (out != null) {
                out.println("CURSOR|" + username + "|" + x + "|" + y);
            }
        });

        JPanel toolsPanel = createToolsPanel(accent, new Color(148, 163, 184), textLight);
        JPanel leftSide = new JPanel(new BorderLayout(12, 0));
        leftSide.setBackground(bgDark);
        leftSide.setBorder(new EmptyBorder(14, 14, 14, 10));
        leftSide.add(toolsPanel, BorderLayout.WEST);

        JPanel boardWrapper = new JPanel(new BorderLayout());
        boardWrapper.setOpaque(false);
        boardWrapper.setBorder(new EmptyBorder(0, 10, 0, 0));
        boardWrapper.add(drawPanel, BorderLayout.CENTER);

        leftSide.add(boardWrapper, BorderLayout.CENTER);

        // ---------- RIGHT SIDE (participants + chat + poll) ----------
        JPanel rightSide = new JPanel(new BorderLayout(0, 10));
        rightSide.setBackground(bgDark);
        rightSide.setBorder(new EmptyBorder(14, 10, 14, 18));
        rightSide.setPreferredSize(new Dimension(310, 760));

        // participants panel
        JPanel participantsPanel = createParticipantsPanel();
        rightSide.add(participantsPanel, BorderLayout.NORTH);

        // chat panel
        JPanel chatPanel = createChatPanel(accentBright, textLight, bgDark);
        rightSide.add(chatPanel, BorderLayout.CENTER);

        // poll panel
        pollPanel = new PollPanel();
        rightSide.add(pollPanel, BorderLayout.SOUTH);

        // ---------- SPLIT PANE ----------
        JSplitPane splitPane = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT, leftSide, rightSide);
        splitPane.setResizeWeight(0.86);
        splitPane.setBorder(null);
        frame.add(splitPane, BorderLayout.CENTER);

        frame.getContentPane().setBackground(bgDark);
        frame.setVisible(true);
    }

    private JPanel createToolsPanel(Color accent, Color neutralText, Color textLight) {
        JPanel wrapper = new JPanel();
        wrapper.setOpaque(false);
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
        wrapper.setPreferredSize(new Dimension(205, 0));

        JPanel primaryCard = createGlassCard(
                new Color(20, 26, 38, 230),
                new Color(13, 17, 24, 230),
                22,
                14);

        JPanel cardContent = new JPanel();
        cardContent.setOpaque(false);
        cardContent.setLayout(new BoxLayout(cardContent, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("Tools");
        title.setForeground(Color.WHITE);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));

        JLabel subtitle = new JLabel("minimal kit");
        subtitle.setForeground(neutralText);
        subtitle.setFont(subtitle.getFont().deriveFont(Font.PLAIN, 11f));

        JPanel heading = new JPanel();
        heading.setOpaque(false);
        heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
        heading.add(title);
        heading.add(Box.createVerticalStrut(2));
        heading.add(subtitle);

        cardContent.add(heading);
        cardContent.add(Box.createVerticalStrut(10));

        JPanel paletteCard = createSoftSectionPanel();
        JPanel paletteGrid = new JPanel(new GridLayout(2, 3, 4, 4));
        paletteGrid.setOpaque(false);

        Color[] colors = new Color[]{
                new Color(248, 250, 252),
                new Color(137, 180, 250),
                new Color(94, 234, 212),
                new Color(255, 203, 107),
                new Color(248, 113, 113),
                new Color(203, 213, 225)
        };
        for (Color swatchColor : colors) {
            JButton colorBtn = new JButton() {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(new Color(255, 255, 255, 40));
                    g2.fillOval(3, 3, getWidth() - 6, getHeight() - 6);
                    g2.setColor(swatchColor);
                    g2.fillOval(5, 5, getWidth() - 10, getHeight() - 10);
                    g2.dispose();
                }
            };
            colorBtn.setOpaque(false);
            colorBtn.setContentAreaFilled(false);
            colorBtn.setBorderPainted(false);
            colorBtn.setFocusPainted(false);
            colorBtn.setPreferredSize(new Dimension(34, 34));
            colorBtn.addActionListener(e -> drawPanel.setCurrentColor(swatchColor));
            paletteGrid.add(colorBtn);
        }
        paletteCard.add(paletteGrid, BorderLayout.CENTER);
        cardContent.add(paletteCard);
        cardContent.add(Box.createVerticalStrut(12));

        JToggleButton penButton = new JToggleButton("Pen");
        JToggleButton eraserButton = new JToggleButton("Eraser");
        penButton.setSelected(true);
        configureToolButton(penButton);
        configureToolButton(eraserButton);

        penButton.addActionListener(e -> {
            if (penButton.isSelected()) {
                eraserButton.setSelected(false);
                drawPanel.setEraserMode(false);
            } else {
                penButton.setSelected(true);
            }
        });

        eraserButton.addActionListener(e -> {
            if (eraserButton.isSelected()) {
                penButton.setSelected(false);
                drawPanel.setEraserMode(true);
            } else {
                eraserButton.setSelected(true);
            }
        });

        JPanel modeRow = createSoftSectionPanel();
        JPanel toggleRow = new JPanel(new GridLayout(1, 2, 6, 0));
        toggleRow.setOpaque(false);
        toggleRow.add(penButton);
        toggleRow.add(eraserButton);
        modeRow.add(toggleRow, BorderLayout.CENTER);
        cardContent.add(modeRow);
        cardContent.add(Box.createVerticalStrut(12));

        JSlider thicknessSlider = new JSlider(1, 12, 3);
        thicknessSlider.setOpaque(false);
        thicknessSlider.addChangeListener(e -> drawPanel.setStrokeWidth(thicknessSlider.getValue()));

        JPanel sliderHolder = createSoftSectionPanel();
        sliderHolder.add(thicknessSlider, BorderLayout.CENTER);
        cardContent.add(sliderHolder);
        cardContent.add(Box.createVerticalStrut(10));

        boardStatusLabel = new JLabel("Board unlocked");
        boardStatusLabel.setForeground(neutralText);
        boardStatusLabel.setFont(boardStatusLabel.getFont().deriveFont(Font.PLAIN, 11f));
        cardContent.add(boardStatusLabel);

        primaryCard.add(cardContent, BorderLayout.CENTER);
        wrapper.add(primaryCard);
        wrapper.add(Box.createVerticalStrut(12));

        teacherControlsPanel = new JPanel();
        teacherControlsPanel.setOpaque(false);
        teacherControlsPanel.setLayout(new BoxLayout(teacherControlsPanel, BoxLayout.Y_AXIS));
        teacherControlsPanel.setVisible(false);

        clearBoardButton = new JButton("Clear board");
        styleAccentButton(clearBoardButton, new Color(239, 68, 68), Color.WHITE);
        clearBoardButton.addActionListener(e -> {
            if (!isTeacher) return;
            drawPanel.clearBoard();
            if (out != null) {
                out.println("CLEAR|");
            }
        });

        lockToggle = new JToggleButton("Lock board");
        configureToolButton(lockToggle);
        lockToggle.addActionListener(e -> {
            if (!isTeacher || out == null) return;
            if (suppressLockToggleEvent) return;
            boolean lock = lockToggle.isSelected();
            out.println("BOARD_LOCK|" + lock);
        });

        createPollButton = new JButton("Create poll");
        configureToolButton(createPollButton);
        createPollButton.addActionListener(e -> showCreatePollDialog());

        teacherControlsPanel.add(clearBoardButton);
        teacherControlsPanel.add(Box.createVerticalStrut(6));
        teacherControlsPanel.add(lockToggle);
        teacherControlsPanel.add(Box.createVerticalStrut(6));
        teacherControlsPanel.add(createPollButton);

        JPanel teacherCard = createGlassCard(
                new Color(24, 30, 44, 230),
                new Color(17, 23, 34, 230),
                20,
                12);
        teacherCard.add(teacherControlsPanel, BorderLayout.CENTER);
        wrapper.add(teacherCard);
        wrapper.add(Box.createVerticalGlue());

        return wrapper;
    }

    private void configureToolButton(AbstractButton btn) {
        btn.setFocusPainted(false);
        btn.setBackground(new Color(28, 36, 48));
        btn.setForeground(new Color(230, 235, 243));
        btn.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        btn.setFont(btn.getFont().deriveFont(Font.PLAIN, 11f));
    }

    private void styleAccentButton(AbstractButton btn, Color bg, Color fg) {
        btn.setFocusPainted(false);
        btn.setBackground(bg);
        btn.setForeground(fg);
        btn.setFont(btn.getFont().deriveFont(Font.BOLD, 11f));
        btn.setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));
    }

    private JPanel createSectionHeader(String title, String subtitle) {
        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 13f));

        JLabel subtitleLabel = new JLabel(subtitle);
        subtitleLabel.setForeground(new Color(186, 196, 215));
        subtitleLabel.setFont(subtitleLabel.getFont().deriveFont(Font.PLAIN, 11f));

        header.add(titleLabel);
        header.add(Box.createVerticalStrut(2));
        header.add(subtitleLabel);
        return header;
    }

    private JPanel createSoftSectionPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(true);
        panel.setBackground(new Color(18, 24, 34, 210));
        panel.setBorder(new EmptyBorder(8, 10, 8, 10));
        return panel;
    }

    private JPanel createGlassCard(Color start, Color end, int arc, int padding) {
        JPanel card = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                GradientPaint gp = new GradientPaint(
                        0, 0, start,
                        getWidth(), getHeight(), end);
                g2.setPaint(gp);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
                g2.setColor(new Color(255, 255, 255, 45));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setBorder(new EmptyBorder(padding, padding, padding, padding));
        return card;
    }

    private JPanel createParticipantsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(0, 0, 10, 0));

        JLabel label = new JLabel(" Participants");
        label.setForeground(new Color(209, 213, 219));
        label.setFont(label.getFont().deriveFont(Font.BOLD, 13f));
        panel.add(label, BorderLayout.NORTH);

        participantsModel = new DefaultListModel<>();
        JList<String> list = new JList<>(participantsModel);
        list.setVisibleRowCount(4);
        list.setBackground(new Color(15, 23, 42));
        list.setForeground(new Color(226, 232, 240));
        list.setFont(list.getFont().deriveFont(12f));
        JScrollPane scroll = new JScrollPane(list);
        scroll.setPreferredSize(new Dimension(200, 80));
        scroll.setBorder(BorderFactory.createLineBorder(new Color(30, 64, 175), 1));

        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createChatPanel(Color accent, Color textLight, Color bgDark) {
        JPanel chatWrapper = new JPanel(new BorderLayout());
        chatWrapper.setOpaque(false);

        JPanel chatCard = createGlassCard(
                new Color(20, 26, 38, 235),
                new Color(13, 17, 25, 235),
                22,
                14);

        JPanel chatHeader = new JPanel(new BorderLayout());
        chatHeader.setOpaque(false);

        JLabel chatLabel = new JLabel("Chat");
        chatLabel.setForeground(Color.WHITE);
        chatLabel.setFont(chatLabel.getFont().deriveFont(Font.BOLD, 15f));

        JLabel status = new JLabel("● live");
        status.setForeground(accent);
        status.setFont(status.getFont().deriveFont(Font.BOLD, 11f));

        chatHeader.add(chatLabel, BorderLayout.WEST);
        chatHeader.add(status, BorderLayout.EAST);

        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        chatArea.setForeground(new Color(226, 232, 240));
        chatArea.setBackground(new Color(18, 24, 34, 210));
        chatArea.setFont(chatArea.getFont().deriveFont(12.5f));
        chatArea.setBorder(new EmptyBorder(6, 8, 6, 8));

        JScrollPane chatScroll = new JScrollPane(chatArea);
        chatScroll.setBorder(BorderFactory.createEmptyBorder());
        chatScroll.setOpaque(false);
        chatScroll.getViewport().setOpaque(false);

        JPanel messagesCard = createSoftSectionPanel();
        messagesCard.setOpaque(false);
        messagesCard.add(chatScroll, BorderLayout.CENTER);

        chatInput = new JTextField();
        chatInput.setFont(chatInput.getFont().deriveFont(12f));
        chatInput.setMargin(new Insets(2, 8, 2, 8));
        chatInput.setBackground(new Color(12, 16, 24, 220));
        chatInput.setForeground(textLight);
        chatInput.setCaretColor(textLight);
        chatInput.setBorder(BorderFactory.createLineBorder(new Color(54, 67, 86), 1, true));
        chatInput.setPreferredSize(new Dimension(0, 30));
        chatInput.addActionListener(e -> sendChatMessage());

        JButton sendBtn = new JButton("Send");
        styleAccentButton(sendBtn, accent, Color.WHITE);
        sendBtn.setPreferredSize(new Dimension(84, 30));
        sendBtn.addActionListener(e -> sendChatMessage());

        JPanel composer = new JPanel(new BorderLayout(6, 0));
        composer.setOpaque(false);
        composer.add(chatInput, BorderLayout.CENTER);
        composer.add(sendBtn, BorderLayout.EAST);

        JPanel composerCard = createSoftSectionPanel();
        composerCard.setOpaque(false);
        composerCard.add(composer, BorderLayout.CENTER);

        JPanel centerContent = new JPanel();
        centerContent.setOpaque(false);
        centerContent.setLayout(new BoxLayout(centerContent, BoxLayout.Y_AXIS));
        centerContent.add(messagesCard);
        centerContent.add(Box.createVerticalStrut(10));
        centerContent.add(composerCard);

        chatCard.add(chatHeader, BorderLayout.NORTH);
        chatCard.add(centerContent, BorderLayout.CENTER);

        chatWrapper.add(chatCard, BorderLayout.CENTER);
        return chatWrapper;
    }

    // ---------------------------------------------------------
    // Networking
    // ---------------------------------------------------------

    private void sendChatMessage() {
        String text = chatInput.getText().trim();
        if (!text.isEmpty() && out != null) {
            out.println("CHAT|" + username + "|" + text);
            chatInput.setText("");
        }
    }

    private void listenToServer() {
        try {
            String line;
            while ((line = in.readLine()) != null) {
                final String msg = line;
                SwingUtilities.invokeLater(() -> handleServerMessage(msg));
            }
        } catch (IOException e) {
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(frame,
                        "Disconnected from server.",
                        "Connection Lost",
                        JOptionPane.WARNING_MESSAGE);
                frame.dispose();
                System.exit(0);
            });
        }
    }

    private void handleServerMessage(String msg) {
        try {
            if (msg.startsWith("CHAT|")) {
                String[] parts = msg.split("\\|", 3);
                if (parts.length == 3) {
                    String fromUser = parts[1];
                    String text = parts[2];
                    chatArea.append(fromUser + ": " + text + "\n");
                    chatArea.setCaretPosition(
                            chatArea.getDocument().getLength());
                }

            } else if (msg.startsWith("DRAW|")) {
                String[] parts = msg.split("\\|");
                if (parts.length >= 5) {
                    int x1 = Integer.parseInt(parts[1]);
                    int y1 = Integer.parseInt(parts[2]);
                    int x2 = Integer.parseInt(parts[3]);
                    int y2 = Integer.parseInt(parts[4]);

                    Color color = Color.BLACK;
                    float stroke = 2.0f;

                    if (parts.length >= 7) {
                        int rgb = Integer.parseInt(parts[5]);
                        color = new Color(rgb, true);
                        stroke = Float.parseFloat(parts[6]);
                    }

                    drawPanel.addRemoteLine(x1, y1, x2, y2, color, stroke);
                }

            } else if (msg.startsWith("CLEAR|")) {
                drawPanel.clearBoard();

            } else if (msg.startsWith("CURSOR|")) {
                // CURSOR|username|x|y
                String[] parts = msg.split("\\|");
                if (parts.length >= 4) {
                    String user = parts[1];
                    int x = Integer.parseInt(parts[2]);
                    int y = Integer.parseInt(parts[3]);
                    // server does not echo our own cursor, so these are remote users
                    drawPanel.updateUserCursor(user, x, y);
                }

            } else if (msg.startsWith("USERLIST|")) {
                // USERLIST|u1,u2,u3
                String[] parts = msg.split("\\|", 2);
                Set<String> active = new HashSet<>();
                participantsModel.clear();
                if (parts.length == 2 && !parts[1].isEmpty()) {
                    String[] names = parts[1].split(",");
                    for (String n : names) {
                        String trimmed = n.trim();
                        if (!trimmed.isEmpty()) {
                            participantsModel.addElement(trimmed);
                            active.add(trimmed);
                        }
                    }
                }
                drawPanel.syncUserCursors(active);

            } else if (msg.startsWith("ROLE|")) {
                // ROLE|TEACHER or ROLE|STUDENT
                String[] parts = msg.split("\\|", 2);
                if (parts.length == 2) {
                    String role = parts[1];
                    isTeacher = "TEACHER".equalsIgnoreCase(role);
                    refreshRoleUI();
                }

            } else if (msg.startsWith("USERNAME|")) {
                String[] parts = msg.split("\\|", 2);
                if (parts.length == 2) {
                    username = parts[1];
                    frame.setTitle("CollabBoard – Interactive Classroom | " + username);
                    if (userLabel != null) {
                        userLabel.setText(" Connected as " + username + "  ");
                    }
                }

            } else if (msg.startsWith("BOARD_LOCK|")) {
                // BOARD_LOCK|true/false
                String[] parts = msg.split("\\|", 2);
                if (parts.length == 2) {
                    boardLocked = Boolean.parseBoolean(parts[1]);
                    boardStatusLabel.setText(
                            boardLocked ? " Board locked by teacher"
                                    : " Board unlocked");

                    if (isTeacher) {
                        suppressLockToggleEvent = true;
                        lockToggle.setSelected(boardLocked);
                        suppressLockToggleEvent = false;
                    }
                    updateDrawingPermission();
                }

            } else if (msg.startsWith("POLL_CREATE|")) {
                // POLL_CREATE|username|pollId|question|opt1;opt2;...
                String[] parts = msg.split("\\|", 5);
                if (parts.length >= 5) {
                    String pollId = parts[2];
                    String question = parts[3];
                    String[] options = parts[4].split(";");
                    pollPanel.showPoll(pollId, question, options);
                }

            } else if (msg.startsWith("POLL_RESULTS|")) {
                // POLL_RESULTS|pollId|c0;c1;c2;...
                String[] parts = msg.split("\\|", 3);
                if (parts.length == 3) {
                    String pollId = parts[1];
                    String[] cParts = parts[2].split(";");
                    int[] counts = new int[cParts.length];
                    for (int i = 0; i < cParts.length; i++) {
                        try {
                            counts[i] = Integer.parseInt(cParts[i]);
                        } catch (NumberFormatException e) {
                            counts[i] = 0;
                        }
                    }
                    pollPanel.updateResults(pollId, counts);
                }
            }

        } catch (Exception ex) {
            // keep client alive even if one message is malformed
            ex.printStackTrace();
        }
    }

    private void refreshRoleUI() {
        if (teacherControlsPanel != null) {
            teacherControlsPanel.setVisible(isTeacher);
        }
        if (clearBoardButton != null) {
            clearBoardButton.setEnabled(isTeacher);
        }
        updateDrawingPermission();
    }

    private void updateDrawingPermission() {
        boolean canDraw = isTeacher || !boardLocked;
        drawPanel.setDrawingEnabled(canDraw);
    }

    private void showCreatePollDialog() {
        JTextField questionField = new JTextField();
        JTextField opt1Field = new JTextField();
        JTextField opt2Field = new JTextField();
        JTextField opt3Field = new JTextField();
        JTextField opt4Field = new JTextField();

        JPanel panel = new JPanel(new GridLayout(0, 1, 4, 4));
        panel.add(new JLabel("Question:"));
        panel.add(questionField);
        panel.add(new JLabel("Option 1:"));
        panel.add(opt1Field);
        panel.add(new JLabel("Option 2:"));
        panel.add(opt2Field);
        panel.add(new JLabel("Option 3 (optional):"));
        panel.add(opt3Field);
        panel.add(new JLabel("Option 4 (optional):"));
        panel.add(opt4Field);

        int result = JOptionPane.showConfirmDialog(
                frame, panel, "Create Poll",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result != JOptionPane.OK_OPTION) return;
        if (out == null) return;

        String question = questionField.getText().trim();
        if (question.isEmpty()) {
            JOptionPane.showMessageDialog(frame,
                    "Question cannot be empty.",
                    "Invalid poll",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        java.util.List<String> opts = new java.util.ArrayList<>();
        if (!opt1Field.getText().trim().isEmpty()) {
            opts.add(opt1Field.getText().trim());
        }
        if (!opt2Field.getText().trim().isEmpty()) {
            opts.add(opt2Field.getText().trim());
        }
        if (!opt3Field.getText().trim().isEmpty()) {
            opts.add(opt3Field.getText().trim());
        }
        if (!opt4Field.getText().trim().isEmpty()) {
            opts.add(opt4Field.getText().trim());
        }

        if (opts.size() < 2) {
            JOptionPane.showMessageDialog(frame,
                    "Please provide at least two options.",
                    "Invalid poll",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        String pollId = "p" + System.currentTimeMillis();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < opts.size(); i++) {
            if (i > 0) sb.append(";");
            sb.append(opts.get(i).replace("|", " ").replace(";", " "));
        }

        out.println("POLL_CREATE|" + username + "|" + pollId + "|" +
                question.replace("|", " ") + "|" + sb);
    }

    // ---------------------------------------------------------
    // Poll panel
    // ---------------------------------------------------------

    private class PollPanel extends JPanel {
        private String currentPollId = null;
        private JLabel titleLabel;
        private JRadioButton[] optionButtons;
        private JProgressBar[] optionBars;
        private ButtonGroup optionGroup;
        private JButton voteButton;
        private JLabel emptyLabel;

        PollPanel() {
            setLayout(new BorderLayout());
            setOpaque(false);
            setBorder(new EmptyBorder(10, 0, 0, 0));

            emptyLabel = new JLabel("No active poll.");
            emptyLabel.setForeground(new Color(148, 163, 184));
            emptyLabel.setFont(emptyLabel.getFont().deriveFont(11f));
            add(emptyLabel, BorderLayout.CENTER);
        }

        void showPoll(String pollId, String question, String[] options) {
            removeAll();
            currentPollId = pollId;

            JPanel card = new JPanel(new BorderLayout(8, 8));
            card.setBackground(new Color(15, 23, 42));
            card.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(30, 64, 175), 1, true),
                    new EmptyBorder(10, 10, 10, 10)
            ));

            titleLabel = new JLabel("Poll: " + question);
            titleLabel.setForeground(new Color(241, 245, 249));
            titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 13f));

            JPanel top = new JPanel(new BorderLayout());
            top.setOpaque(false);
            top.add(titleLabel, BorderLayout.CENTER);

            card.add(top, BorderLayout.NORTH);

            int n = options.length;
            optionButtons = new JRadioButton[n];
            optionBars = new JProgressBar[n];
            optionGroup = new ButtonGroup();

            JPanel center = new JPanel();
            center.setOpaque(false);
            center.setLayout(new GridLayout(n, 1, 4, 4));

            for (int i = 0; i < n; i++) {
                JPanel row = new JPanel(new BorderLayout(4, 2));
                row.setOpaque(false);

                optionButtons[i] = new JRadioButton(options[i]);
                optionButtons[i].setOpaque(false);
                optionButtons[i].setForeground(new Color(226, 232, 240));
                optionButtons[i].setFont(optionButtons[i].getFont().deriveFont(12f));
                optionGroup.add(optionButtons[i]);

                optionBars[i] = new JProgressBar(0, 100);
                optionBars[i].setStringPainted(true);
                optionBars[i].setValue(0);
                optionBars[i].setForeground(new Color(56, 189, 248));

                row.add(optionButtons[i], BorderLayout.WEST);
                row.add(optionBars[i], BorderLayout.CENTER);
                center.add(row);
            }

            card.add(center, BorderLayout.CENTER);

            voteButton = new JButton("Vote");
            voteButton.setFocusPainted(false);
            voteButton.setBackground(new Color(56, 189, 248));
            voteButton.setForeground(Color.BLACK);
            voteButton.setFont(voteButton.getFont().deriveFont(Font.BOLD, 11f));
            voteButton.addActionListener(e -> sendVote());

            JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            bottom.setOpaque(false);
            bottom.add(voteButton);

            card.add(bottom, BorderLayout.SOUTH);

            add(card, BorderLayout.CENTER);
            revalidate();
            repaint();
        }

        private void sendVote() {
            if (out == null || currentPollId == null) return;

            int index = -1;
            for (int i = 0; i < optionButtons.length; i++) {
                if (optionButtons[i].isSelected()) {
                    index = i;
                    break;
                }
            }

            if (index == -1) {
                JOptionPane.showMessageDialog(frame,
                        "Please select an option before voting.",
                        "No option selected",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }

            out.println("POLL_VOTE|" + username + "|" + currentPollId + "|" + index);
        }

        void updateResults(String pollId, int[] counts) {
            if (currentPollId == null || !currentPollId.equals(pollId)) return;
            if (optionBars == null) return;

            int total = 0;
            for (int c : counts) {
                total += c;
            }

            for (int i = 0; i < optionBars.length && i < counts.length; i++) {
                int c = counts[i];
                int pct = (total == 0) ? 0 : (int) Math.round(100.0 * c / total);
                optionBars[i].setValue(pct);
                optionBars[i].setString(c + " (" + pct + "%)");
            }
        }
    }
}
