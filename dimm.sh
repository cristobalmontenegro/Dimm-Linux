#!/bin/bash
export GDK_BACKEND=x11
DIR=$(cd "$(dirname "$0")" && pwd)
cd "$DIR"

# Auto-fix: remove incompatible Bundle-RequiredExecutionEnvironment from all plugins
python3 - "$DIR" << 'PYEOF'
import zipfile, io, re, os, sys

def strip_bree(jar_data):
    """Remove BREE if it contains CDC, Foundation, or J2SE (incompatible with Java 21)"""
    try:
        with zipfile.ZipFile(io.BytesIO(jar_data), 'r') as z:
            if 'META-INF/MANIFEST.MF' not in z.namelist():
                return None
            mf = z.read('META-INF/MANIFEST.MF').decode('utf-8')
            lines = mf.replace('\r\n', '\n').replace('\r', '\n')
            unfolded = re.sub(r'\n ', '', lines)
            m = re.search(r'Bundle-RequiredExecutionEnvironment:\s*(.*?)(?:\n\w+|$)', unfolded, re.DOTALL)
            if not m:
                return None  # No BREE
            bree = m.group(1).strip()
            # Only strip if not JavaSE or OSGi/Minimum
            if bree.startswith('JavaSE-') or bree.startswith('OSGi/Minimum-'):
                return None  # Compatible, skip
            # Strip BREE
            new_mf = re.sub(r'\n?Bundle-RequiredExecutionEnvironment:[^\n]*(?:\n\s[^\n]*)*\n?', '\n', lines)
            new_mf = new_mf.strip() + '\n'
            # Rebuild JAR without signature files
            out = io.BytesIO()
            with zipfile.ZipFile(io.BytesIO(jar_data), 'r') as zin:
                with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED) as zout:
                    for item in zin.infolist():
                        fn = item.filename
                        if fn == 'META-INF/MANIFEST.MF':
                            continue
                        if fn.startswith('META-INF/') and fn.endswith(('.SF', '.RSA', '.DSA')):
                            continue
                        zout.writestr(item, zin.read(fn))
                    zout.writestr('META-INF/MANIFEST.MF', new_mf.encode('utf-8'))
            print(f'  Fixed BREE: {os.path.basename(fn)} ({bree})')
            return out.getvalue()
    except Exception as e:
        print(f'  Error processing JAR: {e}')
        return None

plugins_dir = sys.argv[1] + '/plugins'
count = 0
for root, dirs, files in os.walk(plugins_dir):
    for f in files:
        if f.endswith('.jar'):
            path = os.path.join(root, f)
            with open(path, 'rb') as fh:
                data = fh.read()
            result = strip_bree(data)
            if result is not None:
                with open(path, 'wb') as fh:
                    fh.write(result)
                count += 1
    # Directory-based plugins (MANIFEST.MF)
    for d in dirs:
        mf_path = os.path.join(root, d, 'META-INF', 'MANIFEST.MF')
        if os.path.exists(mf_path):
            with open(mf_path) as fh:
                content = fh.read()
            lines = content.replace('\r\n', '\n').replace('\r', '\n')
            unfolded = re.sub(r'\n ', '', lines)
            m = re.search(r'Bundle-RequiredExecutionEnvironment:\s*(.*?)(?:\n\w+|$)', unfolded, re.DOTALL)
            if m and not m.group(1).strip().startswith('JavaSE-') and not m.group(1).strip().startswith('OSGi/Minimum-'):
                new_content = re.sub(r'\n?Bundle-RequiredExecutionEnvironment:[^\n]*(?:\n\s[^\n]*)*\n?', '\n', lines).strip() + '\n'
                with open(mf_path, 'w') as fh:
                    fh.write(new_content)
                print(f'  Fixed BREE: {d}/')
                count += 1

if count > 0:
    print(f'Stripped BREE from {count} plugin(s)')
PYEOF

JAVA_PACKAGES="java.applet,java.awt,java.awt.color,java.awt.datatransfer,java.awt.dnd,java.awt.event,java.awt.font,java.awt.geom,java.awt.im,java.awt.im.spi,java.awt.image,java.awt.image.renderable,java.awt.print,java.beans,java.beans.beancontext,java.io,java.lang,java.lang.annotation,java.lang.instrument,java.lang.ref,java.lang.reflect,java.math,java.net,java.nio,java.nio.channels,java.nio.channels.spi,java.nio.charset,java.nio.charset.spi,java.nio.file,java.nio.file.attribute,java.nio.file.spi,java.rmi,java.rmi.activation,java.rmi.dgc,java.rmi.registry,java.rmi.server,java.security,java.security.acl,java.security.cert,java.security.interfaces,java.security.spec,java.sql,java.text,java.text.spi,java.util,java.util.concurrent,java.util.concurrent.atomic,java.util.concurrent.locks,java.util.jar,java.util.logging,java.util.prefs,java.util.regex,java.util.spi,java.util.zip,javax.accessibility,javax.activity,javax.annotation,javax.annotation.processing,javax.crypto,javax.crypto.interfaces,javax.crypto.spec,javax.imageio,javax.imageio.event,javax.imageio.metadata,javax.imageio.plugins.bmp,javax.imageio.plugins.jpeg,javax.imageio.plugins.tiff,javax.imageio.spi,javax.imageio.stream,javax.lang.model,javax.lang.model.element,javax.lang.model.type,javax.lang.model.util,javax.management,javax.management.loading,javax.management.modelmbean,javax.management.monitor,javax.management.openmbean,javax.management.relation,javax.management.remote,javax.management.remote.rmi,javax.management.timer,javax.naming,javax.naming.directory,javax.naming.event,javax.naming.ldap,javax.naming.spi,javax.net,javax.net.ssl,javax.print,javax.print.attribute,javax.print.attribute.standard,javax.print.event,javax.rmi,javax.rmi.CORBA,javax.rmi.ssl,javax.script,javax.security.auth,javax.security.auth.callback,javax.security.auth.kerberos,javax.security.auth.login,javax.security.auth.spi,javax.security.auth.x500,javax.security.cert,javax.security.sasl,javax.sound.midi,javax.sound.midi.spi,javax.sound.sampled,javax.sound.sampled.spi,javax.sql,javax.sql.rowset,javax.sql.rowset.serial,javax.sql.rowset.spi,javax.swing,javax.swing.border,javax.swing.colorchooser,javax.swing.event,javax.swing.filechooser,javax.swing.plaf,javax.swing.plaf.basic,javax.swing.plaf.metal,javax.swing.plaf.multi,javax.swing.plaf.synth,javax.swing.table,javax.swing.text,javax.swing.text.html,javax.swing.text.html.parser,javax.swing.text.rtf,javax.swing.tree,javax.swing.undo,javax.tools,javax.transaction,javax.transaction.xa,javax.xml,javax.xml.crypto,javax.xml.crypto.dom,javax.xml.crypto.dsig,javax.xml.crypto.dsig.keyinfo,javax.xml.crypto.dsig.spec,javax.xml.datatype,javax.xml.namespace,javax.xml.parsers,javax.xml.stream,javax.xml.stream.events,javax.xml.stream.util,javax.xml.transform,javax.xml.transform.dom,javax.xml.transform.sax,javax.xml.transform.stream,javax.xml.validation,javax.xml.xpath,org.ietf.jgss,org.omg.CORBA,org.omg.CORBA.DynAnyPackage,org.omg.CORBA.MARSHALPackage,org.omg.CORBA.ORBPackage,org.omg.CORBA.TypeCodePackage,org.omg.CORBA.portable,org.omg.CORBA_2_3,org.omg.CORBA_2_3.portable,org.omg.CosNaming,org.omg.CosNaming.NamingContextExtPackage,org.omg.CosNaming.NamingContextPackage,org.omg.Dynamic,org.omg.DynamicAny,org.omg.DynamicAny.DynAnyFactoryPackage,org.omg.DynamicAny.DynAnyPackage,org.omg.IOP,org.omg.IOP.CodecFactoryPackage,org.omg.IOP.CodecPackage,org.omg.Messaging,org.omg.PortableInterceptor,org.omg.PortableInterceptor.ORBInitInfoPackage,org.omg.PortableServer,org.omg.PortableServer.CurrentPackage,org.omg.PortableServer.POAManagerPackage,org.omg.PortableServer.POAPackage,org.omg.PortableServer.ServantLocatorPackage,org.omg.SendingContext,org.omg.stub.java.rmi,org.w3c.dom,org.w3c.dom.bootstrap,org.w3c.dom.css,org.w3c.dom.events,org.w3c.dom.html,org.w3c.dom.ls,org.w3c.dom.stylesheets,org.w3c.dom.traversal,org.w3c.dom.views,org.xml.sax,org.xml.sax.ext,org.xml.sax.helpers"

java \
  -Dosgi.ws=gtk \
  -Dosgi.install.area="$DIR" \
  -Dosgi.configuration.area="$DIR/configuration" \
  -Dosgi.java.profile=JavaSE-21.profile \
  -Djava.specification.version=1.6 \
  -Dorg.osgi.framework.system.packages="$JAVA_PACKAGES" \
  -Dosgi.parentClassloader=app \
  -Dorg.eclipse.update.reconcile=true \
  --add-opens java.base/java.lang=ALL-UNNAMED \
  --add-opens java.base/java.lang.reflect=ALL-UNNAMED \
  --add-opens java.base/java.io=ALL-UNNAMED \
  --add-opens java.base/java.util=ALL-UNNAMED \
  --add-opens java.base/java.net=ALL-UNNAMED \
  --add-opens java.base/java.text=ALL-UNNAMED \
  --add-opens java.base/sun.security.ssl=ALL-UNNAMED \
  --add-opens java.xml/com.sun.org.apache.xerces.internal.util=ALL-UNNAMED \
  --add-opens java.xml/com.sun.org.apache.xerces.internal.dom=ALL-UNNAMED \
  --add-opens java.xml/com.sun.org.apache.xerces.internal.parsers=ALL-UNNAMED \
  --add-opens java.xml/com.sun.org.apache.xml.internal.serialize=ALL-UNNAMED \
  --add-opens java.xml/jdk.xml.internal=ALL-UNNAMED \
  -cp "plugins/org.eclipse.equinox.launcher_1.0.0.v20070606.jar" \
  org.eclipse.equinox.launcher.Main \
  "$@"
