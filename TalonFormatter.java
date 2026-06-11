package ec.gob.sri.dimm.ats.ui.editores;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public class TalonFormatter {
    public static void setMonospaceFont(Object text) {
        try {
            Class<?> st = text.getClass();
            Object font = st.getMethod("getFont").invoke(text);
            Object[] fd = (Object[]) font.getClass().getMethod("getFontData").invoke(font);
            fd[0].getClass().getMethod("setName", String.class).invoke(fd[0], "Monospace");
            Object disp = st.getMethod("getDisplay").invoke(text);
            Object f = Class.forName("org.eclipse.swt.graphics.Font")
                .getConstructor(Class.forName("org.eclipse.swt.widgets.Display"),
                    Class.forName("org.eclipse.swt.graphics.FontData"))
                .newInstance(disp, fd[0]);
            st.getMethod("setFont", Class.forName("org.eclipse.swt.graphics.Font")).invoke(text, f);
        } catch (Exception e) {
            // ignore
        }
    }

    public static String readFileContent(File file) {
        try {
            String html = new String(Files.readAllBytes(file.toPath()));
            StringBuilder result = new StringBuilder();
            int pos = 0;
            while (true) {
                int ti = html.indexOf("<table", pos);
                if (ti < 0) break;
                result.append(processText(html.substring(pos, ti)));
                int te = html.indexOf("</table>", ti);
                if (te < 0) { result.append(processText(html.substring(ti))); pos = html.length(); break; }
                te += "</table>".length();
                result.append(formatTable(html.substring(ti, te)));
                pos = te;
            }
            result.append(processText(html.substring(pos)));
            return result.toString().trim();
        } catch (Exception e) {
            return "Error al cargar el contenido del tal\u00F3n resumen.";
        }
    }

    static String processText(String t) {
        t = t.replaceAll("(?i)</?(p|div|br|li|h[1-6]|thead|tbody|tfoot|caption|dl|dt|dd|ol|ul|section|article|nav|header|footer|aside|details|summary|figure|figcaption)[^>]*>", "\n");
        t = t.replaceAll("<[^>]+>", " ");
        t = decodeEntities(t);
        t = t.replaceAll("[ \\t]+", " ");
        String[] lines = t.split("\n");
        StringBuilder sb = new StringBuilder(t.length());
        for (String r : lines) {
            String line = r.trim();
            if (!line.isEmpty()) sb.append(line).append('\n');
        }
        return sb.toString();
    }

    static String formatTable(String tableHtml) {
        List<List<String>> rows = new ArrayList<>();
        Matcher rm = Pattern.compile("(?i)<tr[^>]*>(.*?)</tr>").matcher(tableHtml);
        while (rm.find()) {
            String rc = rm.group(1);
            List<String> cells = new ArrayList<>();
            Matcher cm = Pattern.compile("(?i)<t[hd][^>]*>(.*?)</t[hd]>").matcher(rc);
            while (cm.find()) {
                String raw = cm.group(1).replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
                String decoded = decodeEntities(raw);
                if (!decoded.isEmpty()) cells.add(decoded);
            }
            if (!cells.isEmpty()) rows.add(cells);
        }
        if (rows.isEmpty()) return processText(tableHtml);

        StringBuilder sb = new StringBuilder();
        for (List<String> r : rows) {
            for (int c = 0; c < r.size(); c++) {
                if (c > 0) sb.append(" | ");
                sb.append(r.get(c));
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private static String decodeEntities(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        int i = 0;
        while (i < s.length()) {
            int amp = s.indexOf('&', i);
            if (amp < 0) { sb.append(s.substring(i)); break; }
            int semi = s.indexOf(';', amp);
            if (semi < 0) { sb.append(s.substring(i)); break; }
            sb.append(s.substring(i, amp));
            String ent = s.substring(amp + 1, semi);
            switch (ent) {
                case "amp":    sb.append('&'); break;
                case "lt":     sb.append('<'); break;
                case "gt":     sb.append('>'); break;
                case "quot":   sb.append('"'); break;
                case "apos":   sb.append('\''); break;
                case "nbsp":   sb.append(' '); break;
                case "Oacute": sb.append('\u00D3'); break;
                case "oacute": sb.append('\u00F3'); break;
                case "Iacute": sb.append('\u00CD'); break;
                case "iacute": sb.append('\u00ED'); break;
                case "Eacute": sb.append('\u00C9'); break;
                case "eacute": sb.append('\u00E9'); break;
                case "Aacute": sb.append('\u00C1'); break;
                case "aacute": sb.append('\u00E1'); break;
                case "Uacute": sb.append('\u00DA'); break;
                case "uacute": sb.append('\u00FA'); break;
                case "Ntilde": sb.append('\u00D1'); break;
                case "ntilde": sb.append('\u00F1'); break;
                default: sb.append('&').append(ent).append(';'); break;
            }
            i = semi + 1;
        }
        return sb.toString();
    }

    public static void main(String[] args) throws Exception {
        System.out.println(readFileContent(new File(args[0])));
    }
}
