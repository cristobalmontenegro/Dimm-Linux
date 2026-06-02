# DIMM - DIMM-Linux Migration State

## Goal
Hacer que DIMM (Eclipse 3.3 RCP + plugins SRI) funcione con Java 21 en Linux (GTK).

## Current State (Jun 1 2026) ✅
- **DIMM arranca** con Java 21 + Equinox launcher
- **Menú ATS** funciona (Herramientas ATS visible y operativo)
- **Plugin management** funcional (instalar/desinstalar vía Programa, Agregar/Desinstalar)
- **Auto-fix de BREE** en dimm.sh — remueve automáticamente EEs incompatibles (CDC, Foundation, J2SE) al arrancar
- **GitHub**: https://github.com/cristobalmontenegro/Dimm-Linux

## Cómo se logró el menú ATS

### Problema original
Los plugins ATS requieren `org.eclipse.nebula.widgets.cdatetime` (widget de calendario), que a su vez requiere databinding 1.4.0+. Esos JARs no vienen con DIMM; el instalador los descarga del update site SRI, pero el servidor SRI ya no existe. Además, los JARs tienen `Bundle-RequiredExecutionEnvironment` con valores antiguos (CDC-1.1/Foundation-1.1, J2SE-1.4, J2SE-1.5, JavaSE-1.6) que Equinox 3.3 no resuelve en Java 21.

### Solución: 3 pasos

#### 1. JARs faltantes
Los JARs de databinding 1.4.0+ y nebula cdatetime se obtuvieron del update site de ATS (`temp/plugins/`, ya descargados antes de que el servidor cayera). Se copiaron a `plugins/` para que Equinox los vea.

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
Se agregó un script Python al inicio de `dimm.sh` que escanea todos los JARs y directorios en `plugins/`, detecta cualquier BREE con valor que no sea `JavaSE-*` o `OSGi/Minimum-*`, y lo remueve automáticamente. Esto asegura que cualquier plugin nuevo (instalado desde PLUGINSSRI/ o futuras actualizaciones) quede compatible sin intervención manual.

### Otros cambios
- **dimm.sh**: Equinox launcher, `osgi.java.profile=JavaSE-21.profile`, `osgi.parentClassloader=app`, `--add-opens` para módulos Java 21, `commons-collections.jar` en classpath.
- **JavaSE-21.profile**: `executionenvironment` incluye JavaSE-1.6 hasta JavaSE-21 + todas las EEs antiguas.
- **config.ini**: `osgi.bootdelegation=java.sql,javax.sql`.
- **"Desinstalar Programas"**: bytecode ASM en `ApplicationActionBarAdvisor` + `RemoveExtensionAction.class` agregados al principal vía compilación inline.
- **Plugin management**: PLUGINSSRI/ contiene 17 plugins SRI.

## Known Issues
1. **SWT/GTK crash con ciertos temas** — usar `GTK2_RC_FILES=Raleigh` o `GDK_BACKEND=x11`.
2. **Actualización por Internet** — servidor SRI caído (legacy, no arreglable).
3. **Spring/hibernate exportan commons-collections** — posible split-package con `lib/commons-collections.jar`.
