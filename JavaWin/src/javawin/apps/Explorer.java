package javawin.apps;

import javawin.*;
import javawin.Desktop;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

// Проводник по виртуальному диску C:.
public class Explorer extends JPanel implements AppBody {
    final Desktop d;
    Path cur;
    final Deque<Path> back = new ArrayDeque<>(), fwd = new ArrayDeque<>();
    final DefaultListModel<Path> model = new DefaultListModel<>();
    final JList<Path> list = new JList<>(model);
    final JTextField addr = UI.field("");
    final UI.Label status = UI.dim("");
    final UI.Btn emptyTrash;
    final JPanel nav = new JPanel();
    Win win;

    public Explorer(Desktop d, Path start) {
        super(new BorderLayout());
        this.d = d;

        JPanel tools = new JPanel(new BorderLayout(8, 0));
        tools.setBorder(new EmptyBorder(6, 8, 6, 8));
        JPanel arrows = UI.row(2,
            UI.icon("back", "Назад (Backspace)", this::goBack),
            UI.icon("fwd", "Вперёд", this::goFwd),
            UI.icon("up", "Вверх", this::goUp),
            UI.icon("refresh", "Обновить (F5)", this::reload));
        tools.add(arrows, BorderLayout.WEST);
        tools.add(addr, BorderLayout.CENTER);
        UI.Btn create = UI.button("Создать", null);
        create.setIcon(Icons.icon("plus", 14));
        create.addActionListener(e -> {
            JPopupMenu m = new JPopupMenu();
            m.add(item("Папку", this::newFolder));
            m.add(item("Текстовый документ", this::newText));
            m.show(create, 0, create.getHeight());
        });
        emptyTrash = UI.button("Очистить корзину", this::emptyTrash);
        tools.add(UI.row(6, emptyTrash, create), BorderLayout.EAST);
        add(tools, BorderLayout.NORTH);

        nav.setLayout(new BoxLayout(nav, BoxLayout.Y_AXIS));
        nav.setBorder(new EmptyBorder(6, 8, 6, 4));
        place("Рабочий стол", "folder", Vfs.desktop());
        place("Документы", "folder", Vfs.docs());
        place("Рисунки", "image", Vfs.pics());
        place("Загрузки", "folder", Vfs.downloads());
        nav.add(Box.createVerticalStrut(10));
        place("Этот компьютер (C:)", "drive", Vfs.root());
        place("Корзина", "trash", Vfs.trash());
        nav.add(Box.createVerticalGlue());
        JPanel navWrap = new JPanel(new BorderLayout());
        navWrap.add(nav, BorderLayout.NORTH);
        navWrap.setPreferredSize(new Dimension(200, 10));

        list.setLayoutOrientation(JList.HORIZONTAL_WRAP);
        list.setVisibleRowCount(-1);
        list.setFixedCellWidth(104);
        list.setFixedCellHeight(96);
        list.setCellRenderer(new Cell());
        list.setBorder(new EmptyBorder(8, 8, 8, 8));
        list.setDragEnabled(false);
        JScrollPane sc = UI.scroll(list);
        sc.getViewport().addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                list.clearSelection();
                list.requestFocusInWindow();
                if (SwingUtilities.isRightMouseButton(e)) bgMenu().show(sc.getViewport(), e.getX(), e.getY());
            }
        });

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, navWrap, sc);
        split.setBorder(null);
        split.setDividerSize(1);
        split.setDividerLocation(200);
        add(split, BorderLayout.CENTER);

        JPanel st = new JPanel(new BorderLayout());
        st.setBorder(new EmptyBorder(4, 12, 6, 12));
        st.add(status);
        add(st, BorderLayout.SOUTH);

        list.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                int i = list.locationToIndex(e.getPoint());
                boolean onItem = i >= 0 && list.getCellBounds(i, i).contains(e.getPoint());
                if (!onItem) list.clearSelection();
                else if (SwingUtilities.isRightMouseButton(e) && !list.isSelectedIndex(i)) list.setSelectedIndex(i);
                if (SwingUtilities.isRightMouseButton(e)) (onItem ? itemMenu() : bgMenu()).show(list, e.getX(), e.getY());
                else if (onItem && e.getClickCount() == 2) openSel();
            }
        });
        key(list, KeyEvent.VK_ENTER, 0, this::openSel);
        key(list, KeyEvent.VK_DELETE, 0, this::delete);
        key(list, KeyEvent.VK_F2, 0, this::rename);
        key(list, KeyEvent.VK_BACK_SPACE, 0, this::goBack);
        key(this, KeyEvent.VK_F5, 0, this::reload);
        addr.addActionListener(e -> {
            Path p = Vfs.resolve(cur, addr.getText());
            if (Files.isDirectory(p)) go(p, true);
            else if (Files.exists(p)) d.openFile(p);
            else { d.message("Проводник", "Не удаётся найти «" + addr.getText() + "»."); addr.setText(Vfs.virt(cur)); }
        });

        Path s = start != null && Files.isDirectory(start) ? start : Vfs.root();
        go(s, false);
        watch.start();
    }

    static JMenuItem item(String t, Runnable r) {
        JMenuItem i = new JMenuItem(t);
        i.addActionListener(e -> r.run());
        return i;
    }

    static void key(JComponent c, int code, int mods, Runnable r) {
        String id = "k" + code + "_" + mods;
        c.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(code, mods), id);
        c.getActionMap().put(id, new AbstractAction() { public void actionPerformed(ActionEvent e) { r.run(); } });
    }

    void place(String name, String icon, Path p) {
        UI.Btn b = UI.button(name, () -> go(p, true));
        b.flat = true;
        b.setIcon(Icons.icon(icon, 18));
        b.setIconTextGap(10);
        b.setHorizontalAlignment(SwingConstants.LEFT);
        b.setBorder(new EmptyBorder(7, 12, 7, 10));
        b.setAlignmentX(LEFT_ALIGNMENT);
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        b.putClientProperty("path", p);
        nav.add(b);
    }

    @Override public void attached(Win w) {
        win = w;
        updateTitle();
    }

    @Override public Component focusTarget() { return list; }

    @Override public void closing() { watch.stop(); }

    // открытая папка обновляется сама, когда в ней что-то появляется (например, скачанный файл)
    String sig = "";
    final javax.swing.Timer watch = new javax.swing.Timer(1500, e -> {
        if (!isShowing() || cur == null) return;
        String now = signature(cur);
        if (!now.equals(sig)) {
            List<Path> sel = list.getSelectedValuesList();
            reload();
            for (Path p : sel) { int i = model.indexOf(p); if (i >= 0) list.addSelectionInterval(i, i); }
        }
    });

    static String signature(Path dir) {
        StringBuilder b = new StringBuilder();
        for (Path p : Vfs.list(dir)) {
            b.append(p.getFileName()).append('/');
            try { b.append(Files.size(p)); } catch (IOException ignored) {}
            b.append(';');
        }
        return b.toString();
    }

    void updateTitle() {
        if (win != null) win.setTitle(Vfs.name(cur) + " — Проводник");
    }

    // ---------------------------------------------------------------- навигация

    public void go(Path p, boolean push) {
        if (!Files.isDirectory(p)) return;
        if (push && cur != null && !cur.equals(p)) { back.push(cur); fwd.clear(); }
        cur = p;
        reload();
    }

    void goBack() { if (!back.isEmpty()) { fwd.push(cur); cur = back.pop(); reload(); } }
    void goFwd() { if (!fwd.isEmpty()) { back.push(cur); cur = fwd.pop(); reload(); } }
    void goUp() { if (!cur.equals(Vfs.root())) go(cur.getParent(), true); }

    void reload() {
        sig = cur != null && Files.isDirectory(cur) ? signature(cur) : "";
        if (!Files.isDirectory(cur)) cur = Vfs.root();
        model.clear();
        List<Path> items = Vfs.list(cur);
        for (Path p : items) model.addElement(p);
        addr.setText(Vfs.virt(cur));
        long n = items.size();
        status.setText(n + " " + plural(n, "элемент", "элемента", "элементов"));
        emptyTrash.setVisible(cur.equals(Vfs.trash()));
        for (Component c : nav.getComponents())
            if (c instanceof UI.Btn b) { b.toggled = cur.equals(b.getClientProperty("path")); b.repaint(); }
        updateTitle();
        revalidate();
        repaint();
    }

    static String plural(long n, String one, String few, String many) {
        long m10 = n % 10, m100 = n % 100;
        if (m10 == 1 && m100 != 11) return one;
        if (m10 >= 2 && m10 <= 4 && (m100 < 12 || m100 > 14)) return few;
        return many;
    }

    void openSel() {
        Path p = list.getSelectedValue();
        if (p == null) return;
        if (Files.isDirectory(p)) go(p, true);
        else d.openFile(p);
    }

    // ---------------------------------------------------------------- операции

    void changed() {
        reload();
        d.repaint();
    }

    void newFolder() {
        d.prompt("Новая папка", "Имя папки:", "Новая папка", n -> {
            try { Files.createDirectories(Vfs.unique(cur, n)); changed(); } catch (Exception e) { d.error(e); }
        });
    }

    void newText() {
        d.prompt("Новый документ", "Имя файла:", "Новый текстовый документ.txt", n -> {
            try { Files.createFile(Vfs.unique(cur, n.contains(".") ? n : n + ".txt")); changed(); } catch (Exception e) { d.error(e); }
        });
    }

    void rename() {
        Path p = list.getSelectedValue();
        if (p == null) return;
        d.prompt("Переименовать", "Новое имя:", p.getFileName().toString(), n -> {
            try { Files.move(p, p.resolveSibling(n)); changed(); } catch (Exception e) { d.error(e); }
        });
    }

    void delete() {
        List<Path> sel = list.getSelectedValuesList();
        if (sel.isEmpty()) return;
        Runnable go = () -> {
            try { for (Path p : sel) Vfs.toTrash(p); } catch (IOException e) { d.error(e); }
            changed();
        };
        if (cur.startsWith(Vfs.trash()))
            d.ask("Удаление", "Удалить безвозвратно (" + sel.size() + " шт.)?", new String[] { "Да", "Нет" }, i -> { if (i == 0) go.run(); });
        else go.run();
    }

    Path clipboard;
    boolean cut;

    void copy(boolean isCut) {
        clipboard = list.getSelectedValue();
        cut = isCut;
    }

    void paste() {
        if (clipboard == null || !Files.exists(clipboard)) return;
        try {
            Path to = Vfs.unique(cur, clipboard.getFileName().toString());
            if (cut) { Files.move(clipboard, to); clipboard = null; }
            else Vfs.copyAll(clipboard, to);
            changed();
        } catch (IOException e) { d.error(e); }
    }

    void emptyTrash() {
        d.ask("Корзина", "Удалить все объекты из корзины безвозвратно?", new String[] { "Да", "Нет" }, i -> {
            if (i != 0) return;
            try { for (Path p : Vfs.list(Vfs.trash())) Vfs.deleteAll(p); } catch (IOException e) { d.error(e); }
            changed();
        });
    }

    void properties() {
        Path p = list.getSelectedValue();
        if (p == null) p = cur;
        try {
            BasicFileAttributes a = Files.readAttributes(p, BasicFileAttributes.class);
            DateTimeFormatter f = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());
            String text = "Имя: " + Vfs.name(p) + "\nТип: " + Vfs.type(p)
                + "\nРасположение: " + (p.equals(Vfs.root()) ? "—" : Vfs.virt(p.getParent()))
                + "\nРазмер: " + Vfs.size(Vfs.sizeOf(p))
                + (Files.isDirectory(p) ? "\nСодержит: " + Vfs.list(p).size() + " объектов" : "")
                + "\nИзменён: " + f.format(a.lastModifiedTime().toInstant());
            d.message("Свойства: " + Vfs.name(p), text);
        } catch (IOException e) { d.error(e); }
    }

    JPopupMenu itemMenu() {
        JPopupMenu m = new JPopupMenu();
        m.add(item("Открыть", this::openSel));
        Path p = list.getSelectedValue();
        if (p != null && !Files.isDirectory(p)) {
            m.add(item("Открыть в Блокноте", () -> d.open("notepad", p)));
            if (Vfs.isImage(p)) m.add(item("Изменить в Paint", () -> d.open("paint", p)));
        }
        m.addSeparator();
        m.add(item("Вырезать", () -> copy(true)));
        m.add(item("Копировать", () -> copy(false)));
        m.addSeparator();
        m.add(item("Переименовать", this::rename));
        m.add(item(cur.startsWith(Vfs.trash()) ? "Удалить навсегда" : "Удалить", this::delete));
        m.addSeparator();
        m.add(item("Свойства", this::properties));
        return m;
    }

    JPopupMenu bgMenu() {
        JPopupMenu m = new JPopupMenu();
        m.add(item("Обновить", this::reload));
        if (clipboard != null) m.add(item("Вставить", this::paste));
        m.addSeparator();
        m.add(item("Создать папку", this::newFolder));
        m.add(item("Создать текстовый документ", this::newText));
        m.addSeparator();
        m.add(item("Открыть в терминале", () -> d.open("cmd", cur)));
        m.add(item("Свойства", () -> { list.clearSelection(); properties(); }));
        return m;
    }

    // ---------------------------------------------------------------- ячейка: значок и подпись

    class Cell extends JComponent implements ListCellRenderer<Path> {
        Path p;
        boolean sel;

        public Component getListCellRendererComponent(JList<? extends Path> l, Path v, int i, boolean s, boolean f) {
            p = v;
            sel = s;
            return this;
        }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            UI.aa(g);
            int w = getWidth(), h = getHeight();
            if (sel) {
                g.setColor(Theme.selection());
                g.fillRoundRect(3, 2, w - 6, h - 4, 8, 8);
            }
            Icons.draw(g, Vfs.kind(p), (w - 44) / 2, 8, 44);
            g.setFont(Theme.font(Font.PLAIN, 12f));
            g.setColor(Theme.text());
            FontMetrics fm = g.getFontMetrics();
            String name = p.getFileName().toString();
            int y = 68;
            int maxW = w - 12;
            String l1 = name, l2 = null;
            if (fm.stringWidth(name) > maxW) {
                int cut = name.length();
                while (cut > 1 && fm.stringWidth(name.substring(0, cut)) > maxW) cut--;
                int sp = name.lastIndexOf(' ', cut);
                if (sp > 0) cut = sp;
                l1 = name.substring(0, cut);
                l2 = name.substring(cut).trim();
                while (fm.stringWidth(l2) > maxW && l2.length() > 1) l2 = l2.substring(0, l2.length() - 2) + "…";
            }
            g.drawString(l1, (w - fm.stringWidth(l1)) / 2, y);
            if (l2 != null) g.drawString(l2, (w - fm.stringWidth(l2)) / 2, y + fm.getHeight() - 1);
            g.dispose();
        }
    }

    @Override public Dimension getPreferredSize() { return new Dimension(900, 540); }
}
