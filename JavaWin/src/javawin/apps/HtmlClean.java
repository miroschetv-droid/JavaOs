package javawin.apps;

import java.net.URI;
import java.util.*;

// Превращает современную страницу в простой HTML, который умеет показывать Swing (HTML 3.2):
// выкидывает скрипты, стили, svg и скрытые блоки, оставляет текст, ссылки, картинки, списки,
// таблицы и формы, а относительные адреса делает абсолютными.
final class HtmlClean {
    static final Set<String> VOID = Set.of("br", "hr", "img", "input", "meta", "link", "area", "base", "col",
        "embed", "param", "source", "track", "wbr", "keygen");
    // содержимое этих элементов не показываем вообще
    static final Set<String> DROP = Set.of("script", "style", "svg", "math", "template", "iframe", "object",
        "canvas", "video", "audio", "picture", "dialog", "button");
    // оставляем как есть
    static final Set<String> KEEP = Set.of("a", "p", "br", "hr", "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol",
        "li", "dl", "dt", "dd", "b", "strong", "i", "em", "u", "s", "strike", "code", "pre", "blockquote",
        "table", "tr", "td", "th", "caption", "img", "div", "span", "sub", "sup", "small", "big", "center",
        "form", "input", "select", "option", "textarea", "tt", "kbd", "samp", "var", "cite", "address");
    // переименовываем в похожие простые
    static final Map<String, String> MAP = Map.ofEntries(
        Map.entry("section", "div"), Map.entry("article", "div"), Map.entry("main", "div"),
        Map.entry("header", "div"), Map.entry("footer", "div"), Map.entry("nav", "div"),
        Map.entry("aside", "div"), Map.entry("figure", "div"), Map.entry("figcaption", "div"),
        Map.entry("summary", "div"), Map.entry("details", "div"), Map.entry("label", "span"),
        Map.entry("mark", "b"), Map.entry("ins", "u"), Map.entry("del", "s"), Map.entry("abbr", "span"),
        Map.entry("time", "span"), Map.entry("nobr", "span"), Map.entry("font", "span"),
        Map.entry("thead", ""), Map.entry("tbody", ""), Map.entry("tfoot", ""), Map.entry("noscript", ""),
        Map.entry("html", ""), Map.entry("body", ""), Map.entry("head", ""), Map.entry("fieldset", "div"), Map.entry("legend", "b"));
    static final Set<String> ATTRS = Set.of("href", "src", "alt", "colspan", "rowspan", "name", "value", "type",
        "action", "method", "checked", "selected", "size", "maxlength", "rows", "cols", "start", "id",
        "width", "height", "align", "multiple");

    final String src;
    URI base;
    String title;
    int pos;
    final StringBuilder out = new StringBuilder();

    record Open(String name, String mapped, boolean hidden) {}
    final Deque<Open> stack = new ArrayDeque<>();
    int hidden, dropDepth;
    // «режим чтения»: если на странице есть <main>, показываем только его (меню и шапки — мимо)
    boolean mainOnly;
    int mainDepth;
    String dropTag;

    HtmlClean(String src, String url) {
        this.src = src;
        try { base = URI.create(url); } catch (Exception e) { base = null; }
    }

    String clean() {
        int n = src.length();
        mainOnly = indexOfIgnoreCase(src, "<main", 0) >= 0;
        while (pos < n) {
            int lt = src.indexOf('<', pos);
            if (lt < 0) { textOut(src.substring(pos)); break; }
            if (lt > pos) textOut(src.substring(pos, lt));
            pos = lt;
            if (src.startsWith("<!--", pos)) {
                int e = src.indexOf("-->", pos + 4);
                pos = e < 0 ? n : e + 3;
            } else if (src.startsWith("<!", pos) || src.startsWith("<?", pos)) {
                int e = src.indexOf('>', pos);
                pos = e < 0 ? n : e + 1;
            } else if (pos + 1 < n && (Character.isLetter(src.charAt(pos + 1)) || src.charAt(pos + 1) == '/')) {
                tag();
            } else {
                textOut("<");
                pos++;
            }
        }
        while (!stack.isEmpty()) closeTop();
        return out.toString();
    }

    void textOut(String raw) {
        if (dropDepth > 0 || hidden > 0 || (mainOnly && mainDepth == 0)) return;
        out.append(text(decode(raw)));
    }

    void tag() {
        boolean end = src.charAt(pos + 1) == '/';
        int i = pos + (end ? 2 : 1), n = src.length();
        int s = i;
        while (i < n && !Character.isWhitespace(src.charAt(i)) && src.charAt(i) != '>' && src.charAt(i) != '/') i++;
        String name = src.substring(s, i).toLowerCase(Locale.ROOT);
        Map<String, String> attrs = new LinkedHashMap<>();
        boolean selfClose = false;
        // атрибуты
        while (i < n && src.charAt(i) != '>') {
            char c = src.charAt(i);
            if (Character.isWhitespace(c)) { i++; continue; }
            if (c == '/') { selfClose = true; i++; continue; }
            int as = i;
            while (i < n && !Character.isWhitespace(src.charAt(i)) && src.charAt(i) != '=' && src.charAt(i) != '>' && src.charAt(i) != '/') i++;
            String an = src.substring(as, i).toLowerCase(Locale.ROOT);
            while (i < n && Character.isWhitespace(src.charAt(i))) i++;
            String av = "";
            if (i < n && src.charAt(i) == '=') {
                i++;
                while (i < n && Character.isWhitespace(src.charAt(i))) i++;
                if (i < n && (src.charAt(i) == '"' || src.charAt(i) == '\'')) {
                    char q = src.charAt(i);
                    int e = src.indexOf(q, i + 1);
                    if (e < 0) e = n;
                    av = src.substring(i + 1, e);
                    i = Math.min(n, e + 1);
                } else {
                    int vs = i;
                    while (i < n && !Character.isWhitespace(src.charAt(i)) && src.charAt(i) != '>') i++;
                    av = src.substring(vs, i);
                }
            }
            if (!an.isEmpty()) attrs.putIfAbsent(an, decode(av));
        }
        pos = Math.min(n, i + 1);

        if (end) { endTag(name); return; }

        // <title>, <script>, <style>, <textarea> — их содержимое не разбирается как разметка
        if (name.equals("title") || name.equals("script") || name.equals("style") || name.equals("textarea")) {
            int e = indexOfIgnoreCase(src, "</" + name, pos);
            String inner = src.substring(pos, e < 0 ? n : e);
            pos = e < 0 ? n : Math.max(pos, src.indexOf('>', e) + 1);
            if (pos <= 0) pos = n;
            if (name.equals("title") && title == null) title = decode(inner).replaceAll("\\s+", " ");
            if (name.equals("textarea") && dropDepth == 0 && hidden == 0)
                out.append("<textarea").append(attrsOut(name, attrs)).append(">").append(text(decode(inner))).append("</textarea>");
            return;
        }

        if (name.equals("base") && attrs.containsKey("href")) {
            URI b = abs(attrs.get("href"));
            if (b != null) base = b;
        }

        if (dropDepth > 0) {
            if (name.equals(dropTag) && !VOID.contains(name) && !selfClose) dropDepth++;
            return;
        }
        if (DROP.contains(name)) {
            if (!VOID.contains(name) && !selfClose) { dropTag = name; dropDepth = 1; }
            return;
        }

        boolean isHidden = attrs.containsKey("hidden") || "true".equals(attrs.get("aria-hidden"))
            || (attrs.getOrDefault("style", "").replace(" ", "").toLowerCase(Locale.ROOT).contains("display:none"))
            || "hidden".equals(attrs.get("type")) || "navigation".equals(attrs.get("role"))
            || serviceClass(attrs.getOrDefault("class", ""));
        String mapped = KEEP.contains(name) ? name : MAP.getOrDefault(name, VOID.contains(name) ? null : "span");
        if (name.equals("button")) mapped = null;

        if (VOID.contains(name) || selfClose) {
            if (hidden > 0 || isHidden || (mainOnly && mainDepth == 0) || mapped == null || mapped.isEmpty()) return;
            if (name.equals("img")) {
                String srcAttr = attrs.getOrDefault("src", "");
                if (srcAttr.isEmpty() || srcAttr.startsWith("data:")) srcAttr = attrs.getOrDefault("data-src", "");
                if (srcAttr.isEmpty() || srcAttr.startsWith("data:")) {
                    String alt = attrs.getOrDefault("alt", "");
                    if (!alt.isBlank()) out.append("[").append(text(alt)).append("]");
                    return;
                }
                attrs.put("src", srcAttr);
            }
            if (name.equals("input") && attrs.getOrDefault("type", "").equalsIgnoreCase("submit") && attrs.getOrDefault("value", "").isBlank())
                attrs.put("value", attrs.getOrDefault("aria-label", "Отправить").isBlank() ? "Отправить" : attrs.getOrDefault("aria-label", "Отправить"));
            out.append('<').append(mapped).append(attrsOut(name, attrs)).append('>');
            return;
        }

        stack.push(new Open(name, mapped, isHidden));
        if (isHidden) hidden++;
        if (name.equals("main")) mainDepth++;
        if (hidden == 0 && (!mainOnly || mainDepth > 0) && mapped != null && !mapped.isEmpty())
            out.append('<').append(mapped).append(attrsOut(name, attrs)).append('>');
    }

    void endTag(String name) {
        if (dropDepth > 0) {
            if (name.equals(dropTag) && --dropDepth == 0) dropTag = null;
            return;
        }
        boolean found = false;
        for (Open o : stack) if (o.name.equals(name)) { found = true; break; }
        if (!found) return;
        while (!stack.isEmpty()) {
            boolean last = stack.peek().name.equals(name);
            closeTop();
            if (last) break;
        }
    }

    void closeTop() {
        Open o = stack.pop();
        if (hidden == 0 && (!mainOnly || mainDepth > 0) && o.mapped != null && !o.mapped.isEmpty()) out.append("</").append(o.mapped).append('>');
        if (o.hidden) hidden--;
        if (o.name.equals("main")) mainDepth--;
    }

    String attrsOut(String name, Map<String, String> attrs) {
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, String> e : attrs.entrySet()) {
            String k = e.getKey(), v = e.getValue();
            if (!ATTRS.contains(k)) continue;
            if (k.equals("href") || k.equals("src") || k.equals("action")) {
                if (k.equals("href") && v.startsWith("#")) { b.append(" href=\"").append(attr(v)).append('"'); continue; }
                URI u = abs(v);
                if (u == null || !(u.getScheme() != null && u.getScheme().startsWith("http"))) continue;
                v = u.toString();
            }
            if ((k.equals("width") || k.equals("height")) && name.equals("img")) {
                try { if (Integer.parseInt(v.replace("px", "").trim()) > 900) continue; } catch (Exception ex) { continue; }
            }
            if (k.equals("type") && name.equals("input") && v.equalsIgnoreCase("search")) v = "text";
            b.append(' ').append(k).append("=\"").append(attr(v)).append('"');
        }
        if (name.equals("form") && !attrs.containsKey("action") && base != null)
            b.append(" action=\"").append(attr(base.toString())).append('"');
        return b.toString();
    }

    URI abs(String v) {
        try {
            v = v.trim().replace(" ", "%20");
            if (v.startsWith("//")) v = (base != null ? base.getScheme() : "https") + ":" + v;
            URI u = URI.create(v);
            return base != null ? base.resolve(u) : u;
        } catch (Exception e) { return null; }
    }

    // служебные блоки, которые в «режиме чтения» только мешают: выпадающие меню, навигационные
    // таблицы, «править», элементы «не для печати»
    static boolean serviceClass(String cls) {
        String c = cls.toLowerCase(Locale.ROOT);
        return c.contains("dropdown") || c.contains("noprint") || c.contains("navbox") || c.contains("mw-editsection")
            || c.contains("vector-menu") || c.contains("sr-only") || c.contains("visually-hidden");
    }

    static int indexOfIgnoreCase(String s, String what, int from) {
        int n = s.length(), m = what.length();
        for (int i = from; i + m <= n; i++) if (s.regionMatches(true, i, what, 0, m)) return i;
        return -1;
    }

    // ---------------------------------------------------------------- сущности и экранирование

    static final Map<String, String> ENT = Map.ofEntries(
        Map.entry("amp", "&"), Map.entry("lt", "<"), Map.entry("gt", ">"), Map.entry("quot", "\""),
        Map.entry("apos", "'"), Map.entry("nbsp", " "), Map.entry("mdash", "—"), Map.entry("ndash", "–"),
        Map.entry("hellip", "…"), Map.entry("laquo", "«"), Map.entry("raquo", "»"), Map.entry("copy", "©"),
        Map.entry("reg", "®"), Map.entry("trade", "™"), Map.entry("middot", "·"), Map.entry("bull", "•"),
        Map.entry("rsquo", "’"), Map.entry("lsquo", "‘"), Map.entry("rdquo", "”"), Map.entry("ldquo", "“"),
        Map.entry("bdquo", "„"), Map.entry("times", "×"), Map.entry("deg", "°"), Map.entry("euro", "€"),
        Map.entry("larr", "←"), Map.entry("rarr", "→"), Map.entry("uarr", "↑"), Map.entry("darr", "↓"),
        Map.entry("shy", ""), Map.entry("zwj", ""), Map.entry("zwnj", ""), Map.entry("thinsp", " "),
        Map.entry("ensp", " "), Map.entry("emsp", " "), Map.entry("minus", "−"), Map.entry("plusmn", "±"),
        Map.entry("sect", "§"), Map.entry("para", "¶"), Map.entry("frac12", "½"), Map.entry("hearts", "♥"));

    static String decode(String s) {
        if (s.indexOf('&') < 0) return s;
        StringBuilder b = new StringBuilder(s.length());
        int i = 0, n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (c == '&') {
                int semi = s.indexOf(';', i);
                if (semi > i + 1 && semi - i <= 10) {
                    String e = s.substring(i + 1, semi);
                    String r = null;
                    try {
                        if (e.startsWith("#x") || e.startsWith("#X")) r = new String(Character.toChars(Integer.parseInt(e.substring(2), 16)));
                        else if (e.startsWith("#")) r = new String(Character.toChars(Integer.parseInt(e.substring(1))));
                        else r = ENT.get(e);
                    } catch (Exception ignored) {}
                    if (r != null) { b.append(r); i = semi + 1; continue; }
                }
            }
            b.append(c);
            i++;
        }
        return b.toString();
    }

    static String text(String s) {
        StringBuilder b = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&' -> b.append("&amp;");
                case '<' -> b.append("&lt;");
                case '>' -> b.append("&gt;");
                default -> {
                    // символы вне BMP (эмодзи) Swing не рисует — пропускаем
                    if (Character.isSurrogate(c)) continue;
                    b.append(c);
                }
            }
        }
        return b.toString();
    }

    static String attr(String s) { return text(s).replace("\"", "&quot;"); }
}
