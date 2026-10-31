package javawin.apps;

import javawin.*;
import javawin.Desktop;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.math.BigDecimal;
import java.math.MathContext;

public class Calc extends JPanel implements AppBody {
    final UI.Label expr = UI.dim(" ");
    final UI.Label display = new UI.Label("0", false, 44f, Font.BOLD);
    double acc;
    String op, cur = "0";
    boolean fresh = true, error;

    static final String[] KEYS = {
        "%", "CE", "C", "←",
        "1/x", "x²", "√x", "÷",
        "7", "8", "9", "×",
        "4", "5", "6", "−",
        "1", "2", "3", "+",
        "±", "0", ",", "=" };

    public Calc() {
        super(new BorderLayout());
        setBorder(new EmptyBorder(8, 8, 8, 8));
        setFocusable(true);
        JPanel top = new JPanel(new GridLayout(2, 1));
        top.setBorder(new EmptyBorder(10, 10, 14, 10));
        expr.setHorizontalAlignment(SwingConstants.RIGHT);
        expr.setFont(Theme.font(Font.PLAIN, 14f));
        display.setHorizontalAlignment(SwingConstants.RIGHT);
        top.add(expr);
        top.add(display);
        add(top, BorderLayout.NORTH);
        JPanel grid = new JPanel(new GridLayout(6, 4, 3, 3));
        for (String k : KEYS) {
            UI.Btn b = UI.button(k, () -> press(k));
            b.setFont(Theme.font(k.matches("[0-9]") ? Font.BOLD : Font.PLAIN, 17f));
            b.setFocusable(false);
            if (k.equals("=")) b.accent = true;
            grid.add(b);
        }
        add(grid, BorderLayout.CENTER);

        String keys = "0123456789";
        for (char c : keys.toCharArray()) bind(String.valueOf(c), String.valueOf(c));
        bind("typed +", "+");
        bind("typed -", "−");
        bind("typed *", "×");
        bind("typed /", "÷");
        bind("typed ,", ",");
        bind("typed .", ",");
        bind("typed %", "%");
        bind("ENTER", "=");
        bind("typed =", "=");
        bind("BACK_SPACE", "←");
        bind("ESCAPE", "C");
        bind("DELETE", "CE");
        for (int i = 0; i <= 9; i++) bind("NUMPAD" + i, String.valueOf(i));
    }

    void bind(String stroke, String key) {
        KeyStroke ks = stroke.length() == 1 ? KeyStroke.getKeyStroke(stroke.charAt(0)) : KeyStroke.getKeyStroke(stroke);
        if (ks == null) return;
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(ks, "c" + key);
        getActionMap().put("c" + key, new AbstractAction() { public void actionPerformed(ActionEvent e) { press(key); } });
    }

    @Override public Component focusTarget() { return this; }

    @Override public Dimension getPreferredSize() { return new Dimension(340, 500); }

    double val() { return Double.parseDouble(cur.replace(',', '.')); }

    void press(String k) {
        if (error && !k.equals("C") && !k.equals("CE") && !k.matches("[0-9]")) return;
        if (error) { reset(); }
        switch (k) {
            case "0", "1", "2", "3", "4", "5", "6", "7", "8", "9" -> {
                if (fresh) { cur = k; fresh = false; }
                else if (cur.equals("0")) cur = k;
                else if (cur.replaceAll("[^0-9]", "").length() < 16) cur += k;
            }
            case "," -> {
                if (fresh) { cur = "0,"; fresh = false; }
                else if (!cur.contains(",")) cur += ",";
            }
            case "←" -> {
                if (fresh) break;
                cur = cur.length() > 1 ? cur.substring(0, cur.length() - 1) : "0";
                if (cur.equals("-")) cur = "0";
            }
            case "C" -> { reset(); expr.setText(" "); }
            case "CE" -> { cur = "0"; fresh = true; }
            case "±" -> { if (!cur.equals("0")) cur = cur.startsWith("-") ? cur.substring(1) : "-" + cur; }
            case "%" -> { cur = fmt(op == null ? val() / 100 : acc * val() / 100); fresh = true; }
            case "1/x" -> unary(1 / val(), "1/(" + cur + ")");
            case "x²" -> unary(val() * val(), "sqr(" + cur + ")");
            case "√x" -> unary(val() < 0 ? Double.NaN : Math.sqrt(val()), "√(" + cur + ")");
            case "+", "−", "×", "÷" -> {
                if (op != null && !fresh) { acc = apply(acc, op, val()); cur = fmt(acc); }
                else if (op == null || !fresh) acc = val();
                op = k;
                if (!error) expr.setText(fmt(acc) + " " + op);
                fresh = true;
            }
            case "=" -> {
                if (op == null) break;
                double b = val();
                String e = fmt(acc) + " " + op + " " + cur + " =";
                acc = apply(acc, op, b);
                cur = fmt(acc);
                if (!error) expr.setText(e);
                op = null;
                fresh = true;
            }
        }
        display.setText(cur);
        fit();
    }

    void unary(double v, String label) {
        expr.setText(label);
        cur = fmt(v);
        fresh = true;
    }

    void reset() {
        acc = 0;
        op = null;
        cur = "0";
        fresh = true;
        error = false;
    }

    double apply(double a, String op, double b) {
        return switch (op) {
            case "+" -> a + b;
            case "−" -> a - b;
            case "×" -> a * b;
            default -> b == 0 ? Double.NaN : a / b;
        };
    }

    String fmt(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            error = true;
            expr.setText(" ");
            return "Деление на ноль невозможно";
        }
        BigDecimal b = new BigDecimal(v).round(new MathContext(15)).stripTrailingZeros();
        String s = Math.abs(v) >= 1e16 || (Math.abs(v) < 1e-10 && v != 0) ? b.toString() : b.toPlainString();
        return s.replace('.', ',');
    }

    void fit() {
        float size = 44f;
        display.setFont(Theme.font(Font.BOLD, size));
        while (size > 14 && display.getFontMetrics(display.getFont()).stringWidth(display.getText()) > 300) {
            size -= 2;
            display.setFont(Theme.font(Font.BOLD, size));
        }
    }
}
