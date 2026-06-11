# DIMM — Estado de la migración a Linux

## Objetivo
Hacer que DIMM (Eclipse 3.3 RCP + plugins SRI) funcione con Java 21 en Linux (GTK).

## Estado actual (11 Jun 2026) ✅
- **DIMM arranca** con Java 21 + Equinox launcher
- **Menú ATS** funciona (Herramientas ATS visible y operativo)
- **Gestión de plugins** funcional (instalar/desinstalar vía Programa, Agregar/Desinstalar)
- **Autocorrección de BREE** en dimm.sh — remueve automáticamente EEs incompatibles (CDC, Foundation, J2SE) al arrancar
- **Autoextracción de feature JARs** en dimm.sh — extrae `features/*.jar` a directorios para que `getPluginFiles()` pueda leer `feature.xml`
- **Autolimpieza de plugins huérfanos** en dimm.sh — al arrancar, elimina plugins en `plugins/` que no pertenecen a ningún feature instalado según `platform.xml`
- **FeatureXmlReader** — clase auxiliar que lee `feature.xml` desde directorio o desde JAR (respaldo), usada por la autolimpieza de dimm.sh
- **GitHub**: https://github.com/cristobalmontenegro/Dimm-Linux
- **ADI e ICE** instalados y funcionando
- **Validación ATS funcional** ✅ — `ValidarATSHandler.execute()` parcheado vía ASM: la primera llamada a `HandlerUtil.getActiveWorkbenchWindow(event)` guarda el `IWorkbenchWindow` en la variable local 12 (mediante `DUP; ASTORE 12` antes de `getShell()`). La segunda y tercera llamadas (que ocurren tras cerrar el wizard modal y retornan `null`) se reemplazan por `ALOAD 12`, reusando la referencia capturada al inicio. Esto elimina el `NullPointerException: Cannot invoke "IWorkbenchWindow.getActivePage()" because "window" is null`.
- **"Instalación por Internet" completada y probada** ✅:
  - `buttonPressed()` parcheado vía ASM: salta `BusyIndicator` y llama directamente a `InternetDownloader.downloadAndInstall()`
  - `InternetDownloader` extrae la lista de `https://www.sri.gob.ec/formularios-e-instructivos1` vía `PluginListFetcher`
  - Muestra lista seleccionable de plugins con nombre + versión extraída de la URL
  - Descarga el zip a `descargas/` (dentro del dir de la app), extrae a `temp/`, y abre el instalador vía `UpdateManagerUI.openInstaller`
  - Bugs corregidos: (i) NullPointerException por pasar `null` Throwable a `desplegarError` → ahora siempre pasa `Exception`; (ii) conflicto con `unzipFile` que borraba `temp/` donde estaba el zip descargado → ahora se descarga a `descargas/`
- **Etiquetas contextuales** ✅ — `createDialogArea()` parcheado vía ASM: cuando `nuevaExtension=true`, los radio buttons dicen "Instalación por Internet" / "Instalación por archivo". Cuando `nuevaExtension=false`, mantienen "Actualización por Internet" / "Actualización por archivo".
- **Spring context multi-plugin**: `createContext()` reescrito vía ASM para usar `SpringContextHelper.findAllContextFiles()`
  - Escanea `plugins/*/applicationContext*.xml` y retorna paths `file:` absolutos
  - `ClassPathXmlApplicationContext` procesa URLs `file:` como `UrlResource`, evitando el classloader OSGi
  - TCCL setea al classloader del principal (`BeanFactory.class`)
  - `Eclipse-RegisterBuddy: ec.gov.sri.dimm.principal` agregado a RDEP, DP y Comun → el principal puede ver sus clases vía buddy policy
- **SpringContextHelper** creado y agregado a `ec.gov.sri.dimm.principal.util`
- **Talón Resumen funcional** ✅ — `EditorTalonATS` parcheado vía ASM: reemplaza el widget `Browser` por `StyledText` con fuente monospace. Muestra tablas HTML formateadas como texto con columnas alineadas, guiones bajo encabezados y títulos de sección como texto plano. El botón "Imprimir" queda como no-op.
- **GenericBrowserPatcher** ✅ — Escanea TODOS los JARs en `plugins/` al arrancar y reemplaza `Browser` por `StyledText` + `TalonFormatter` en cualquier clase que use Browser. Cubre plugins nuevos o existentes que generen talones/resúmenes HTML.

## Problema original de Spring
- Los plugins RDEP, DP y Comun requieren Spring context con beans propios + beans del principal (dataSource, sessionFactory, abstract-tx-bean)
- El classloader del principal (`ec.gov.sri.dimm.principal`) no puede ver clases de RDEP/DP directamente
- Solución: `file:` URLs en Spring + `Eclipse-RegisterBuddy` + TCCL = classloader del principal
- El buddy policy permite al principal delegar carga de clases a RDEP/DP/Comun

## Cómo se logró el menú ATS

### Problema original
Los plugins ATS requieren `org.eclipse.nebula.widgets.cdatetime` (widget de calendario), que a su vez requiere databinding 1.4.0+. Esos JARs no vienen con DIMM; el instalador original los descargaba del update site Eclipse (`http://descargas.sri.gov.ec/dimm/updates`), pero el formato update site de Eclipse nunca funcionó bien (ni siquiera en Windows). El servidor SRI SÍ está activo en `descargas.sri.gob.ec` y provee todos los zips. En esta versión Linux se implementó descarga directa de zips individuales en vez del protocolo update site. Además, los JARs tienen `Bundle-RequiredExecutionEnvironment` con valores antiguos (CDC-1.1/Foundation-1.1, J2SE-1.4, J2SE-1.5, JavaSE-1.6) que Equinox 3.3 no resuelve en Java 21.

### Solución: 3 pasos

#### 1. JARs faltantes
Los JARs de databinding 1.4.0+ y nebula cdatetime se copiaron manualmente a `plugins/` desde `temp/plugins/` (descargados del update site SRI antes de que el formato site.xml dejara de estar disponible).

#### 2. BREE eliminado
Se quitó `Bundle-RequiredExecutionEnvironment` de 12 JARs problemáticos:
- `ec.gob.sri.dimm.api_1.9.3.jar` (JavaSE-1.6)
- `ec.gob.sri.dimm.ats.modelo_1.2.0.jar` (JavaSE-1.6)
- `ec.gob.sri.dimm.ats.ui_1.2.0.jar` (JavaSE-1.6)
- `ec.gob.sri.dimm.ats.validacion_1.2.0.jar` (JavaSE-1.6)
- `ec.gob.sri.dimm.data_1.18.0.jar` (JavaSE-1.6)
- `org.eclipse.core.databinding_1.4.0.*.jar` (CDC-1.1/Foundation-1.1, J2SE-1.4)
- `org.eclipse.core.databinding.beans_1.2.100*.jar` (J2SE-1.4)
- `org.eclipse.core.databinding.observable_1.4.0*.jar` (CDC-1.1/Foundation-1.1, J2SE-1.4)
- `org.eclipse.core.databinding.property_1.4.0*.jar` (CDC-1.1/Foundation-1.1, J2SE-1.4)
- `org.eclipse.jface.databinding_1.5.0*.jar` (CDC-1.0/Foundation-1.0, J2SE-1.3)
- `org.eclipse.nebula.cwt_0.9.0.HEAD.jar` (J2SE-1.5)
- `org.eclipse.nebula.widgets.cdatetime_0.14.0.HEAD.jar` (J2SE-1.5)

#### 3. Auto-fix en dimm.sh
Se agregó un script Python al inicio de `dimm.sh` que escanea todos los JARs y directorios en `plugins/`, detecta cualquier BREE con valor que no sea `JavaSE-*` o `OSGi/Minimum-*`, y lo remueve automáticamente. Esto asegura que cualquier plugin nuevo (instalado desde el SRI o futuras actualizaciones) quede compatible sin intervención manual.

### Otros cambios
- **dimm.sh**: Equinox launcher, `osgi.java.profile=JavaSE-21.profile`, `osgi.parentClassloader=app`, `--add-opens` para módulos Java 21.
- **JavaSE-21.profile**: `executionenvironment` incluye JavaSE-1.6 hasta JavaSE-21 + todas las EEs antiguas.
- **config.ini**: `osgi.bootdelegation=java.sql,javax.sql`.
- **"Desinstalar Programas"**: bytecode ASM en `ApplicationActionBarAdvisor` + `RemoveExtensionAction.class` agregados al principal vía compilación en línea.
- **Plugin management**: los plugins SRI se instalan desde el SRI vía "Instalación por Internet".
- **Eclipse-RegisterBuddy**: agregado a RDEP, DP y Comun para que el principal pueda cargar sus clases vía buddy policy.
- **SpringContextHelper**: clase helper en `ec.gov.sri.dimm.principal.util` que escanea `plugins/` y retorna paths `file:` absolutos.
- **FeatureXmlReader**: clase helper en `ec.gov.sri.dimm.principal.util` que lee `feature.xml` desde un directorio o un JAR.
- **Autoextracción de feature JARs**: script Python en `dimm.sh` que extrae `features/*.jar` a directorios (elimina el JAR) para que `getPluginFiles()` pueda leer `feature.xml`.
- **Autolimpieza de plugins huérfanos**: script Python en `dimm.sh` que al arrancar elimina de `plugins/` todo bundle OSGi que no esté referenciado por ningún feature en `platform.xml` (excepto lista blanca de core). También limpia entradas obsoletas del registro.
- **Auto-patch ValidarATSHandler**: `dimm.sh` ejecuta `PatchValidarATSHandler` (vía ASM) sobre `ec.gob.sri.dimm.ats.ui_1.2.0.jar` al arrancar. Si el JAR ya está parcheado (detecta bytes `DUP; ASTORE 12`), lo salta. Esto asegura que tras reinstalar el plugin ATS desde el SRI, el fix se aplique automáticamente.
- **Auto-patch EditorTalonATS**: `dimm.sh` ejecuta `PatchEditorTalonATS` (vía ASM) sobre el mismo JAR al arrancar. Reemplaza `Browser` por `StyledText` en `EditorTalonATS`, inyecta llamada a `TalonFormatter.setMonospaceFont()` (font monospace vía reflection) y vacía el `widgetSelected` de la clase interna `$1`. El parche se salta si ya está aplicado (detecta `setMonospaceFont` en el classfile).

## Problemas conocidos
1. **SWT/GTK crash con ciertos temas** — usar `GTK2_RC_FILES=Raleigh` o `GDK_BACKEND=x11`.
2. **Split-package commons-collections** ✅ resuelto — se quitó `lib/commons-collections.jar` del classpath JVM y se removió export de hibernate.
3. **createContext() no restaura TCCL al salir** — después de crear el contexto, el TCCL queda como el classloader del principal. No debería causar problemas pero es mejorable.
4. **UninstallDialog.getPluginFiles() no lee feature.xml desde JARs** ⚠️ — si el feature se instaló como `features/*.jar` (no directorio), `new File(featureDir, "feature.xml").exists()` falla y retorna lista vacía. Solución parcial: dimm.sh extrae JARs a directorios al arrancar, y el auto-cleanup elimina huérfanos. Para el caso install→uninstall en misma sesión (sin restart), el cleanup corre al siguiente arranque.
5. **UninstallDialog.deleteDirectory() parcheado** ✅ — reemplaza `File.delete()` que falla en directorios no vacíos por `deleteDirectory()` recursivo.
6. **SpringContextHelper$1.class faltante** ✅ resuelto — al compilar `SpringContextHelper.java` (que usa un `FilenameFilter` anónimo), no se copió `SpringContextHelper$1.class` al plugin. Causaba `ClassNotFoundException` al intentar crear RUC o cualquier wizard que cargue el Spring context. Se recompiló y desplegaron ambos class files.
7. ~~**Pestaña Talón Resumen no muestra contenido**~~ ✅ — `Browser` de SWT 3.3 requiere Mozilla XULRunner o WebKitGTK 1.0 (obsoletos). Se reemplazó por `StyledText` vía ASM con fuente monospace (`Monospace`). `TalonFormatter` procesa tablas HTML como pipe-separado, decodifica entidades y remueve tags. El botón "Imprimir" queda deshabilitado (no-op).

## Tareas pendientes (prioridad del usuario)
1. ~~**Validación ATS**~~ ✅ — `ValidarATSHandler` parcheado vía ASM para guardar y reusar `IWorkbenchWindow` (ver sección "Estado actual").
2. ~~**Probar flujo completo "Instalación por Internet"**~~ ✅ Probado y funciona: abrir DIMM → Programa → Agregar Nuevos Programas → OK → seleccionar ATS de la lista → descargar → instalar.
3. **Probar flujo completo RDEP**: instalar → restart → desinstalar → restart → verificar que RDEP desaparezca (plugins + menú).
4. **Probar los otros plugins SRI**: ACA, AFIC, ANR, ABT, APS, MID, OPRE, REOC, ValidadorConsola.
5. **Verificar labels contextuales**: `nuevaExtension=true` debe decir "Instalación por Internet/archivo", `nuevaExtension=false` debe decir "Actualización por Internet/archivo".
6. ~~**Probar Talón Resumen**~~ ✅ — Tablas visibles con columnas pipe-separadas y fuente monospace.
7. **Probar GenericBrowserPatcher con otros plugins**: instalar RDEP, ICE, ADI, etc. y verificar que sus talones/resúmenes se muestren correctamente con StyledText + TalonFormatter (sin Browser).
8. **Probar desinstalar/reinstalar ATS con GenericBrowserPatcher**: verificar que tras reinstalar ATS desde el SRI, el generic patcher lo parchee automáticamente (el ats.ui JAR original tiene Browser, el generic patcher debe reemplazarlo).

## Archivos relevantes
- `dimm.sh`: lanzador + autocorrección BREE + autoextracción features + autolimpieza huérfanos + flags Java 21
- `JavaSE-21.profile`: perfil OSGi
- `plugins/ec.gov.sri.dimm.principal_1.0.1/ec/gov/sri/dimm/principal/factory/BeanFactory.class`: `createContext()` parcheado vía ASM
- `plugins/ec.gov.sri.dimm.principal_1.0.1/ec/gov/sri/dimm/principal/util/SpringContextHelper.class`: helper que escanea `plugins/*/applicationContext*.xml`
- `plugins/ec.gov.sri.dimm.principal_1.0.1/ec/gov/sri/dimm/principal/util/SpringContextHelper\$1.class`: FilenameFilter anónimo (inline, no olvidar copiar ambos al compilar)
- `plugins/ec.gov.sri.dimm.principal_1.0.1/ec/gov/sri/dimm/principal/util/FeatureXmlReader.class`: helper que lee `feature.xml` desde dir o JAR
- `plugins/ec.gov.sri.dimm.principal_1.0.1/ec/gov/sri/dimm/principal/formas/UninstallDialog.class`: parcheado — `File.delete()` → `deleteDirectory()` recursivo
- `plugins/ec.gov.sri.dimm.principal_1.0.1/ec/gov/sri/dimm/principal/formas/InternetDownloader.class`: helper que descarga zip desde URL, lo extrae y lo instala
- `plugins/ec.gov.sri.dimm.principal_1.0.1/ec/gov/sri/dimm/principal/formas/InternetDownloader$PluginSelectionDialog.class`: diálogo de selección de plugins con versión mostrada
- `plugins/ec.gov.sri.dimm.principal_1.0.1/ec/gov/sri/dimm/principal/formas/PluginListFetcher.class`: fetcher que extrae lista del SRI y extrae versión desde URL
- `plugins/ec.gov.sri.dimm.principal_1.0.1/ec/gov/sri/dimm/principal/formas/ActualizacionDialog$4.class`: parcheado vía ASM — llama a `InternetDownloader.downloadAndInstall()` en vez de usar update site URL
- `src/ec/gov/sri/dimm/principal/formas/InternetDownloader.java`: fuente de InternetDownloader
- `src/ec/gov/sri/dimm/principal/formas/PluginListFetcher.java`: fuente de PluginListFetcher
- `plugins/ec.gov.sri.dimm.rdep_3.12.0/META-INF/MANIFEST.MF`: `Eclipse-RegisterBuddy`
- `plugins/ec.gov.sri.dimm.dp_1.0.2/META-INF/MANIFEST.MF`: `Eclipse-RegisterBuddy`
- `plugins/ec.gov.sri.dimm.comun_1.0.1/META-INF/MANIFEST.MF`: `Eclipse-RegisterBuddy`
- `PatchValidarATSHandler.java`: script ASM para parchear `ValidarATSHandler` — guarda el `IWorkbenchWindow` en var 12 tras la primera llamada y reemplaza las llamadas subsiguientes por `ALOAD 12`. Incluye soporte para parchear JARs directamente.
- `PatchValidarATSHandler.class`, `PatchValidarATSHandler$1.class`, `PatchValidarATSHandler$Patcher.class`: clases pre-compiladas para el auto-patch desde `dimm.sh`.
- `PatchEditorTalonATS.java`: script ASM para parchear `EditorTalonATS` — reemplaza `Browser` por `StyledText`, inyecta `setMonospaceFont()` (font Monospace via reflection), agrega `readFileContent(File)` sintético que delega a `TalonFormatter`. Incluye soporte para parchear JARs directamente.
- `PatchEditorTalonATS.class`, `PatchEditorTalonATS$1.class`, `PatchEditorTalonATS$2.class`, `PatchEditorTalonATS$2$1.class`, `PatchEditorTalonATS$Access0Patcher.class`, `PatchEditorTalonATS$CreatePartControlPatcher.class`, `PatchEditorTalonATS$SetFocusPatcher.class`: clases pre-compiladas para el auto-patch desde `dimm.sh`.
- `TalonFormatter.java`: fuente de la clase utilitaria para formateo del talón resumen.
- `TalonFormatter.class`, `ec/gob/sri/dimm/ats/ui/editores/TalonFormatter.class`: clases compiladas, inyectadas en el JAR vía auto-patch.
- `GenericBrowserPatcher.java`: patcher ASM genérico que escanea todos los JARs en `plugins/` y reemplaza `Browser` por `StyledText` + `TalonFormatter` en cualquier clase que referencie `org.eclipse.swt.browser.Browser`. Detecta y parchea automáticamente `setUrl`, `add*Listener`, `setFocus`, `back`, `forward`, `refresh`, `getUrl`, etc. Inyecta `TalonFormatter.class` y métodos `readFileContent(String)` y `readFileContent(File)` sintéticos en cada JAR parcheado.
- `GenericBrowserPatcher.class`, `GenericBrowserPatcher$ClassPatcher.class`, `GenericBrowserPatcher$MethodPatcher.class`, `GenericBrowserPatcher$SetFocusPatcher.class`: clases pre-compiladas para el auto-patch desde `dimm.sh`.
- `asm.jar`: biblioteca ASM 9 para parches de bytecode.
- `/tmp/dimm-debug.log`: stderr de JVM
