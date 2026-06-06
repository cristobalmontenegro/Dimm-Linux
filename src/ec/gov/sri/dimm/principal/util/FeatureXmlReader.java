package ec.gov.sri.dimm.principal.util;

import java.io.*;
import java.util.jar.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;

public class FeatureXmlReader {

    public static Document readFeatureXml(File featureDir) {
        if (featureDir == null) return null;
        try {
            File xml = new File(featureDir, "feature.xml");
            if (xml.exists()) {
                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                DocumentBuilder db = dbf.newDocumentBuilder();
                return db.parse(xml);
            }
            if (featureDir.isFile() && featureDir.getName().endsWith(".jar")) {
                JarFile jar = new JarFile(featureDir);
                try {
                    JarEntry entry = jar.getJarEntry("feature.xml");
                    if (entry != null) {
                        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                        DocumentBuilder db = dbf.newDocumentBuilder();
                        return db.parse(jar.getInputStream(entry));
                    }
                } finally {
                    jar.close();
                }
            }
        } catch (Exception e) {
        }
        return null;
    }
}
