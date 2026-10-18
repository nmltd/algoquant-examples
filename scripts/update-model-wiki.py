#!/usr/bin/env python3
"""Merge measured results into an existing wiki Home and add model introductions.

Run against a checkout of the wiki after copying newly generated model pages.
Existing Home sections, execution records, output and chart links are preserved.
The former index URL remains a short pointer so old external links still work.
"""
import argparse
import json
from pathlib import Path
import re

START='<!-- MODEL_RESULTS_START -->'
END='<!-- MODEL_RESULTS_END -->'
INTRO_START='<!-- MODEL_INTRO_START -->'
INTRO_END='<!-- MODEL_INTRO_END -->'
HOME='https://github.com/nmltd/algoquant-examples/wiki/Home#model-demo-results'


def apply(wiki, introductions):
    data=json.loads(introductions.read_text())
    paths=[wiki/('Model-'+name+'.md') for name in data]
    missing=[p.name for p in paths if not p.exists()]
    if missing:
        raise ValueError('Missing tutorial pages: '+', '.join(missing))
    index=wiki/'Model-demo-results.md'
    summary=index.read_text()
    home_path=wiki/'Home.md'
    home=home_path.read_text() if home_path.exists() else '# AlgoQuant examples tutorials\n'
    # A prior application leaves a compatibility page; use the existing merged
    # block on repeat runs rather than replacing the table with that pointer.
    if '| Demo tutorial | Status |' in summary:
        summary=re.sub(r'^# [^\n]+\n\n','',summary,count=1)
        summary=re.sub(r'^(#{2,5}) ',r'\1# ',summary,flags=re.M)
        block=START+'\n## Model demo results\n\n'+summary.strip()+'\n'+END
    elif START in home and END in home:
        block=home[home.index(START):home.index(END)+len(END)]
    else:
        raise ValueError('No execution-results table to merge')
    # Remove only the exact link-only section created by the previous publisher.
    home=re.sub(r'\n## All model demos\n\n\[\[Model demo results\|Model-demo-results\]\][^\n]*\n?', '\n', home)
    if START in home:
        home=re.sub(re.escape(START)+r'.*?'+re.escape(END),lambda _:block,home,flags=re.S)
    else:
        if home.startswith('# AlgoQuant demo results'):
            previous=re.sub(r'^# [^\n]+\n\n','',home,count=1).strip()
            previous=re.sub(r'^(#{2,5}) ',r'\1# ',previous,flags=re.M)
            previous=previous.replace('All prices and volumes in this report','All prices and volumes in this earlier technical-demo report',1)
            home='# AlgoQuant model tutorials\n\nExplore the models, their original research, measured demo outputs and reproduction steps. Each tutorial distinguishes the publication from the Java adaptation and records whether its calculation completed. Data sources vary by demo; read the data mode on each page.\n\n'+block+'\n\n## Earlier technical-demo results (17 October 2026)\n\n'+previous+'\n'
        else:
            home=home.rstrip()+'\n\n'+block+'\n'
    home_path.write_text(home)
    for name,item in data.items():
        path=wiki/('Model-'+name+'.md')
        text=path.read_text()
        intro=INTRO_START+'\n## '+item['heading']+'\n\n'+item['text'].strip()+'\n'+INTRO_END+'\n\n'
        if INTRO_START in text:
            text=re.sub(re.escape(INTRO_START)+r'.*?'+re.escape(INTRO_END)+r'\s*',lambda _:intro,text,flags=re.S)
        else:
            marker='## What this example teaches'
            if marker not in text:raise ValueError('Missing insertion point: '+path.name)
            text=text.replace(marker,intro+marker,1)
        text=text.replace('https://github.com/nmltd/algoquant-examples/wiki/Model-demo-results',HOME)
        path.write_text(text)
    index.write_text('# Model demo results\n\nThe complete results index is now part of the [wiki front page]('+HOME+'). Follow that page for all 41 model tutorials, execution outcomes, charts and downloadable evidence.\n')
    sidebar=wiki/'_Sidebar.md'
    if sidebar.exists():
        sidebar.write_text(sidebar.read_text().replace('[[Model demo results|Model-demo-results]]','[Model demo results]('+HOME+')'))
    # Keep the original results anchor usable after changing visible headings.
    for path in [home_path,index,sidebar]+paths:
        if not path.exists():continue
        text=path.read_text()
        text=text.replace('# AlgoQuant model tutorials','# AlgoQuant quantitative model tutorials')
        text=text.replace('Explore the models,','Explore quantitative models,')
        text=text.replace('## Model demo results','<a id="model-demo-results"></a>\n## Quantitative model demo results')
        text=text.replace('# Model demo results','# Quantitative model demo results')
        text=text.replace('[Model demo results]','[Quantitative model demo results]')
        text=text.replace('[All model results]','[All quantitative model results]')
        text=text.replace('all 41 model tutorials','all 41 quantitative model tutorials')
        text=text.replace(' — execution tutorial',' — quantitative model tutorial')
        path.write_text(text)
    assert home_path.read_text().count('| Demo tutorial | Status |')==1
    assert all((wiki/('Model-'+name+'.md')).read_text().count(INTRO_START)==1 for name in data)
    print('Merged results into Home; updated %d model tutorial introductions.'%len(data))


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--wiki',required=True,type=Path)
    parser.add_argument('--introductions',type=Path,default=Path(__file__).resolve().parents[1]/'docs/model-introductions.json')
    args=parser.parse_args()
    apply(args.wiki,args.introductions)
