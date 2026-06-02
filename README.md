# DIMM-Linux

**DIMM** (Declaración de Impuestos y Manejo de Módulos) — migrated from Eclipse 3.3 RCP on Windows to **Java 21 on Linux (GTK)**.

## What's inside

- Eclipse 3.3 RCP base (Equinox OSGi)
- **ATS** (Anexo Transaccional Simples) and other SRI plugins
- Databinding 1.4.0+ for ATS UI
- **Plugin management**: install and uninstall SRI extensions via the UI
- All 17 SRI extension plugins ready to install

## Quick start

```bash
cd /path/to/DIMM-Linux
./dimm.sh
```

## SRI plugins included in PLUGINSSRI/

| Plugin | File |
|--------|------|
| ACA | aca.1.0.1.zip |
| ADI | adi_1.6.0.zip |
| AFIC | AFIC.1.1.0.zip |
| ANR | anr_1_2(4).zip |
| ATS | ats.plugin.1.15.0.zip |
| devIVA | devIVA.zip |
| DIMM-ABT | dimm-abt.1.1.11.zip |
| DIMM-APS | dimm-aps.2.5.0.zip |
| DIMM-MID Plugin | dimm-mid-plugin-v1.7.0.zip |
| DIMM-MID Validador | dimm-mid-validador-v1.7.0.zip |
| DIMM-RDEP | dimm-rdep-plugin-v3.19.0.zip |
| DP | dp_v2.3.2.zip |
| ICE | ice.1.5.0.zip |
| OPRE | opre_1_0_15.zip |
| REOC | reoc_2_0_4.zip |
| Validador Consola | validador-consola-inst-3.0.2.jar |

Install via **Programa → Agregar Nuevos Programas** and select the zip file.

## License

This project includes:

- **Eclipse RCP 3.3** — [Eclipse Public License 2.0](https://www.eclipse.org/legal/epl-2.0/)
- **Apache Derby** — [Apache License 2.0](LICENSE)
- **DIMM plugins** by **Servicio de Rentas Internas del Ecuador (SRI)** — licensed under LGPL per ATS feature

See [LICENSE](LICENSE) for details.
