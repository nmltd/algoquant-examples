# Running and publishing model tutorials

The manual **Run model demos and prepare tutorials** workflow compiles production
source with tests skipped, discovers every `main` under `demo/model`, and runs
those entries in isolated processes. Support classes are inventoried separately.

Each demo gets a virtual display, a 1200 MB maximum Java heap and a 180-second
limit. A completed main is distinct from a profitable or statistically valid model.
The six technical demos use documented synthetic `--quick` inputs. Other demos
keep their original main defaults; missing historical files, providers and legacy
databases are reported as blockers rather than substituted with invented data.
MySQL access is rejected before authentication because this public run has no
configured database account. No unit tests are run.

`ModelDemoRunner.java` initializes the external NM license, invokes the unchanged
main, exports its JFreeChart panels as PNGs, closes its windows and exits. This
avoids treating a finished desktop demo as a hung process. Randomized demos retain
their original seeding behavior. Live Yahoo results depend on provider availability.

The Python runner retains stdout/stderr, a JSON execution record and generated
report/chart files. It redacts legacy database configuration if echoed by a
library; market-data caches and license files are excluded from publication.
Each exported file has a SHA-256 digest. Outputs from failed or timed-out processes
are explicitly labelled as partial.

After all shards finish, `publish-model-tutorials.py` requires an execution record
for every discovered entry point. It generates one wiki page and one release ZIP
per demo, a support-class inventory, a manifest and an index with actual outcomes.
Publish the `model-tutorial-publication` artifact to a GitHub Release, and push
its Markdown pages into the repository wiki. A wiki write requires a suitable
GitHub token; it is never embedded in the generated pages or release files.

For a local single-demo run after Maven packaging (Linux with `xvfb-run`, JDK 8
and a valid external license):

```bash
mkdir -p target/model-runner
javac -cp 'target/classes:target/lib/*' -d target/model-runner scripts/ModelDemoRunner.java
export NM_LICENSE_FILE=/absolute/path/nm.lic
python3 scripts/run-model-demos.py --only SMA2Demo010 --seconds 180
```

Invoke `scripts/publish-model-tutorials.py` only after a full run; it deliberately
rejects an incomplete inventory. Raw results live in release assets, not the
source tree. The wiki explains verified outputs and specific prerequisites.

## Paper introductions and the wiki front page

`docs/model-introductions.json` contains reviewed paper/strategy introductions for all
41 runnable model demos. Each entry links the publication, explains its main idea
and separates that idea from this repository's implementation. Review limitations
are explicit; a demo without an identified paper receives a strategy introduction.
The tutorial generator includes these entries in newly generated pages.

After copying generated model pages into an existing wiki checkout, merge the
results table into Home and preserve the old index URL as a compatibility link:

```bash
python3 scripts/update-model-wiki.py --wiki /path/to/algoquant-examples.wiki
```

The updater preserves other Home content and the demo execution/output sections.
It can be run again without duplicating introductions or the results index.
Review and commit the wiki diff before pushing. No simulations or tests are needed
when updating these explanatory sections.
