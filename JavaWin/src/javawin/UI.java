package javawin;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;

// Свои кнопки, подписи, прокрутка — всё берёт цвета из Theme при отрисовке.
public final class UI {
    private UI() {}

    public static void aa(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    }

    public static Btn button(String text, Runnable r) {
        Btn b = new Btn(text);
        if (r != null) b.addActionListener(e -> r.run());
        return b;
    }

    public static Btn accent(String text, Runnable r) {
        Btn b = button(text, r);
        b.accent = true;
        return b;
    }

    public static Btn icon(String kind, String tip, Runnable r) {
        Btn b = button("", r);
        b.setIcon(Icons.icon(kind, 16));
        b.setToolTipText(tip);
        b.flat = true;
        b.setBorder(new EmptyBorder(7, 9, 7, 9));
        return b;
    }

    public static Label label(String text) { return new Label(text, false, 13f, Font.PLAIN); }
    public static Label dim(String text) { return new Label(text, true, 12f, Font.PLAIN); }
    public static Label title(String text, float size) { return new Label(text, false, size, Font.BOLD); }

    public static JTextField field(String text) {
        JTextField f = new JTextField(text);
        f.addFocusListener(new FocusAdapter() {
            public void focusGained(FocusEvent e) { f.repaint(); }
            public void focusLost(FocusEvent e) { f.repaint(); }
        });
        return f;
    }

    // строка меню в цвет окна (стандартная в Metal рисуется светлой)
    public static JMenuBar menuBar() {
        JMenuBar mb = new JMenuBar() {
            @Override protected void paintComponent(Graphics g) {
                g.setColor(Theme.window());
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        mb.setBorder(new EmptyBorder(2, 4, 2, 4));
        return mb;
    }

    public static JScrollPane scroll(Component c) {
        JScrollPane s = new JScrollPane(c) {
            public JScrollBar createVerticalScrollBar() { return new Bar(JScrollBar.VERTICAL); }
            public JScrollBar createHorizontalScrollBar() { return new Bar(JScrollBar.HORIZONTAL); }
        };
        s.setBorder(BorderFactory.createEmptyBorder());
        s.getVerticalScrollBar().setUnitIncrement(24);
        s.getHorizontalScrollBar().setUnitIncrement(24);
        return s;
    }

    public static JPanel row(int gap, Component... cs) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, gap, 0));
        p.setOpaque(false);
        for (Component c : cs) p.add(c);
        return p;
    }

    public static JPanel pad(JComponent c, int t, int l, int b, int r) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(t, l, b, r));
        p.add(c);
        return p;
    }

    // ---------------------------------------------------------------- кнопка

    public static class Btn extends JButton {
        public boolean accent, flat, toggled;

        public Btn(String text) {
            super(text);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setBorder(new EmptyBorder(7, 16, 7, 16));
            setFont(Theme.font(Font.PLAIN, 13f));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        @Override public Color getForeground() {
            return accent ? Color.WHITE : (isEnabled() ? Theme.text() : Theme.dim());
        }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            aa(g);
            ButtonModel m = getModel();
            Color bg;
            if (accent) bg = m.isPressed() ? Theme.accent.darker() : m.isRollover() ? Theme.mix(Theme.accent, Color.WHITE, 0.12) : Theme.accent;
            else if (toggled) bg = Theme.selection();
            else if (flat) bg = m.isPressed() ? Theme.hover().darker() : m.isRollover() ? Theme.hover() : null;
            else bg = m.isPressed() ? Theme.hover() : m.isRollover() ? Theme.mix(Theme.control(), Theme.text(), 0.06) : Theme.control();
            if (bg != null) {
                g.setColor(bg);
                g.fill(new RoundRectangle2D.Double(0, 0, getWidth() - 1, getHeight() - 1, 8, 8));
                if (!accent && !flat && !toggled) {
                    g.setColor(Theme.border());
                    g.draw(new RoundRectangle2D.Double(0, 0, getWidth() - 1, getHeight() - 1, 8, 8));
                }
            }
            if (toggled) {
                g.setColor(Theme.accent);
                g.fillRoundRect(2, getHeight() / 2 - 7, 3, 14, 3, 3);
            }
            g.dispose();
            super.paintComponent(g0);
        }
    }

    // ---------------------------------------------------------------- подпись

    public static class Label extends JLabel {
        final boolean dim;

        public Label(String text, boolean dim, float size, int style) {
            super(text);
            this.dim = dim;
            setFont(Theme.font(style, size));
        }

        @Override public Color getForeground() { return dim ? Theme.dim() : Theme.text(); }
    }

    // ---------------------------------------------------------------- переключатель

    public static class Switch extends JComponent {
        public boolean on;
        final Runnable change;

        public Switch(boolean on, Runnable change) {
            this.on = on;
            this.change = change;
            setPreferredSize(new Dimension(44, 22));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                public void mouseClicked(MouseEvent e) {
                    Switch.this.on = !Switch.this.on;
                    repaint();
                    if (change != null) change.run();
                }
            });
        }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            aa(g);
            int w = 40, h = 20, y = (getHeight() - h) / 2;
            if (on) {
                g.setColor(Theme.accent);
                g.fillRoundRect(0, y, w, h, h, h);
                g.setColor(Color.WHITE);
                g.fillOval(w - h + 4, y + 4, h - 8, h - 8);
            } else {
                g.setColor(Theme.dim());
                g.drawRoundRect(0, y, w - 1, h - 1, h, h);
                g.fillOval(5, y + 5, h - 10, h - 10);
            }
            g.dispose();
        }
    }

    // ---------------------------------------------------------------- тонкая полоса прокрутки

    static class Bar extends JScrollBar {
        Bar(int o) { super(o); }

        @Override public void updateUI() { setUI(new SlimUI()); }
    }

    static class SlimUI extends BasicScrollBarUI {
        @Override protected void configureScrollBarColors() {}

        @Override protected JButton createDecreaseButton(int o) { return zero(); }
        @Override protected JButton createIncreaseButton(int o) { return zero(); }

        static JButton zero() {
            JButton b = new JButton();
            b.setPreferredSize(new Dimension(0, 0));
            b.setMinimumSize(new Dimension(0, 0));
            b.setMaximumSize(new Dimension(0, 0));
            return b;
        }

        @Override public Dimension getPreferredSize(JComponent c) {
            return scrollbar.getOrientation() == JScrollBar.VERTICAL ? new Dimension(10, 48) : new Dimension(48, 10);
        }

        @Override protected void paintTrack(Graphics g, JComponent c, Rectangle r) {
            g.setColor(Theme.window());
            g.fillRect(r.x, r.y, r.width, r.height);
        }

        @Override protected void paintThumb(Graphics g0, JComponent c, Rectangle r) {
            if (r.isEmpty()) return;
            Graphics2D g = (Graphics2D) g0.create();
            aa(g);
            g.setColor(isDragging || isThumbRollover() ? Theme.dim() : Theme.mix(Theme.window(), Theme.dim(), 0.55));
            if (scrollbar.getOrientation() == JScrollBar.VERTICAL)
                g.fillRoundRect(r.x + 3, r.y + 2, r.width - 6, r.height - 4, 6, 6);
            else
                g.fillRoundRect(r.x + 2, r.y + 3, r.width - 4, r.height - 6, 6, 6);
            g.dispose();
        }
    }

    public static void text(Graphics2D g, String s, int x, int y, int maxW) {
        FontMetrics fm = g.getFontMetrics();
        if (fm.stringWidth(s) > maxW) {
            while (!s.isEmpty() && fm.stringWidth(s + "…") > maxW) s = s.substring(0, s.length() - 1);
            s += "…";
        }
        g.drawString(s, x, y);
    }
}
