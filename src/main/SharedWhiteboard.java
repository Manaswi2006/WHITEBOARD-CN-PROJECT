package main;

import client.WhiteboardClient;
import server.WhiteboardServer;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class SharedWhiteboard {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(SharedWhiteboard::showDialog);
    }

    private static void showDialog() {
        // Parentless dialog so it centers on screen
        JDialog dialog = new JDialog((Frame) null, "Whiteboard", true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JPanel content = new JPanel(new BorderLayout(12, 12));
        content.setBorder(new EmptyBorder(18, 18, 18, 18));
        content.setBackground(Color.WHITE);

        JLabel title = new JLabel("<html><div style='text-align:center;'><span style='font-size:14pt;font-weight:600;'>Connect to Whiteboard</span><br/><span style='font-size:10pt;color:gray;'>Host or join a session</span></div></html>");
        title.setHorizontalAlignment(SwingConstants.CENTER);
        content.add(title, BorderLayout.NORTH);

        // Buttons panel
        JPanel buttons = new JPanel(new GridLayout(1, 2, 12, 12));
        buttons.setBackground(Color.WHITE);

        JButton hostBtn = makeBigButton("Host a whiteboard", new Color(0x2E86AB));
        JButton joinBtn = makeBigButton("Join a whiteboard", new Color(0x27AE60));

        buttons.add(hostBtn);
        buttons.add(joinBtn);
        content.add(buttons, BorderLayout.CENTER);

        // small cancel link
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setBackground(Color.WHITE);
        JButton cancel = new JButton("Cancel");
        cancel.setBorderPainted(false);
        cancel.setContentAreaFilled(false);
        cancel.setForeground(Color.GRAY);
        cancel.addActionListener(e -> {
            System.out.println("Cancelled");
            dialog.dispose();
        });
        bottom.add(cancel, BorderLayout.CENTER);
        content.add(bottom, BorderLayout.SOUTH);

        // actions
        hostBtn.addActionListener(e -> {
            System.out.println("Host selected");
            dialog.dispose();
            // start host flow here
            startHost();
        });
        joinBtn.addActionListener(e -> {
            System.out.println("Join selected");
            dialog.dispose();
            // start join flow here
            startJoin();
        });

        dialog.setContentPane(content);
        dialog.pack();
        dialog.setResizable(false);
        dialog.setLocationRelativeTo(null); // center on screen
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                hostBtn.requestFocusInWindow();
            }
        });
        dialog.setVisible(true);
//        System.exit(0);
    }

    private static void startJoin() {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(
                        UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}
            new WhiteboardClient().start();
        });
    }

    private static void startHost() {
        // ---- connection dialog ----
        JTextField portField = new JTextField("5001");

        JPanel connectPanel = new JPanel(new GridLayout(0, 1, 6, 6));
        connectPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        connectPanel.add(new JLabel("Server Port:"));
        connectPanel.add(portField);


        int result = JOptionPane.showConfirmDialog(
                null, connectPanel, "Start Whiteboard",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result != JOptionPane.OK_OPTION) {
            System.exit(0);
        }

        String serverPort = portField.getText().trim();
//        WhiteboardServer.startServer(Integer.parseInt(serverPort));
        new Thread(() -> WhiteboardServer.startServer(Integer.parseInt(serverPort)), "WB-Server-Thread").start();
        WhiteboardClient.AsHost(Integer.parseInt(serverPort));
    }

    private static JButton makeBigButton(String text, Color bg) {
        JButton b = new JButton(text);
        b.setForeground(Color.WHITE);
        b.setBackground(bg);
        b.setFocusPainted(false);
        b.setFont(b.getFont().deriveFont(Font.BOLD, 13f));
        b.setPreferredSize(new Dimension(220, 56));
        b.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        // Simple rounded effect:
        b.setOpaque(true);
        return b;
    }


}
