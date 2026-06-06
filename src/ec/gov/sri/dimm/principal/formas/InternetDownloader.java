package ec.gov.sri.dimm.principal.formas;

import java.io.*;
import java.net.URL;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.update.ui.UpdateJob;
import org.eclipse.update.ui.UpdateManagerUI;
import org.eclipse.update.search.UpdateSearchRequest;

import ec.gov.sri.dimm.principal.util.Utils;
import ec.gov.sri.dimm.core.DimmCoreUtils;
import ec.gov.sri.dimm.core.DimmCoreUtilsCommon;

public class InternetDownloader {

    public static void downloadAndInstall(ActualizacionDialog dialog) {
        Shell shell = dialog.getShell();
        InputDialog dlg = new InputDialog(shell,
            "Descargar desde Internet",
            "Ingrese la URL del archivo ZIP del plugin a instalar:",
            "https://descargas.sri.gob.ec/download/anexos/...",
            null);
        if (dlg.open() != Window.OK) return;
        String urlStr = dlg.getValue();
        if (urlStr == null || urlStr.trim().isEmpty()) return;

        try {
            String dirPath = Utils.getProperty("dir.path");
            String tempDir = dirPath + "/temp";
            new File(tempDir).mkdirs();

            URL url = new URL(urlStr.trim());
            String path = url.getPath();
            String fileName = path.substring(path.lastIndexOf('/') + 1);
            if (fileName.isEmpty()) fileName = "plugin.zip";
            String zipPath = tempDir + "/" + fileName;

            BufferedInputStream in = new BufferedInputStream(url.openStream());
            BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(zipPath));
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            in.close();
            out.close();

            String extractPath = DimmCoreUtilsCommon.unzipFile(zipPath, dirPath);
            if (extractPath == null || extractPath.isEmpty()) {
                DimmCoreUtils.desplegarError("Error al extraer el archivo ZIP", null);
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
                DimmCoreUtils.desplegarError("Error al preparar la instalacion", null);
                return;
            }

            UpdateJob job = new UpdateJob(jobName, request);
            UpdateManagerUI.openInstaller(shell, job);

        } catch (Exception e) {
            DimmCoreUtils.desplegarError("Error al descargar el plugin: " + e.getMessage(), e);
        }
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
