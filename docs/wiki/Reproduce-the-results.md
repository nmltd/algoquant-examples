# Reproduce the results

1. Clone [algoquant-examples](https://github.com/nmltd/algoquant-examples). Use Java 8 and Maven 3.9.9.
2. Configure Maven authentication as described in the repository README: your own GitHub classic PAT with `read:packages`, server ID `github-nm-packages`. The internal `NM_REPOSITORIES_TOKEN` is not required.
3. Put a valid NM license at `license/nm.lic`, or set the `nm.license` JVM property for an individual run. The README links the already-public starter trial license.
4. Build and run all examples:

```bash
mvn -U clean verify
bash scripts/run-offline.sh
```

The script uses Bash and GNU `timeout`, runs each demo in a separate JVM, passes `--quick`, and disables GUI charts. Set `NM_LICENSE_FILE` to override the script's license path. The dynamic demo writes CSV files under `log/`; the other demos print performance reports.

For one example:

```bash
mvn exec:java -Dexec.mainClass=tech.nmfin.algoquant.demo.model.technical.SMA2Demo010
```

Use `--charts` on demos with a plotter to open a desktop chart. Use `--live` to download Yahoo data; those outputs differ from this report. Remove `--quick` from the dynamic demo to run its full interval/grid.

The dependency is a SNAPSHOT, so future builds may resolve different bytes. The verified CI run is the provenance for these recorded outputs; future research should retain exact artifact checksums and versions alongside reports.

[[Home]]
