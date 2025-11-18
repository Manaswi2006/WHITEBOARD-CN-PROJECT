package client;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.*;
import java.util.List;

/**
 * Custom panel that acts as the whiteboard.
 * - Supports color, variable thickness, eraser, clear.
 * - Tracks remote user cursors and labels.
 * - Notifies listeners when the local cursor moves or a new line is drawn.
 */
public class DrawPanel extends JPanel {

    // ---- Listener interfaces ----
    public interface DrawListener {
        void onNewLine(int x1, int y1, int x2, int y2, Color color, float strokeWidth);
    }

    public interface CursorListener {
        void onCursorMove(int x, int y);
    }

    // ---- Internal line model ----
    private static class Line {
        int x1, y1, x2, y2;
        Color color;
        float stroke;

        Line(int x1, int y1, int x2, int y2, Color color, float stroke) {
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
            this.color = color;
            this.stroke = stroke;
        }
    }

    private final java.util.List<Line> lines = new ArrayList<>();
    private final Map<String, Point> userCursors = new HashMap<>();

    private int lastX, lastY;
    private DrawListener drawListener;
    private CursorListener cursorListener;

    private Color currentColor = new Color(56, 189, 248); // default cyan
    private float currentStroke = 3.0f;
    private boolean eraserMode = false;
    private boolean drawingEnabled = true;

    public DrawPanel() {
        setBackground(new Color(249, 250, 255));
        setDoubleBuffered(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));

        MouseAdapter mouseAdapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (!drawingEnabled) return;
                lastX = e.getX();
                lastY = e.getY();
                notifyCursorMove(lastX, lastY);
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (!drawingEnabled) return;
                int x = e.getX();
                int y = e.getY();
                Color drawColor = eraserMode ? getBackground() : currentColor;
                float stroke = eraserMode ? currentStroke + 4.0f : currentStroke;
                addLineInternal(lastX, lastY, x, y, drawColor, stroke, true);
                lastX = x;
                lastY = y;
                notifyCursorMove(x, y);
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                notifyCursorMove(e.getX(), e.getY());
            }
        };

        addMouseListener(mouseAdapter);
        addMouseMotionListener(mouseAdapter);
    }

    // ---- Listener wiring ----
    public void setDrawListener(DrawListener listener) {
        this.drawListener = listener;
    }

    public void setCursorListener(CursorListener listener) {
        this.cursorListener = listener;
    }

    private void notifyCursorMove(int x, int y) {
        if (cursorListener != null) {
            cursorListener.onCursorMove(x, y);
        }
    }

    // ---- External controls ----
    public void setCurrentColor(Color color) {
        this.currentColor = color;
        this.eraserMode = false;
    }

    public void setStrokeWidth(float strokeWidth) {
        this.currentStroke = Math.max(1.0f, strokeWidth);
    }

    public void setEraserMode(boolean eraserMode) {
        this.eraserMode = eraserMode;
    }

    public void clearBoard() {
        lines.clear();
        repaint();
    }

    public void setDrawingEnabled(boolean enabled) {
        this.drawingEnabled = enabled;
    }

    // Called by network layer when a DRAW message arrives
    public void addRemoteLine(int x1, int y1, int x2, int y2,
                              Color color, float stroke) {
        addLineInternal(x1, y1, x2, y2, color, stroke, false);
    }

    // Update / remove user cursors (used from network / client)
    public void updateUserCursor(String username, int x, int y) {
        userCursors.put(username, new Point(x, y));
        repaint();
    }

    public void syncUserCursors(Set<String> activeUsers) {
        userCursors.keySet().retainAll(activeUsers);
        repaint();
    }

    // ---- Internal drawing logic ----
    private void addLineInternal(int x1, int y1, int x2, int y2,
                                 Color color, float stroke,
                                 boolean notifyServer) {
        lines.add(new Line(x1, y1, x2, y2, color, stroke));
        repaint();

        if (notifyServer && drawListener != null) {
            drawListener.onNewLine(x1, y1, x2, y2, color, stroke);
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        // Light grid background with accent guides
        for (int x = 0; x < getWidth(); x += 25) {
            g2.setColor((x % 100 == 0) ? new Color(219, 234, 254) : new Color(235, 240, 248));
            g2.drawLine(x, 0, x, getHeight());
        }
        for (int y = 0; y < getHeight(); y += 25) {
            g2.setColor((y % 100 == 0) ? new Color(219, 234, 254) : new Color(235, 240, 248));
            g2.drawLine(0, y, getWidth(), y);
        }
        g2.setColor(new Color(209, 213, 219));
        g2.drawRoundRect(4, 4, getWidth() - 8, getHeight() - 8, 18, 18);

        // Draw lines
        for (Line line : lines) {
            g2.setColor(line.color);
            g2.setStroke(new BasicStroke(
                    line.stroke,
                    BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND));
            g2.drawLine(line.x1, line.y1, line.x2, line.y2);
        }

        // Draw user cursors + labels
        Font labelFont = getFont().deriveFont(Font.BOLD, 11f);
        g2.setFont(labelFont);
        FontMetrics fm = g2.getFontMetrics(labelFont);

        for (Map.Entry<String, Point> entry : userCursors.entrySet()) {
            String name = entry.getKey();
            Point p = entry.getValue();
            int x = p.x;
            int y = p.y;

            // Cursor dot
            g2.setColor(new Color(56, 189, 248));
            g2.fillOval(x - 4, y - 4, 8, 8);

            // Label background
            String label = name;
            int w = fm.stringWidth(label) + 10;
            int h = fm.getHeight();
            int lx = x + 10;
            int ly = y - h - 2;
            Shape bubble = new RoundRectangle2D.Float(lx, ly, w, h, 10, 10);
            g2.setColor(new Color(15, 23, 42, 220));
            g2.fill(bubble);

            // Label text
            g2.setColor(Color.WHITE);
            g2.drawString(label, lx + 5, ly + h - 4);
        }

        g2.dispose();
    }
}
