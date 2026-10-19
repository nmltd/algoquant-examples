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
javac -cp 'target/classes:target/lib/nmdev-dist-1.2.7.jar:target/lib/*' -d target/model-runner scripts/ModelDemoRunner.java
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

## Charts from recorded CSV and printed tables

The tutorial workflow now runs `chart-model-results.py` after collecting all demo
records. It converts recognized Product reports, CSV performance summaries,
allocation vectors/histories, P/L series, executions and selected analytical/model
diagnostics into PNG figures. No statistical model or simulation is rerun.

```bash
python3 -m pip install -r scripts/chart-requirements.txt
python3 scripts/chart-model-results.py --results target/model-results
```

Every figure includes a plotted-data CSV and metadata identifying its original
source file and SHA-256. Printed values retain the precision available in the log.
Different measures use separate axes; undefined or malformed values are omitted,
never replaced with zero. Failed/time-limited runs retain their status and have
prominent incomplete-run captions. Logged calculation errors remain visible.
Unrecognized or empty reports generate no invented data. For Infantino's parameter
report, the parser separates the next H label from the preceding value using the
exact sweep labels declared in the source.

`publish-model-tutorials.py` embeds these figures with captions and data downloads.
For a new publication based on an existing execution, set `GITHUB_RUN_ID` and
`GITHUB_SHA` to the original execution ID/source commit, `CHART_GENERATOR_SHA` to
the generator commit, and `TUTORIAL_RELEASE_TAG` to a new release tag. This preserves
the original run provenance and its raw release. Merge the resulting wiki index
into Home with `update-model-wiki.py` as described above.

## Wiki navigation and example roles

The wiki starts with usage tutorials, emphasizing API learning rather than their
numerical results. The six technical demos lead the learning path. Dataset,
resampling, analytical-call and portfolio-workflow examples are also tutorials,
including CoinBacktestDemo, CSI300SimulationDemo, GMA12Demo and
BootstrapSimulationDemo. Quantitative model references follow in research families.
AlgoQuant is the framework/host/hub for studying, simulating, researching and
applying these models or user-defined strategies.

`update-model-wiki.py` preserves stable demo URLs and their captured output. It
builds Home, Tutorials, Quantitative-models and Execution-records; execution status
and performance numbers live in the evidence pages, rather than the front-page
learning directory. The current taxonomy covers 19 tutorials and 22 model
references. Update its classification when adding a new runnable example.
