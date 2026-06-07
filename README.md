# DIMM-Linux

**DIMM** (Declaración de Impuestos y Manejo de Módulos) is the tax declaration software mandated by Ecuador's **Servicio de Rentas Internas (SRI)** for filing annexes such as ATS, RDEP, ICE, ADI, and many others.

It was built as an **Eclipse 3.3 RCP** application (circa 2007), originally distributed only for **Windows** and designed to run on **Java 6**. The official installer downloaded extensions from an SRI update site that no longer responds in update site format.

**This project is the only way to run DIMM on modern Linux.**

## Why this exists

Without this migration:
- DIMM crashes on startup (SWT/GTK incompatibility, classloader issues, missing dependencies)
- The update site (`http://descargas.sri.gov.ec/dimm/updates`) is dead — no extensions can be installed
- Java 6-8 are EOL and unavailable on modern distributions
- Eclipse 3.3 RCP cannot resolve plugins compiled for Java 21

This migration solves every layer of that stack: bytecode patches, dependency injection, OSGi configuration, SWT/GTK compatibility, and a replacement for the dead update site.

## What was done

| Problem | Solution |
|---------|----------|
| SWT/GTK crashes with certain themes | `GTK2_RC_FILES=Raleigh` or `GDK_BACKEND=x11` |
| Java 6-8 → Java 21 | Equinox launcher, `--add-opens`, custom `JavaSE-21.profile` |
| `Bundle-RequiredExecutionEnvironment` rejects Java 21 | Auto-fix Python script in `dimm.sh` strips incompatible BREE values on every startup |
| Update site `descargas.sri.gov.ec/dimm/updates` is dead | **Internet download reimplemented**: scrapea `www.sri.gob.ec/formularios-e-instructivos1`, muestra lista seleccionable de plugins con versión, descarga el zip desde `descargas.sri.gob.ec/download/anexos/...`, extrae e instala |
| Missing databinding + nebula calendar widgets | Bundled manually into `plugins/` |
| Spring context fails across OSGi bundles | **SpringContextHelper** scans for `applicationContext*.xml` files, returns `file:` URLs bypassing OSGi classloader; **Eclipse-RegisterBuddy** policy for cross-bundle class visibility |
| `File.delete()` fails on non-empty directories | Patched to `deleteDirectory()` recursive |
| Feature JARs not readable as directories | Auto-extract `features/*.jar` → directory on startup |
| Orphan plugins after uninstall | Auto-cleanup on startup removes plugins not referenced by any installed feature |

## Quick start

```bash
cd /path/to/DIMM-Linux
./dimm.sh
```

### Install a plugin

- **From a local zip**: Programa → Agregar Nuevos Programas → Instalación por archivo
- **From the internet**: Programa → Agregar Nuevos Programas → Instalación por Internet → seleccionar de la lista de plugins disponibles → descarga e instala automáticamente

### Uninstall a plugin

Programa → Desinstalar Programas → select → OK → restarts automatically.

## SRI plugins included in PLUGINSSRI/

| Plugin | File |
|--------|------|
| ACA | `aca.1.0.1.zip` |
| ADI | `adi_1.6.0.zip` |
| AFIC | `AFIC.1.1.0.zip` |
| ANR | `anr_1_2(4).zip` |
| ATS | `ats.plugin.1.15.0.zip` |
| devIVA | `devIVA.zip` |
| DIMM-ABT | `dimm-abt.1.1.11.zip` |
| DIMM-APS | `dimm-aps.2.5.0.zip` |
| DIMM-MID Plugin | `dimm-mid-plugin-v1.7.0.zip` |
| DIMM-MID Validador | `dimm-mid-validador-v1.7.0.zip` |
| DIMM-RDEP | `dimm-rdep-plugin-v3.19.0.zip` |
| DP | `dp_v2.3.2.zip` |
| ICE | `ice.1.5.0.zip` |
| OPRE | `opre_1_0_15.zip` |
| REOC | `reoc_2_0_4.zip` |
| Validador Consola | `validador-consola-inst-3.0.2.jar` |

## Technical stack

- **Eclipse 3.3** (Equinox OSGi) — RCP base from 2007
- **Java 21** — modern LTS with `--add-opens` for reflective access
- **SWT/GTK** — native Linux widgets via GTK2 backend
- **Spring 2.5 + Hibernate 3** — legacy framework used by RDEP, DP, and core
- **ASM 9** — bytecode manipulation for patching compiled classes without source
- **Zip4j** — zip extraction for plugin installation

## License

- **Eclipse RCP 3.3** — [Eclipse Public License 2.0](https://www.eclipse.org/legal/epl-2.0/)
- **DIMM plugins** by **Servicio de Rentas Internas del Ecuador (SRI)** — LGPL per ATS feature
- Migration patches — provided under the same license as the original project

## Links

- [GitHub repository](https://github.com/cristobalmontenegro/Dimm-Linux)
- SRI plugin downloads: `https://descargas.sri.gob.ec/download/anexos/{name}/{filename}.zip`
