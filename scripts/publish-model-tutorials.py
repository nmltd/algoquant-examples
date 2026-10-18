#!/usr/bin/env python3
"""Turn measured run outputs into versioned wiki tutorials and release attachments."""
from collections import Counter
import datetime
import html
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import zipfile

ROOT=Path(__file__).resolve().parents[1]
RESULTS=ROOT/'target/model-results'
WIKI=Path(os.environ.get('TUTORIAL_WIKI',str(ROOT/'target/model-wiki')))
REPO='https://github.com/nmltd/algoquant-examples'
RUN=os.environ['GITHUB_RUN_ID']
SHA=os.environ['GITHUB_SHA']
TAG='model-tutorials-run-'+RUN
RELEASE=REPO+'/releases/tag/'+TAG
WIKI_URL=REPO+'/wiki/'
UTC=datetime.datetime.now(datetime.timezone.utc).strftime('%Y-%m-%d %H:%M UTC')


def narrative(source):
    text=(ROOT/source).read_text()
    declaration=re.search(r'^public (?:class|enum)',text,re.M)
    blocks=list(re.finditer(r'/\*\*.*?\*/',text[:declaration.start()],re.S))
    body=blocks[-1].group()[3:-2]
    body=re.sub(r'^\s*\* ?', '', body, flags=re.M)
    body=re.split(r'^@(?:author|see|param)',body,flags=re.M)[0]
    body=re.sub(r'\{@(?:code|link|linkplain) ([^}]+)\}', r'`\1`', body)
    body=re.sub(r'<b>(.*?)</b>',r'**\1**',body,flags=re.S)
    body=re.sub(r'</?p>|<br\s*/?>','\n\n',body)
    body=re.sub(r'<[^>]+>','',body)
    return re.sub(r'\n{3,}', '\n\n', html.unescape(body)).strip()


def output_excerpt(stdout):
    lines=stdout.splitlines()
    lines=[line for line in lines if not line.startswith('DEMO_RUNNER_') and '|-' not in line]
    if len(lines)>110:
        lines=lines[:30]+['... excerpt shortened; complete stdout is in the release archive ...']+lines[-80:]
    text='\n'.join(lines)
    if len(text)>18000:text=text[:8000]+'\n... excerpt shortened ...\n'+text[-10000:]
    return text.replace('```','~~~')


def diagnostic(stderr):
    lines=[line for line in stderr.splitlines() if line.strip()]
    selected=[line for line in lines if re.search(r'Exception|Error|Caused by|DEMO_RUNNER_FAILURE|not configured',line)]
    if not selected:selected=lines[-12:]
    return '\n'.join(selected[:18])[:5000].replace('```','~~~')


if __name__=='__main__':
    WIKI.mkdir(parents=True,exist_ok=True)
    inventory=json.loads((RESULTS/'inventory.json').read_text())
    records=[json.loads(p.read_text()) for p in sorted(RESULTS.glob('*/result.json'))]
    expected={x['class'] for x in inventory if x['runnable']}
    actual={x['class'] for x in records}
    assert expected==actual,(expected-actual,actual-expected)
    assert len(records)==len(actual),'Duplicate execution records'
    introductions=json.loads((ROOT/'docs/model-introductions.json').read_text())
    assert {x['name'] for x in records} <= set(introductions),'Missing model introductions'
    counts=Counter(x['status'] for x in records)
    manifest={'run':RUN,'source_commit':SHA,'generated_utc':UTC,'counts':dict(counts),
              'runnable_count':len(expected),'support_classes':[x for x in inventory if not x['runnable']],
              'results':records}
    (RESULTS/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
    summary='# Model demo run results\n\n'
    summary+='Run ['+RUN+']('+REPO+'/actions/runs/'+RUN+'), generated '+UTC+'. Source: `'+SHA+'`.\n\n'
    summary+='This run attempted **%d runnable demos**; **%d support classes** have no main entry point.\n\n'%(len(records),len(manifest['support_classes']))
    summary+='| Outcome | Count |\n| --- | ---: |\n'+''.join('| '+status+' | '+str(count)+' |\n' for status,count in sorted(counts.items()))
    summary+='\nTechnical demos use documented synthetic `--quick` inputs. Other demos use their original main defaults. Each process uses a virtual display, a valid external starter license, a bounded heap and a time limit; no tests are run. Legacy MySQL access is blocked before authentication because no database account is configured.\n\n'
    summary+='A completed main is an execution observation, not proof of model validity or investment performance. Timeout/failed runs can contain partial outputs; these are labelled and must not be treated as completed backtests. Generated random inputs may be unseeded, and provider data/caches can change.\n\n'
    summary+='[Permanent raw outputs and charts]('+RELEASE+') · [Model literature guide]('+REPO+'/blob/'+SHA+'/docs/model-literature.md)\n\n'
    summary+='| Demo tutorial | Status | Seconds | Data mode |\n| --- | --- | ---: | --- |\n'
    for item in sorted(records,key=lambda x:x['source']):
        slug='Model-'+item['name'];summary+='| ['+item['name']+']('+WIKI_URL+slug+') | '+item['status']+' | %.2f | '%item['seconds']+item['mode']+' |\n'
        page='# '+item['name']+' — execution tutorial\n\n'
        page+='[All model results]('+WIKI_URL+'Home#model-demo-results) · [Pinned source]('+REPO+'/blob/'+SHA+'/'+item['source']+') · [Javadoc](https://nmltd.github.io/algoquant-examples/'+item['class'].replace('.','/')+'.html)\n\n'
        intro=introductions[item['name']]
        page+='<!-- MODEL_INTRO_START -->\n## '+intro['heading']+'\n\n'+intro['text'].strip()+'\n<!-- MODEL_INTRO_END -->\n\n'
        page+='## What this example teaches\n\n'+narrative(item['source'])+'\n\n'
        page+='## Verified execution record\n\n| Item | Value |\n| --- | --- |\n'
        for name,value in [('Status',item['status']),('Run',RUN),('UTC publication',UTC),('Source commit',SHA),('Data mode',item['mode']),('Exit code',str(item['exit_code'])),('Elapsed seconds',str(item['seconds'])),('Time limit seconds',str(item['limit_seconds'])),('Maximum Java heap',item['heap'])]:page+='| '+name+' | '+str(value)+' |\n'
        page+='\n'+item['explanation']+'\n\n'
        page+='## Reproduce the calculation\n\nFollow the repository setup for JDK 8, Maven credentials and an external NM license. From the repository root:\n\n```bash\nmvn -U -Dmaven.test.skip=true clean package\nmvn exec:java -Dexec.mainClass='+item['class']+(' -Dexec.args="--quick"' if item['arguments'] else '')+'\n```\n\n'
        page+='The automated run uses `scripts/ModelDemoRunner.java` to initialize the external license before the unchanged main, save JFreeChart windows as PNGs, and exit after main returns. It uses an isolated working directory with the repository datasets/configuration linked in. Running directly may leave desktop windows open.\n\n'
        page+='## Observed output\n\n'
        if item['status']!='completed':page+='**This is an incomplete or blocked run. Output below is diagnostic or partial, not a verified strategy result.**\n\n'
        stdout=(RESULTS/item['name']/'stdout.log').read_text()
        page+='```text\n'+output_excerpt(stdout)+'\n```\n\n'
        stderr=(RESULTS/item['name']/'stderr.log').read_text()
        warnings=item['status']=='completed' and re.search(r'Exception|\bWARN\b.*(?:fail|error)|\bNaN\b',stdout+'\n'+stderr,re.I)
        if warnings:
            page+='**Execution caveat:** Main returned, but the log contains exceptions, failed operations or undefined values. This records process completion only; inspect the diagnostic below before treating any output as a valid backtest.\n\n'
        if (item['status']!='completed' or warnings) and (stdout+stderr).strip():page+='## Diagnostic and prerequisites\n\n```text\n'+diagnostic(stdout+'\n'+stderr)+'\n```\n\n'
        charts=[x for x in item['exports'] if x['path'].lower().endswith('.png')]
        if charts:
            page+='## Charts from this run\n\n'
            for i,chart in enumerate(charts,1):
                asset=item['name']+'-chart-%02d.png'%i
                page+='![Chart '+str(i)+']('+REPO+'/releases/download/'+TAG+'/'+asset+')\n\n'
        page+='## Downloadable evidence\n\n[Download '+item['name']+'.zip]('+REPO+'/releases/download/'+TAG+'/'+item['name']+'.zip) contains stdout, stderr, the execution record, checksums and any exported CSV/chart files.\n\n'
        if item['exports']:
            page+='| Export | Bytes |\n| --- | ---: |\n'+''.join('| `'+x['path']+'` | '+str(x['bytes'])+' |\n' for x in item['exports'])+'\n'
        page+='## Interpreting the result\n\n'
        if item['status']=='completed':
            page+='Read the reported units: optimizer weights, model-fit coefficients, forecast correlations and simulated P/L are different outputs. Synthetic runs demonstrate the API rather than historical market returns. Inspect undefined values and open positions instead of replacing them with zero. Chart exports visualize exactly this run.\n'
        else:
            page+='Use the first relevant exception and the source configuration to resolve the prerequisite or code issue, then rerun. A missing dataset requires the correct original schema and period; substituting synthetic values would be a different experiment. A timeout establishes only that this bounded attempt did not finish.\n'
        page+='\nFor model assumptions, original publications and adaptation limits, use the [literature guide]('+REPO+'/blob/'+SHA+'/docs/model-literature.md).\n'
        (WIKI/(slug+'.md')).write_text(page)
    summary+='\n## Support classes (not standalone demos)\n\n'+''.join('- `'+x['class']+'`\n' for x in manifest['support_classes'])
    (WIKI/'Model-demo-results.md').write_text(summary)
    release=ROOT/'target/model-release';release.mkdir(exist_ok=True)
    for item in records:
        charts=[x for x in item['exports'] if x['path'].lower().endswith('.png')]
        for i,chart in enumerate(charts,1):
            shutil.copyfile(RESULTS/item['name']/chart['path'],release/(item['name']+'-chart-%02d.png'%i))
        with zipfile.ZipFile(release/(item['name']+'.zip'),'w',zipfile.ZIP_DEFLATED) as archive:
            for path in sorted((RESULTS/item['name']).rglob('*')):
                if path.is_file():archive.write(path,path.relative_to(RESULTS/item['name']))
    (release/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
    (ROOT/'target/model-release-notes.md').write_text(summary)
    print(json.dumps({'demos':len(records),'counts':dict(counts),'wiki':str(WIKI)}))
