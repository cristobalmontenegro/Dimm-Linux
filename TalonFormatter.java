package ec.gob.sri.dimm.ats.ui.editores;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.SWT;

public class TalonFormatter {
    public static void setMonospaceFont(Object text) { } // legacy stub
    public static void setMonospaceFontV2(Object text) {
        try {
            StyledText st = (StyledText) text;
            Display disp = st.getDisplay();
            FontData oldFd = st.getFont().getFontData()[0];
            int h = oldFd.getHeight();
            if (h < 6 || h > 48) h = 10;
            String[] names = {"Monospace", "Liberation Mono", "DejaVu Sans Mono",
                "Courier New", "Courier 10 Pitch", "Bitstream Vera Sans Mono",
                "Andale Mono", "FreeMono", "Luxi Mono", "DejaVu LGC Sans Mono",
                "Noto Mono", "Droid Sans Mono", "Ubuntu Mono", "Menlo",
                "Consolas", "Source Code Pro", "Monaco", "Inconsolata",
                "Fira Code", "JetBrains Mono"};
            for (String name : names) {
                try {
                    Font f = new Font(disp, name, h, SWT.NORMAL);
                    st.setFont(f);
                    return;
                } catch (Exception e2) { }
            }
        } catch (Exception e) {
            System.err.println("[TalonFormatter] setMonospaceFontV2 error: " + e);
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
                result.append('\n');
                result.append(formatTable(html.substring(ti, te)));
                result.append('\n');
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
        List<Integer> headerRows = new ArrayList<>();
        Matcher rm = Pattern.compile("(?i)<tr[^>]*>(.*?)</tr>").matcher(tableHtml);
        while (rm.find()) {
            String rc = rm.group(1);
            List<String> cells = new ArrayList<>();
            boolean hasTH = false;
            Matcher cm = Pattern.compile("(?i)<(t[hd])([^>]*)>(.*?)</\\1>").matcher(rc);
            while (cm.find()) {
                String tag = cm.group(1);
                String attrs = cm.group(2);
                String raw = cm.group(3).replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
                String decoded = decodeEntities(raw);
                if ("th".equalsIgnoreCase(tag)) hasTH = true;
                int colspan = 1;
                Matcher colM = Pattern.compile("(?i)colspan\\s*=\\s*\"?(\\d+)\"?").matcher(attrs);
                if (colM.find()) colspan = Integer.parseInt(colM.group(1));
                cells.add(decoded);
                for (int i = 1; i < colspan; i++) cells.add("");
            }
            if (!cells.isEmpty()) {
                rows.add(cells);
                if (hasTH) headerRows.add(rows.size() - 1);
            }
        }
        if (rows.isEmpty()) return processText(tableHtml);

        // Layout table (no <th>): simple text rows, no dashes
        if (headerRows.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (List<String> r : rows) {
                List<String> nonEmpty = new ArrayList<>();
                for (String v : r) if (!v.isEmpty()) nonEmpty.add(v);
                if (nonEmpty.isEmpty()) continue;
                for (int c = 0; c < nonEmpty.size(); c++) {
                    if (c > 0) sb.append("  ");
                    sb.append(nonEmpty.get(c));
                }
                sb.append('\n');
            }
            return sb.toString();
        }

        int maxCols = 0;
        for (List<String> r : rows) maxCols = Math.max(maxCols, r.size());

        // Filter section titles out of headerRows, keep only real column headers
        List<Integer> realHeaders = new ArrayList<>();
        for (int idx : headerRows) {
            List<String> r = rows.get(idx);
            int nonEmpty = 0;
            for (String v : r) if (!v.isEmpty()) nonEmpty++;
            if (nonEmpty >= 2 || r.size() < maxCols) realHeaders.add(idx);
        }

        int headerRow = realHeaders.isEmpty() ? 0 : realHeaders.get(0);

        int[] widths = new int[maxCols];
        for (int ri = 0; ri < rows.size(); ri++) {
            List<String> r = rows.get(ri);
            int nonEmpty = 0;
            for (String v : r) if (!v.isEmpty()) nonEmpty++;
            boolean isSection = nonEmpty <= 1 && r.size() == maxCols;
            if (isSection) continue;
            for (int c = 0; c < r.size(); c++) {
                widths[c] = Math.max(widths[c], r.get(c).length());
            }
        }

        boolean[] numCol = new boolean[maxCols];
        Arrays.fill(numCol, true);
        for (int ri = 0; ri < rows.size(); ri++) {
            if (ri == headerRow) continue;
            List<String> r = rows.get(ri);
            for (int c = 0; c < r.size(); c++) {
                String v = r.get(c);
                if (!v.isEmpty() && !v.matches("[\\d.,\\-]+")) numCol[c] = false;
            }
        }

        StringBuilder sb = new StringBuilder();
        for (int ri = 0; ri < rows.size(); ri++) {
            List<String> r = rows.get(ri);

            // Section title rows (colspan covering all cols, 1 non-empty cell): plain text
            int nonEmpty = 0;
            for (String v : r) if (!v.isEmpty()) nonEmpty++;
            if (nonEmpty <= 1 && r.size() == maxCols) {
                for (String v : r) if (!v.isEmpty()) { sb.append(v); break; }
                sb.append('\n');
                continue;
            }

            for (int c = 0; c < maxCols; c++) {
                if (c > 0) sb.append("  ");
                String val = c < r.size() ? r.get(c) : "";
                if (numCol[c]) {
                    for (int p = val.length(); p < widths[c]; p++) sb.append(' ');
                    sb.append(val);
                } else {
                    sb.append(val);
                    for (int p = val.length(); p < widths[c]; p++) sb.append(' ');
                }
            }
            sb.append('\n');

            if (ri == headerRow) {
                for (int c = 0; c < maxCols; c++) {
                    if (c > 0) sb.append("  ");
                    for (int p = 0; p < widths[c]; p++) sb.append('-');
                }
                sb.append('\n');
            }
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
