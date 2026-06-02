# DIMM Migration: State & Pending Tasks

## Última sesión: 31/05/2026

## Logros

- **DIMM arranca** con Java 21 usando Equinox launcher
- **RUC wizard funciona** (Archivo / Nuevo / RUC)
- **Herramientas ATS / Validar Anexo** abre ventana
- **Nuevo / Anexo Transaccional** funciona
- **Ingreso de facturas** funciona (con GTK2_RC_FILES=Raleigh)

## Cambios realizados

### `dimm.sh`

- Reemplazado launcher Java propio por Equinox + Main-Class
- Añadido `commons-collections.jar` al classpath JVM
- Añadido `-Dosgi.java.profile`
- Añadido `-Dosgi.parentClassloader=app` — permite acceso a java.sql module
- Añadidos `--add-opens` para módulos Java necesarios
- Eliminado `-Dosgi.bootdelegation` (ahora solo en config.ini)
- Añadido `GDK_BACKEND=x11` como variable de entorno

### `configuration/config.ini`

- `osgi.bootdelegation=java.sql,javax.sql` (sin sun.* ni com.sun.*)
- `org.osgi.framework.bootdelegation=java.sql,javax.sql`

### Plugins modificados

- **`ec.gov.sri.dimm.spring_1.0.1.jar`**: Eliminadas exportaciones de `org.apache.commons.collections.*` de Export-Package (para evitar split-package)
- **`ec.gov.sri.dimm.hibernate_1.0.1.jar`**: Ídem
- **`ec.gov.sri.dimm.jasperviewer_1.0.1/META-INF/MANIFEST.MF`**: Ídem
- **`ec.gov.sri.dimm.principal_1.0.1/META-INF/MANIFEST.MF`**: Añadido `Require-Bundle: org.apache.derby.core` (para que Spring cargue driver Derby)
- **`org.apache.derby.core_10.3.1.4/META-INF/MANIFEST.MF`**: Creado con `DynamicImport-Package: *` (no existía, necesario para java.sql)

## Problemas conocidos

### 1. SWT/GTK crash al ingresar facturas (SIGSEGV en ButtonDrawData.draw)

**Síntoma:** SIGSEGV en `libc.so.6` al renderizar botones Nebula CWT (`VButtonPainter.paintBackground`). Stack: `OS.memmove(GtkBorder) -> ButtonDrawData.draw -> Theme.drawBackground -> VButtonPainter.paintBackground`.

**Causa:** SWT 3.3 (2007) nativo (`libswt-pi-gtk-3346.so`) incompatible con GTK2 2.24.33 + tema Adwaita. El `memmove` para copiar `GtkBorder` desde memoria nativa crashea cuando el tema devuelve borders con layout distinto al esperado.

**Workaround actual:** `GTK2_RC_FILES=/usr/share/themes/Raleigh/gtk-2.0/gtkrc GDK_BACKEND=x11` — usar tema Raleigh (el más simple) + backend X11.

**Pendiente:** Si el crash persiste con otros datos/menús, considerar:
- Instalar `gtk2-engines-clearlooks` y usar tema Clearlooks (default de 2007)
- Reemplazar SWT 3.3 nativo por una versión más nueva (3.8+)
- Usar LD_PRELOAD para parchear `Java_org_eclipse_swt_internal_gtk_OS_memmove__Lorg_eclipse_swt_internal_gtk_GtkBorder_2JJ`

### 2. Actualización por Internet

**Síntoma:** Programa / Agregar Nuevos Programas / Actualización por Internet da error de red (`http://descargas.sri.gov.ec/dimm/updates` no existe).

**Causa:** El servidor SRI ya no existe. No se puede arreglar. Es un feature legacy.

## Tareas Pendientes

### A. Pruebas interactivas a fondo (ALTA PRIORIDAD)
El usuario debe probar exhaustivamente:
- [ ] **Programa / Desinstalar Programas** → debe abrir Configuration Manager
- [ ] RUC: crear contribuyente, guardar, exportar
- [ ] ATS: ingresar varias facturas con diferentes datos
- [ ] ATS Validar: probar con datos reales
- [ ] Anexo Transaccional: wizard completo
- [ ] Reportes / exportaciones
- Si algún menú/feature falla, reportar el error exacto

## Completado

### "Desinstalar Programas" en menú Programa
- Creado `RemoveExtensionAction.java` → abre `ConfigurationManagerWindow(Shell)` (el gestor de configuración de Eclipse Update UI)
- Compilado y copiado a `ec.gov.sri.dimm.principal_1.0.1/ec/gov/sri/dimm/principal/actions/`
- Modificado `ApplicationActionBarAdvisor.class` vía ASM 9.6: añadido field `removeExtensionAction`, creado en `makeActions()`, añadido al menú "Programa" en `fillMenuBar()` (después de "Agregar Nuevos Programas")
- No requiere cambios en `plugin.xml` ni `MANIFEST.MF`

### Limpieza para GitHub
- Eliminados: `hs_err_pid*.log`, `derby.log`, `configuration/*.log`, `workspace/`, `DimmDb/` (backup en `/tmp/DimmDb-backup/`)
- Temp files eliminados

## Resumen de Dependencias OSGi

- `ec.gov.sri.dimm.principal` → Require-Bundle: `org.eclipse.update.ui`, `org.eclipse.update.configurator`, `org.eclipse.update.core`, `ec.gov.sri.dimm.hibernate`, `ec.gov.sri.dimm.spring`, `ec.gov.sri.dimm.core`, `org.apache.derby.core`
- `ec.gov.sri.dimm.spring` → Require-Bundle: `ec.gov.sri.dimm.hibernate`, `org.apache.derby.core`
- `ec.gov.sri.dimm.hibernate` → Bundle-ClassPath con hibernate3.jar (no tiene Require-Bundle)
- `ec.gob.sri.dimm.ats.ui` → Require-Bundle con `ec.gob.sri.dimm.data`, `ec.gob.sri.dimm.ats.modelo`. Activator usa `java.sql.SQLException` sin importarlo (resuelto via `osgi.parentClassloader=app`)
- `org.apache.derby.core` → DynamicImport-Package: * (para java.sql)
