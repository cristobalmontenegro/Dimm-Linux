#!/bin/bash
export GDK_BACKEND=x11
DIR=$(cd "$(dirname "$0")" && pwd)
cd "$DIR"

# Auto-fix: remove incompatible Bundle-RequiredExecutionEnvironment from all plugins
python3 - "$DIR" << 'PYEOF'
import zipfile, io, re, os, sys

def strip_bree(jar_data):
    """Remove BREE (only keep OSGi/Minimum)"""
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
            # Only keep OSGi/Minimum (spec level), strip everything else
            if bree.startswith('OSGi/Minimum-'):
                return None  # Keep OSGi spec level
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
                print(f'  Fixed BREE: {f}')
    # Directory-based plugins (MANIFEST.MF)
    for d in dirs:
        mf_path = os.path.join(root, d, 'META-INF', 'MANIFEST.MF')
        if os.path.exists(mf_path):
            with open(mf_path) as fh:
                content = fh.read()
            lines = content.replace('\r\n', '\n').replace('\r', '\n')
            unfolded = re.sub(r'\n ', '', lines)
            m = re.search(r'Bundle-RequiredExecutionEnvironment:\s*(.*?)(?:\n\w+|$)', unfolded, re.DOTALL)
            if m and not m.group(1).strip().startswith('OSGi/Minimum-'):
                new_content = re.sub(r'\n?Bundle-RequiredExecutionEnvironment:[^\n]*(?:\n\s[^\n]*)*\n?', '\n', lines).strip() + '\n'
                with open(mf_path, 'w') as fh:
                    fh.write(new_content)
                print(f'  Fixed BREE: {d}/')
                count += 1

if count > 0:
    print(f'Stripped BREE from {count} plugin(s)')

# Extract feature JARs to directories (so getPluginFiles can read feature.xml)
features_dir = sys.argv[1] + '/features'
if os.path.isdir(features_dir):
    for f in os.listdir(features_dir):
        if f.endswith('.jar'):
            jar_path = os.path.join(features_dir, f)
            dir_path = jar_path[:-4]
            if not os.path.exists(dir_path):
                os.makedirs(dir_path, exist_ok=True)
                with zipfile.ZipFile(jar_path, 'r') as z:
                    z.extractall(dir_path)
                os.remove(jar_path)
                print(f'  Extracted feature: {f}')

# Remove orphaned plugin dirs that don't belong to any installed feature
def get_feature_plugins(install_dir):
    """Parse platform.xml and return set of expected plugin file names"""
    import xml.etree.ElementTree as ET
    expected = set()
    platform = os.path.join(install_dir, 'configuration', 'org.eclipse.update', 'platform.xml')
    if not os.path.exists(platform):
        return expected
    try:
        tree = ET.parse(platform)
        for feature in tree.findall('.//feature'):
            furl = feature.get('url', '')
            fdir = os.path.join(install_dir, furl.replace('file:', '').lstrip('/'))
            if not os.path.isdir(fdir):
                continue
            fxml = os.path.join(fdir, 'feature.xml')
            if not os.path.exists(fxml):
                continue
            try:
                ft = ET.parse(fxml)
                for pl in ft.findall('.//plugin'):
                    pid = pl.get('id')
                    pver = pl.get('version')
                    if pid and pver:
                        expected.add(f'{pid}_{pver}')
            except Exception:
                pass
    except Exception:
        pass
    return expected

def has_manifest(plugin_path):
    """Check if a dir has META-INF/MANIFEST.MF (OSGi bundle)"""
    mf = os.path.join(plugin_path, 'META-INF', 'MANIFEST.MF')
    return os.path.isfile(mf)

plugins_dir = sys.argv[1] + '/plugins'
expected_plugins = get_feature_plugins(sys.argv[1])
if expected_plugins:
    # Core whitelist: always keep these
    core_prefixes = (
        'org.eclipse.', 'javax.', 'com.ibm.',
        'org.apache.', 'org.osgi.', 'org.w3c.',
        'ec.gov.sri.dimm.core_', 'ec.gov.sri.dimm.principal_',
        'ec.gov.sri.dimm.hibernate_', 'ec.gov.sri.dimm.spring_',
        'ec.gov.sri.dimm.ayuda_',
    )
    deleted = 0
    for entry in os.listdir(plugins_dir):
        plugin_path = os.path.join(plugins_dir, entry)
        entry_name = entry
        if entry_name.endswith('.jar'):
            entry_name = entry_name[:-4]  # strip .jar for comparison
        elif entry_name.endswith('/'):
            entry_name = entry_name[:-1]
        # Check: is it an expected plugin, or a core/whitelisted entry?
        if entry_name in expected_plugins:
            continue
        if entry.startswith(core_prefixes):
            continue
        # Check if this is actually a plugin dir/jar with a manifest
        is_plugin = entry.endswith('.jar') or has_manifest(plugin_path)
        if not is_plugin:
            continue  # skip non-plugin files
        # Delete orphan
        try:
            if os.path.isfile(plugin_path):
                os.remove(plugin_path)
            elif os.path.isdir(plugin_path):
                import shutil
                shutil.rmtree(plugin_path)
            print(f'  Removed orphan plugin: {entry}')
            deleted += 1
        except Exception as e:
            print(f'  Error removing {entry}: {e}')
    if deleted > 0:
        print(f'Removed {deleted} orphan plugin(s)')

# Clean stale entries from the update registry
registry_path = os.path.join(sys.argv[1], 'configuration', 'org.eclipse.update', 'registry')
if os.path.exists(registry_path):
    try:
        with open(registry_path) as fh:
            lines = fh.readlines()
        kept = [l for l in lines if not l.startswith('#') and l.strip()]
        # Get feature IDs from platform.xml
        platform_path = os.path.join(sys.argv[1], 'configuration', 'org.eclipse.update', 'platform.xml')
        feature_ids = set()
        if os.path.exists(platform_path):
            try:
                import xml.etree.ElementTree as ET
                tree = ET.parse(platform_path)
                for feat in tree.findall('.//feature'):
                    fid = feat.get('id')
                    if fid:
                        feature_ids.add(fid)
            except Exception:
                pass
        # Filter registry: keep only lines referencing existing features or plugins from those features
        new_lines = []
        feature_plugins = get_feature_plugins(sys.argv[1])
        for line in kept:
            is_expected = False
            for eid in feature_ids:
                if eid in line:
                    is_expected = True
                    break
            if not is_expected:
                # Check if it's a plugin referenced by an installed feature
                parts = line.split('=')
                plugin_key = parts[0].split('_', 1)[-1] if len(parts) > 0 else ''
                for ep in feature_plugins:
                    if ep in line:
                        is_expected = True
                        break
            if is_expected or line.startswith('plugin_org.eclipse.') or line.startswith('plugin_javax.') or line.startswith('plugin_ec.gov.sri.dimm.core') or line.startswith('plugin_ec.gov.sri.dimm.principal') or line.startswith('plugin_ec.gov.sri.dimm.spring') or line.startswith('plugin_ec.gov.sri.dimm.hibernate') or line.startswith('plugin_ec.gov.sri.dimm.data') or line.startswith('plugin_ec.gov.sri.dimm.api') or line.startswith('plugin_com.ibm.'):
                new_lines.append(line)
        with open(registry_path, 'w') as fh:
            fh.writelines(new_lines)
    except Exception as e:
        print(f'  Registry cleanup error: {e}')
PYEOF

# Auto-patch ValidarATSHandler for ATS validation (re-use IWorkbenchWindow reference)
python3 - "$DIR" << 'PYEOF'
import zipfile, io, os, subprocess, sys
plugins_dir = sys.argv[1] + '/plugins'
jar_name = 'ec.gob.sri.dimm.ats.ui_1.2.0.jar'
jar_path = os.path.join(plugins_dir, jar_name)
if os.path.exists(jar_path):
    asm_jar = os.path.join(sys.argv[1], 'asm.jar')
    cp = asm_jar + ':' + sys.argv[1]
    result = subprocess.run(
        ['java', '-cp', cp, 'PatchValidarATSHandler', jar_path],
        capture_output=True, text=True, timeout=30)
    if result.stdout:
        for line in result.stdout.strip().split('\n'):
            print(f'  {line}')
    if result.stderr:
        for line in result.stderr.strip().split('\n'):
            if line:
                print(f'  [patcher] {line}')
PYEOF

# Auto-patch EditorTalonATS: replace Browser with StyledText for Talon Resumen tab
python3 - "$DIR" << 'PYEOF'
import zipfile, io, os, subprocess, sys
plugins_dir = sys.argv[1] + '/plugins'
jar_name = 'ec.gob.sri.dimm.ats.ui_1.2.0.jar'
jar_path = os.path.join(plugins_dir, jar_name)
if os.path.exists(jar_path):
    asm_jar = os.path.join(sys.argv[1], 'asm.jar')
    swt_jar = os.path.join(plugins_dir, 'org.eclipse.swt.gtk.linux.x86_64_3.3.0.v3346.jar')
    root = sys.argv[1]
    formatter_src = os.path.join(root, 'TalonFormatter.java')
    if os.path.exists(formatter_src) and os.path.exists(swt_jar):
        r = subprocess.run(['javac', '-cp', swt_jar, '-d', root, formatter_src],
            capture_output=True, text=True, timeout=30)
        if r.returncode == 0:
            print('  Compiled TalonFormatter.java')
        elif r.stderr:
            for line in r.stderr.strip().split('\n'):
                if line: print(f'  [compile] {line}')
    cp = asm_jar + ':' + root
    result = subprocess.run(
        ['java', '-cp', cp, 'PatchEditorTalonATS', jar_path],
        capture_output=True, text=True, timeout=30)
    if result.stdout:
        for line in result.stdout.strip().split('\n'):
            print(f'  {line}')
    if result.stderr:
        for line in result.stderr.strip().split('\n'):
            if line:
                print(f'  [patcher] {line}')
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
  --add-opens java.desktop/java.beans=ALL-UNNAMED \
  --add-opens java.base/java.lang.invoke=ALL-UNNAMED \
  -cp "plugins/org.eclipse.equinox.launcher_1.0.0.v20070606.jar" \
  org.eclipse.equinox.launcher.Main \
  "$@" 2>/tmp/dimm-debug.log
