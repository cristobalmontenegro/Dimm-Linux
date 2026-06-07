# DIMM-Linux

**DIMM** (Declaración de Impuestos y Manejo de Módulos) es el software de declaración de impuestos del **Servicio de Rentas Internas del Ecuador (SRI)** para presentar anexos como ATS, RDEP, ICE, ADI y muchos otros.

Fue construido como una aplicación **Eclipse 3.3 RCP** (~2007), distribuido originalmente solo para **Windows** y diseñado para **Java 6**. El instalador oficial descargaba extensiones desde un sitio de actualizaciones del SRI que ya no responde en formato update site.

**Este proyecto es la única forma de ejecutar DIMM en Linux moderno.**

## Por qué existe

Sin esta migración:
- DIMM se cae al iniciar (incompatibilidad SWT/GTK, problemas de classloader, dependencias faltantes)
- El sitio de actualizaciones (`http://descargas.sri.gov.ec/dimm/updates`) no funciona — no se pueden instalar extensiones
- Java 6-8 están obsoletos y no están disponibles en distribuciones modernas
- Eclipse 3.3 RCP no puede resolver plugins compilados para Java 21

Esta migración resuelve cada capa del problema: parches de bytecode, inyección de dependencias, configuración OSGi, compatibilidad SWT/GTK, y un reemplazo para el sitio de actualizaciones caído.

## Qué se hizo

| Problema | Solución |
|----------|----------|
| SWT/GTK se cuelga con ciertos temas | `GTK2_RC_FILES=Raleigh` o `GDK_BACKEND=x11` |
| Java 6-8 → Java 21 | Equinox launcher, `--add-opens`, perfil `JavaSE-21.profile` personalizado |
| `Bundle-RequiredExecutionEnvironment` rechaza Java 21 | Script Python en `dimm.sh` elimina valores BREE incompatibles al iniciar |
| Sitio de actualizaciones caído | **Descarga por Internet reimplementada**: extrae la lista de `www.sri.gob.ec/formularios-e-instructivos1`, muestra los plugins disponibles con versión, descarga el zip desde `descargas.sri.gob.ec/download/anexos/...`, lo extrae y lo instala |
| Faltan widgets de calendario (databinding + nebula) | Copiados manualmente a `plugins/` |
| Spring context falla entre bundles OSGi | **SpringContextHelper** busca archivos `applicationContext*.xml`, retorna URLs `file:` evitando el classloader OSGi; política **Eclipse-RegisterBuddy** para visibilidad entre bundles |
| `File.delete()` falla en directorios no vacíos | Reemplazado por `deleteDirectory()` recursivo |
| Features en JAR no se leen como directorios | Extracción automática de `features/*.jar` a directorios al iniciar |
| Plugins huérfanos tras desinstalar | Limpieza automática al iniciar elimina plugins no referenciados por ningún feature instalado |

## Inicio rápido

```bash
cd /ruta/a/DIMM-Linux
./dimm.sh
```

### Instalar un plugin

- **Desde un zip local**: Programa → Agregar Nuevos Programas → Instalación por archivo
- **Desde internet**: Programa → Agregar Nuevos Programas → Instalación por Internet → seleccionar de la lista de plugins disponibles → descarga e instala automáticamente

### Desinstalar un plugin

Programa → Desinstalar Programas → seleccionar → OK → reinicia automáticamente.

## Plugins SRI

Todos los plugins se descargan directamente del SRI mediante la función **Instalación por Internet** desde `https://www.sri.gob.ec/formularios-e-instructivos1`. El repositorio no incluye ningún plugin SRI — se instalan en tiempo de ejecución por el usuario.

## Stack técnico

- **Eclipse 3.3** (Equinox OSGi) — base RCP de 2007
- **Java 21** — LTS moderno con `--add-opens` para acceso reflectivo
- **SWT/GTK** — widgets nativos de Linux mediante backend GTK2
- **Spring 2.5 + Hibernate 3** — framework legacy usado por RDEP, DP y core
- **ASM 9** — manipulación de bytecode para parchar clases compiladas sin fuente
- **Zip4j** — extracción de zip para instalación de plugins

## Licencia

- **Eclipse RCP 3.3** — [Eclipse Public License 2.0](https://www.eclipse.org/legal/epl-2.0/)
- **Plugins DIMM** por **Servicio de Rentas Internas del Ecuador (SRI)** — LGPL según feature ATS
- **Parches de migración** — bajo la misma licencia del proyecto original

## Enlaces

- [Repositorio GitHub](https://github.com/cristobalmontenegro/Dimm-Linux)
- Descargas de plugins SRI: `https://descargas.sri.gob.ec/download/anexos/{nombre}/{archivo}.zip`
