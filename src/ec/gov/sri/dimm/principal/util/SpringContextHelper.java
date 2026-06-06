package ec.gov.sri.dimm.principal.util;

import java.io.*;
import java.util.*;

public class SpringContextHelper {

    public static String[] findAllContextFiles() {
        String userDir = System.getProperty("user.dir");
        if (userDir == null) userDir = ".";
        File pluginsDir = new File(userDir, "plugins");
        if (!pluginsDir.isDirectory()) {
            return new String[] { "applicationContext.xml" };
        }

        List<String> archivos = new ArrayList<>();
        File[] dirs = pluginsDir.listFiles();
        if (dirs == null) return new String[0];

        for (File dir : dirs) {
            if (!dir.isDirectory()) continue;
            File[] contextFiles = dir.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir2, String name) {
                    return name.startsWith("applicationContext") && name.endsWith(".xml");
                }
            });
            if (contextFiles == null) continue;
            for (File cf : contextFiles) {
                archivos.add("file:" + cf.getAbsolutePath());
            }
        }

        if (archivos.isEmpty()) {
            File fallback = new File(pluginsDir,
                "ec.gov.sri.dimm.principal_1.0.1/applicationContext.xml");
            if (fallback.exists()) {
                archivos.add("file:" + fallback.getAbsolutePath());
            } else {
                archivos.add("applicationContext.xml");
            }
        }

        return archivos.toArray(new String[0]);
    }
}
