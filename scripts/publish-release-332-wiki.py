#!/usr/bin/env python3
"""Overlay 3.3.2 release guidance on the current wiki, preserving earlier records."""
from pathlib import Path
import re,sys
wiki=Path(sys.argv[1]);src=Path('docs/wiki')
START='<!-- RELEASE_332_START -->';END='<!-- RELEASE_332_END -->'
def overlay(path,content):
    text=path.read_text();block=START+'\n'+content.strip()+'\n'+END+'\n\n'
    if START in text:text=re.sub(re.escape(START)+r'.*?'+re.escape(END)+r'\s*',lambda _:block,text,flags=re.S)
    else:
        first,sep,rest=text.partition('\n\n');text=first+'\n\n'+block+rest
    path.write_text(text)
notice='''## Latest release: AlgoQuant 3.3.2

AlgoQuant is a framework for studying, simulating, researching and applying quantitative strategies. Start with the tutorials to learn its APIs; tutorial numerical results are incidental to their teaching purpose.

- [Release and downloads](https://github.com/nmltd/algoquant/releases/tag/v3.3.2); Maven: `tech.nmfin:algoquant-dist:3.3.2`.
- [Yahoo, Sina and Eastmoney stock/index history and CSV ZIP caching](https://github.com/nmltd/algoquant-examples/wiki/Historical-data-downloaders).
- [Updated CSI300 data-loading tutorial](https://github.com/nmltd/algoquant-examples/wiki/Model-CSI300SimulationDemo).
- [Complete compatibility survey and known limitations](https://github.com/nmltd/algoquant-examples/wiki/Compatibility-3.3.2): 84 main sources compile, 31 tests pass; 49 demos completed cleanly, four returned with errors/warnings, one failed, one needed credentials, one aggregate timed out, and the web app passed dedicated checks. Release promotion reused validation without rerunning test suites.

These observations concern execution, not financial validity. CSI300 completed both default baselines but skipped some constituent histories and reported NaN correlation measures. The 990-optimizer grid was not run.
'''
for name in ['Home.md','Tutorials.md','Execution-records.md']:
    if not (wiki/name).exists():(wiki/name).write_text('# '+name.removesuffix('.md').replace('-',' ')+'\n\n')
    overlay(wiki/name,notice)
for name in ['Historical-data-downloaders.md','Compatibility-3.3.2.md','Release-3.3.2.md']:
    (wiki/name).write_text((src/name).read_text())
sidebar=wiki/'_Sidebar.md'
if sidebar.exists():overlay(sidebar,'## AlgoQuant 3.3.2\n\n- [[Release-3.3.2]]\n- [[Historical-data-downloaders]]\n- [[Model-CSI300SimulationDemo]]\n- [[Compatibility-3.3.2]]')
# Present the maintained tutorial first. Keep the old page as a dated appendix,
# rather than silently replacing its old measurements with newer results.
csi=wiki/'Model-CSI300SimulationDemo.md';marker='<!-- RELEASE_332_CSI_ARCHIVE -->'
old=csi.read_text() if csi.exists() else ''
archive=old.split(marker,1)[1].strip().removesuffix('</details>').rstrip() if marker in old else old
current=(src/'CSI300-3.3.2.md').read_text()
current+='\n\n## Verified execution and limitations\n\nBoth default baselines completed with CSV ZIP/PNG exports. Some unavailable constituent histories were skipped, and CSI300 correlation calculations were assigned NaN. This is completed with warnings, not a clean numerical result. See [[Compatibility-3.3.2]] for source/artifact provenance and all entry-point outcomes.\n'
if archive.strip():current+='\n<details>\n<summary>Archived tutorial/execution record from before the 3.3.2 update</summary>\n\n'+marker+'\n'+archive.strip()+'\n\n</details>\n'
csi.write_text(current)
print('Updated release, downloader, compatibility and CSI300 wiki guidance; historical records preserved.')
