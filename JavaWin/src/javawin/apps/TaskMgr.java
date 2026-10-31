package javawin.apps;

import javawin.*;
import javawin.Desktop;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Path2D;
import java.lang.management.ManagementFactory;
import java.util.List;

public class TaskMgr extends JPanel implements AppBody {
    final Desktop d;
    final Graph cpu = new Graph("ЦП", "%"), mem = new Graph("Память", "%");
    final DefaultListModel<Win> model = new DefaultListModel<>();
    final JList<Win> list = new JList<>(model);
    final UI.Label info = UI.dim("");
    final Timer timer;
    final long t0 = System.currentTimeMillis();

    public TaskMgr(Desktop d) {
        super(new BorderLayout());
        this.d = d;
        setBorder(new EmptyBorder(10, 12, 10, 12));
        JPanel graphs = new JPanel(new GridLayout(1, 2, 10, 0));
        graphs.add(cpu);
        graphs.add(mem);
        graphs.setPreferredSize(new Dimension(10, 170));
        add(graphs, BorderLayout.NORTH);

        JPanel mid = new JPanel(new BorderLayout(0, 6));
        mid.setBorder(new EmptyBorder(14, 0, 8, 0));
        mid.add(UI.title("Приложения", 14f), BorderLayout.NORTH);
        list.setCellRenderer((l, w, i, sel, f) -> {
            JLabel lb = new JLabel(w.getTitle(), Icons.icon(appIcon(w), 18), SwingConstants.LEFT);
            lb.setIconTextGap(10);
            lb.setOpaque(true);
            lb.setBorder(new EmptyBorder(6, 10, 6, 10));
            lb.setBackground(sel ? Theme.selection() : Theme.field());
            lb.setForeground(Theme.text());
            lb.setText("<html>" + esc(w.getTitle()) + "&nbsp;&nbsp;<font color='#888888'>"
                + (w == d.activeWindow() ? "активно" : "работает") + "</font></html>");
            return lb;
        });
        list.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) { if (e.getClickCount() == 2) switchTo(); }
        });
        mid.add(UI.scroll(list), BorderLayout.CENTER);
        add(mid, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(info, BorderLayout.WEST);
        bottom.add(UI.row(6, UI.button("Переключиться", this::switchTo), UI.accent("Снять задачу", this::kill)), BorderLayout.EAST);
        add(bottom, BorderLayout.SOUTH);

        timer = new Timer(1000, e -> sample());
        timer.start();
        sample();
    }

    static String esc(String s) { return s.replace("&", "&amp;").replace("<", "&lt;"); }

    static String appIcon(Win w) {
        Apps.App a = Apps.get(w.appId());
        return a == null ? "file" : a.icon();
    }

    void switchTo() {
        Win w = list.getSelectedValue();
        if (w != null) d.activate(w);
    }

    void kill() {
        Win w = list.getSelectedValue();
        if (w != null) d.closeWin(w);
        sample();
    }

    @Override public void closing() { timer.stop(); }

    void sample() {
        double c = 0, m = 0;
        long total = 0, used = 0;
        try {
            var os = (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            c = Math.max(0, os.getCpuLoad()) * 100;
            total = os.getTotalMemorySize();
            used = total - os.getFreeMemorySize();
            m = total > 0 ? used * 100.0 / total : 0;
        } catch (Throwable ignored) {}
        cpu.add(c, String.format("%.0f%%", c));
        mem.add(m, total > 0 ? String.format("%.1f / %.1f ГБ", used / 1073741824.0, total / 1073741824.0) : "—");

        List<Win> ws = d.windows();
        Win sel = list.getSelectedValue();
        boolean same = ws.size() == model.size();
        for (int i = 0; same && i < ws.size(); i++) same = ws.get(i) == model.get(i);
        if (!same) {
            model.clear();
            for (Win w : ws) model.addElement(w);
            if (sel != null && ws.contains(sel)) list.setSelectedValue(sel, false);
        }
        list.repaint();
        Runtime rt = Runtime.getRuntime();
        long up = (System.currentTimeMillis() - t0) / 1000;
        info.setText("Окон: " + ws.size() + "   Потоков: " + Thread.activeCount()
            + "   Память системы: " + Vfs.size(rt.totalMemory() - rt.freeMemory())
            + "   Открыт: " + String.format("%d:%02d", up / 60, up % 60));
    }

    static class Graph extends JComponent {
        final String name, unit;
        final double[] v = new double[60];
        int n;
        String label = "";

        Graph(String name, String unit) {
            this.name = name;
            this.unit = unit;
        }

        void add(double x, String label) {
            System.arraycopy(v, 1, v, 0, v.length - 1);
            v[v.length - 1] = x;
            n = Math.min(v.length, n + 1);
            this.label = label;
            repaint();
        }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            UI.aa(g);
            int w = getWidth(), h = getHeight();
            g.setColor(Theme.panel());
            g.fillRoundRect(0, 0, w, h, 10, 10);
            g.setColor(Theme.text());
            g.setFont(Theme.font(Font.BOLD, 14f));
            g.drawString(name, 14, 24);
            g.setFont(Theme.font(Font.PLAIN, 13f));
            g.setColor(Theme.dim());
            g.drawString(label, w - 14 - g.getFontMetrics().stringWidth(label), 24);
            int gx = 14, gy = 36, gw = w - 28, gh = h - 50;
            g.setColor(Theme.border());
            g.drawRect(gx, gy, gw, gh);
            for (int i = 1; i < 4; i++) g.drawLine(gx, gy + gh * i / 4, gx + gw, gy + gh * i / 4);
            for (int i = 1; i < 6; i++) g.drawLine(gx + gw * i / 6, gy, gx + gw * i / 6, gy + gh);
            if (n > 1) {
                Path2D line = new Path2D.Double(), area = new Path2D.Double();
                int start = v.length - n;
                for (int i = start; i < v.length; i++) {
                    double x = gx + gw * (i / (double) (v.length - 1));
                    double y = gy + gh - gh * Math.min(100, v[i]) / 100.0;
                    if (i == start) { line.moveTo(x, y); area.moveTo(x, gy + gh); }
                    line.lineTo(x, y);
                    area.lineTo(x, y);
                }
                area.lineTo(gx + gw, gy + gh);
                area.closePath();
                g.setColor(Theme.alpha(Theme.accent, 50));
                g.fill(area);
                g.setColor(Theme.accent);
                g.setStroke(new BasicStroke(1.6f));
                g.draw(line);
            }
            g.dispose();
        }
    }
}
