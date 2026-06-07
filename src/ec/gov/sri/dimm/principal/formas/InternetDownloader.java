package ec.gov.sri.dimm.principal.formas;

import java.io.*;
import java.net.URL;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;
import org.eclipse.update.ui.UpdateJob;
import org.eclipse.update.ui.UpdateManagerUI;
import org.eclipse.update.search.UpdateSearchRequest;

import ec.gov.sri.dimm.principal.util.Utils;
import ec.gov.sri.dimm.core.DimmCoreUtils;
import ec.gov.sri.dimm.core.DimmCoreUtilsCommon;

public class InternetDownloader {

    public static void downloadAndInstall(ActualizacionDialog dialog) {
        Shell shell = dialog.getShell();

        // 1. Fetch plugin list from SRI
        List<PluginListFetcher.PluginEntry> plugins;
        try {
            plugins = PluginListFetcher.fetchPlugins();
        } catch (Exception e) {
            String detail = (e.getMessage() != null) ? e.getMessage() : e.toString();
            DimmCoreUtils.desplegarError("Error al obtener lista de plugins: " + detail, e);
            return;
        }

        if (plugins.isEmpty()) {
            DimmCoreUtils.desplegarError("No se encontraron plugins disponibles en la p\u00e1gina del SRI", new RuntimeException("lista vac\u00eda"));
            return;
        }

        // 2. Show selection dialog
        PluginSelectionDialog dlg = new PluginSelectionDialog(shell, plugins);
        if (dlg.open() != Dialog.OK) return;
        String urlStr = dlg.getSelectedUrl();
        if (urlStr == null || urlStr.trim().isEmpty()) return;

        // 3. Download and install
        try {
            String dirPath = Utils.getProperty("dir.path");
            String downloadDir = dirPath + "/descargas";
            new File(downloadDir).mkdirs();

            URL url = new URL(urlStr.trim());
            String path = url.getPath();
            String fileName = path.substring(path.lastIndexOf('/') + 1);
            if (fileName.isEmpty()) fileName = "plugin.zip";
            String zipPath = downloadDir + "/" + fileName;

            byte[] data = readAllBytes(url.openStream());
            writeAllBytes(zipPath, data);

            String extractPath = DimmCoreUtilsCommon.unzipFile(zipPath, dirPath);
            if (extractPath == null || extractPath.isEmpty()) {
                DimmCoreUtils.desplegarError("Error al extraer el archivo ZIP", new RuntimeException("extracci\u00f3n fall\u00f3"));
                return;
            }

            String filePrefix = "file:";
            if (!extractPath.startsWith("/")) filePrefix = "file://";
            String fileUrl = filePrefix + extractPath;

            boolean nuevaExtension = getNuevaExtension(dialog);
            UpdateSearchRequest request;
            String jobName;

            if (nuevaExtension) {
                request = callGetSearchRequest(dialog, fileUrl);
                jobName = "Buscar nuevas extensiones";
            } else {
                request = callGetUpdateRequest(dialog, fileUrl);
                jobName = "Buscando actualizaciones...";
            }

            if (request == null) {
                DimmCoreUtils.desplegarError("Error al preparar la instalacion", new RuntimeException("request null"));
                return;
            }

            UpdateJob job = new UpdateJob(jobName, request);
            UpdateManagerUI.openInstaller(shell, job);

        } catch (Exception e) {
            String detail = (e.getMessage() != null) ? e.getMessage() : e.toString();
            DimmCoreUtils.desplegarError("Error al descargar el plugin: " + detail, e);
        }
    }

    // --- Plugin selection dialog ---
    static class PluginSelectionDialog extends Dialog {
        private List<PluginListFetcher.PluginEntry> entries;
        private String selectedUrl;
        private org.eclipse.swt.widgets.List list;

        PluginSelectionDialog(Shell shell, List<PluginListFetcher.PluginEntry> entries) {
            super(shell);
            this.entries = entries;
        }

        String getSelectedUrl() { return selectedUrl; }

        @Override
        protected void configureShell(Shell newShell) {
            super.configureShell(newShell);
            newShell.setText("Seleccionar plugin a instalar");
        }

        @Override
        protected Control createDialogArea(Composite parent) {
            Composite container = (Composite) super.createDialogArea(parent);
            container.setLayout(new GridLayout(1, false));

            Label label = new Label(container, SWT.NONE);
            label.setText("Seleccione el plugin que desea descargar e instalar:");

            list = new org.eclipse.swt.widgets.List(container,
                SWT.BORDER | SWT.V_SCROLL | SWT.SINGLE | SWT.H_SCROLL);
            GridData gd = new GridData(SWT.FILL, SWT.FILL, true, true);
            gd.heightHint = 280;
            gd.widthHint = 480;
            list.setLayoutData(gd);

            for (PluginListFetcher.PluginEntry entry : entries) {
                String display = entry.version.isEmpty()
                    ? entry.name
                    : entry.name + "  v" + entry.version;
                list.add(display);
            }
            if (entries.size() > 0) list.select(0);

            list.addListener(SWT.DefaultSelection, e -> okPressed());

            return container;
        }

        @Override
        protected void okPressed() {
            int idx = list.getSelectionIndex();
            if (idx >= 0) {
                selectedUrl = entries.get(idx).url;
            }
            super.okPressed();
        }
    }

    // --- helpers ---
    private static byte[] readAllBytes(InputStream in) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) baos.write(buf, 0, n);
        in.close();
        return baos.toByteArray();
    }

    private static void writeAllBytes(String path, byte[] data) throws IOException {
        FileOutputStream out = new FileOutputStream(path);
        out.write(data);
        out.close();
    }

    private static boolean getNuevaExtension(ActualizacionDialog dialog) throws Exception {
        Field f = ActualizacionDialog.class.getDeclaredField("nuevaExtension");
        f.setAccessible(true);
        return f.getBoolean(dialog);
    }

    private static UpdateSearchRequest callGetSearchRequest(ActualizacionDialog dialog, String url) throws Exception {
        Method m = ActualizacionDialog.class.getDeclaredMethod("getSearchRequest", String.class);
        m.setAccessible(true);
        return (UpdateSearchRequest) m.invoke(dialog, url);
    }

    private static UpdateSearchRequest callGetUpdateRequest(ActualizacionDialog dialog, String url) throws Exception {
        Method m = ActualizacionDialog.class.getDeclaredMethod("getUpdateRequest", String.class);
        m.setAccessible(true);
        return (UpdateSearchRequest) m.invoke(dialog, url);
    }
}
