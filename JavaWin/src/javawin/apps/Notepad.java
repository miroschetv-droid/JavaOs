package javawin.apps;

import javawin.*;
import javawin.Desktop;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.undo.UndoManager;
import java.awt.*;
import java.awt.event.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Notepad extends JPanel implements AppBody {
    final Desktop d;
    Path file;
    final JTextArea area = new JTextArea();
    final UndoManager undo = new UndoManager();
    final UI.Label status = UI.dim("");
    boolean dirty, loading;
    float fontSize = 15f;
    Win win;

    public Notepad(Desktop d, Path p) {
        super(new BorderLayout());
        this.d = d;
        add(menu(), BorderLayout.NORTH);
        area.setFont(Theme.mono(fontSize));
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setTabSize(4);
        add(UI.scroll(area), BorderLayout.CENTER);
        JPanel st = new JPanel(new BorderLayout());
        st.setBorder(new EmptyBorder(4, 12, 5, 12));
        st.add(status, BorderLayout.WEST);
        st.add(UI.dim("UTF-8      Windows (CRLF)"), BorderLayout.EAST);
        add(st, BorderLayout.SOUTH);

        area.getDocument().addUndoableEditListener(undo);
        area.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { touched(); }
            public void removeUpdate(DocumentEvent e) { touched(); }
            public void changedUpdate(DocumentEvent e) {}
        });
        area.addCaretListener(e -> caret());
        if (p != null) load(p);
        caret();
    }

    JMenuBar menu() {
        JMenuBar mb = UI.menuBar();
        JMenu f = new JMenu("Файл");
        f.add(mi("Создать", KeyEvent.VK_N, () -> ifSaved(this::clear)));
        f.add(mi("Открыть…", KeyEvent.VK_O, this::openDialog));
        f.add(mi("Сохранить", KeyEvent.VK_S, () -> save(null)));
        f.add(mi("Сохранить как…", 0, () -> saveAs(null)));
        f.addSeparator();
        f.add(mi("Выход", 0, () -> { if (win != null) win.close(); }));
        JMenu e = new JMenu("Правка");
        e.add(mi("Отменить", KeyEvent.VK_Z, () -> { if (undo.canUndo()) undo.undo(); }));
        e.add(mi("Вернуть", KeyEvent.VK_Y, () -> { if (undo.canRedo()) undo.redo(); }));
        e.addSeparator();
        e.add(mi("Вырезать", 0, area::cut));
        e.add(mi("Копировать", 0, area::copy));
        e.add(mi("Вставить", 0, area::paste));
        e.addSeparator();
        e.add(mi("Найти…", KeyEvent.VK_F, this::find));
        e.add(mi("Выделить всё", 0, area::selectAll));
        JMenuItem dt = mi("Дата и время", 0, () -> area.replaceSelection(
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm dd.MM.yyyy"))));
        dt.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0));
        e.add(dt);
        JMenu v = new JMenu("Вид");
        JCheckBoxMenuItem wrap = new JCheckBoxMenuItem("Перенос по словам", true);
        wrap.addActionListener(x -> area.setLineWrap(wrap.isSelected()));
        v.add(wrap);
        v.add(mi("Крупнее", KeyEvent.VK_EQUALS, () -> zoom(2)));
        v.add(mi("Мельче", KeyEvent.VK_MINUS, () -> zoom(-2)));
        mb.add(f);
        mb.add(e);
        mb.add(v);
        return mb;
    }

    JMenuItem mi(String text, int ctrlKey, Runnable r) {
        JMenuItem i = new JMenuItem(text);
        if (ctrlKey != 0) i.setAccelerator(KeyStroke.getKeyStroke(ctrlKey, InputEvent.CTRL_DOWN_MASK));
        i.addActionListener(e -> r.run());
        return i;
    }

    void zoom(int dz) {
        fontSize = Math.max(9, Math.min(48, fontSize + dz));
        area.setFont(Theme.mono(fontSize));
    }

    void touched() {
        if (loading || dirty) return;
        dirty = true;
        title();
    }

    void caret() {
        try {
            int pos = area.getCaretPosition(), line = area.getLineOfOffset(pos);
            status.setText("Стр " + (line + 1) + ", стлб " + (pos - area.getLineStartOffset(line) + 1)
                + "      " + area.getDocument().getLength() + " символов");
        } catch (BadLocationException ignored) {}
    }

    String name() { return file == null ? "Без имени" : file.getFileName().toString(); }

    void title() {
        if (win != null) win.setTitle((dirty ? "*" : "") + name() + " — Блокнот");
    }

    @Override public void attached(Win w) {
        win = w;
        title();
    }

    @Override public Component focusTarget() { return area; }

    @Override public boolean beforeClose(Runnable doClose) {
        if (!dirty) return false;
        ifSaved(doClose);
        return true;
    }

    // если есть несохранённое — спросить, затем выполнить next
    void ifSaved(Runnable next) {
        if (!dirty) { next.run(); return; }
        d.ask("Блокнот", "Сохранить изменения в файле «" + name() + "»?",
            new String[] { "Сохранить", "Не сохранять", "Отмена" }, i -> {
                if (i == 0) save(next);
                else if (i == 1) next.run();
            });
    }

    void clear() {
        loading = true;
        area.setText("");
        loading = false;
        file = null;
        dirty = false;
        undo.discardAllEdits();
        title();
    }

    void load(Path p) {
        try {
            String s = Vfs.readText(p);
            if (s.startsWith("\uFEFF")) s = s.substring(1);
            loading = true;
            area.setText(s.replace("\r\n", "\n"));
            area.setCaretPosition(0);
            loading = false;
            file = p;
            dirty = false;
            undo.discardAllEdits();
            title();
        } catch (Exception e) {
            loading = false;
            d.error(e);
        }
    }

    void openDialog() {
        ifSaved(() -> d.prompt("Открыть", "Путь к файлу:", Vfs.virt(file != null ? file.getParent() : Vfs.docs()) + "\\", s -> {
            Path p = Vfs.resolve(Vfs.docs(), s);
            if (Files.isRegularFile(p)) load(p);
            else d.message("Блокнот", "Файл «" + s + "» не найден.");
        }));
    }

    void save(Runnable after) {
        if (file == null) { saveAs(after); return; }
        try {
            Files.writeString(file, area.getText().replace("\n", "\r\n"), StandardCharsets.UTF_8);
            dirty = false;
            title();
            if (after != null) after.run();
        } catch (Exception e) { d.error(e); }
    }

    void saveAs(Runnable after) {
        String init = file != null ? file.getFileName().toString() : "Без имени.txt";
        d.prompt("Сохранить как", "Имя файла (сохранится в «Документы», можно указать путь C:\\…):", init, s -> {
            Path dir = file != null ? file.getParent() : Vfs.docs();
            Path p = Vfs.resolve(dir, s.contains(".") ? s : s + ".txt");
            if (Files.isDirectory(p)) { d.message("Блокнот", "Это папка, а не файл."); return; }
            file = p;
            save(after);
        });
    }

    String lastFind = "";

    void find() {
        d.prompt("Найти", "Что искать:", lastFind.isEmpty() ? "текст" : lastFind, s -> {
            lastFind = s;
            String t = area.getText().toLowerCase();
            int from = area.getSelectionEnd();
            int i = t.indexOf(s.toLowerCase(), from);
            if (i < 0) i = t.indexOf(s.toLowerCase());
            if (i < 0) { d.message("Блокнот", "Не удаётся найти «" + s + "»."); return; }
            area.requestFocusInWindow();
            area.select(i, i + s.length());
        });
    }
}
