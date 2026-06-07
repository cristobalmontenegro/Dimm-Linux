package ec.gov.sri.dimm.principal.formas;

import java.io.*;
import java.net.URL;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PluginListFetcher {

    public static class PluginEntry {
        public final String name;
        public final String url;
        public final String version;
        public PluginEntry(String name, String url) {
            this.name = name;
            this.url = url;
            this.version = extractVersion(url);
        }
        private static String extractVersion(String url) {
            String fn = url.substring(url.lastIndexOf('/') + 1);
            if (fn.endsWith(".zip")) fn = fn.substring(0, fn.length() - 4);
            Pattern vp = Pattern.compile("(\\d+\\.\\d+(?:\\.\\d+)*)$");
            Matcher m = vp.matcher(fn);
            if (m.find()) return m.group(1);
            return "";
        }
    }

    public static List<PluginEntry> fetchPlugins() throws IOException {
        String html = fetchPage("https://www.sri.gob.ec/formularios-e-instructivos1");
        return parseHtml(html);
    }

    static String fetchPage(String urlStr) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (X11; Linux x86_64)");
        conn.setInstanceFollowRedirects(true);
        int code = conn.getResponseCode();
        if (code != 200) {
            throw new IOException("HTTP " + code + " al obtener " + urlStr);
        }
        String encoding = conn.getContentEncoding();
        if (encoding == null) encoding = "UTF-8";
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] tmp = new byte[8192];
        int n;
        InputStream in = conn.getInputStream();
        while ((n = in.read(tmp)) > 0) buf.write(tmp, 0, n);
        in.close();
        return buf.toString(encoding);
    }

    static List<PluginEntry> parseHtml(String html) {
        List<PluginEntry> result = new ArrayList<>();
        Pattern linkPattern = Pattern.compile(
            "<a\\s[^>]*href=\"(https://descargas\\.sri\\.gob\\.ec/download/anexos/[^\"]*\\.zip)\"[^>]*title=\"([^\"]*)\"[^>]*>",
            Pattern.CASE_INSENSITIVE);
        Matcher m = linkPattern.matcher(html);
        while (m.find()) {
            String url = m.group(1);
            String name = m.group(2);
            if (name.startsWith("Descargar ")) {
                name = name.substring(10);
            }
            result.add(new PluginEntry(name, url));
        }
        return result;
    }
}
