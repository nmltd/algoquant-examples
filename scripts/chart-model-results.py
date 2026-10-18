#!/usr/bin/env python3
"""Chart recorded AlgoQuant demo outputs without rerunning or modifying models.

Only recognized report schemas are parsed. Figures retain raw units, missing
values and source ordering. Every figure has a machine-readable CSV and metadata
with its source file; process completion is never upgraded by chart generation.
"""
import argparse
import csv
from collections import defaultdict
import hashlib
import io
import json
import math
from pathlib import Path
import re
import textwrap

import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from matplotlib.ticker import FuncFormatter

NUMBER=r'[-+]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][-+]?\d+)?'
PARAMETERS={'rebalance','window','riskAverse','shrinkage','rebalance period','data window','algorithm'}
ROOT=Path(__file__).resolve().parents[1]


def number(value):
    try:return float(value.strip())
    except (ValueError,AttributeError):return float('nan')


def table_blocks(text):
    """Recognize printed Product reports, preserving malformed/missing cells."""
    blocks=[];header=None;rows=[]
    # Some demos print the next report label without a preceding newline.
    text=text.replace('DEMO_RUNNER_MAIN_RETURNED','')
    for line in text.splitlines()+['']:
        cells=line.split('\t')
        if line.startswith('Product\t'):
            if header and rows:blocks.append((header,rows))
            header=cells;rows=[]
        elif header and len(cells)==len(header) and cells[0] and any(math.isfinite(number(v)) for v in cells[1:]):
            rows.append(cells)
        elif header:
            if rows:blocks.append((header,rows))
            header=None;rows=[]
    return blocks


class Charts:
    def __init__(self,directory,record):
        self.directory=directory;self.record=record
        self.output=directory/'files/derived-charts';self.output.mkdir(parents=True,exist_ok=True)
        self.items=[]
        self.logged_errors=bool(re.search(r'Exception|\bWARN\b.*(?:fail|error)',(directory/'stdout.log').read_text(),re.I))
        plt.rcParams.update({'font.size':9,'axes.spines.top':False,'axes.spines.right':False,'figure.facecolor':'white'})

    def save(self,title,panels,source,kind='bar',note=''):
        """One panel per metric: unlike units never share a numerical axis."""
        panels=[p for p in panels if any(math.isfinite(number(str(v))) for v in p[2])]
        if not panels:return
        if len(panels)>6:
            for i in range(0,len(panels),6):self.save(title+' — part '+str(i//6+1),panels[i:i+6],source,kind,note)
            return
        columns=min(2,len(panels));rows=math.ceil(len(panels)/columns)
        fig,axes=plt.subplots(rows,columns,figsize=(12,max(3.7,rows*3.2)),squeeze=False)
        data=[];missing=0
        for ax,(metric,labels,values) in zip(axes.flat,panels):
            values=[number(str(v)) for v in values];xx=list(range(len(values)))
            if kind=='line':
                ax.plot(xx,values,color='#126e82',linewidth=1.3,marker='o' if len(xx)<35 else None,markersize=3)
            else:
                finite=[i for i,v in enumerate(values) if math.isfinite(v)]
                ax.bar(finite,[values[i] for i in finite],color=['#126e82' if values[i]>=0 else '#bc4c45' for i in finite])
                ax.axhline(0,color='#64748b',linewidth=.6)
            for i,(label,value) in enumerate(zip(labels,values)):
                data.append([metric,label,'' if not math.isfinite(value) else repr(value)])
                if not math.isfinite(value):
                    missing+=1
                    if len(labels)<25:ax.text(i,.02,'undefined',rotation=90,transform=ax.get_xaxis_transform(),color='#a33',fontsize=7)
            step=max(1,math.ceil(len(labels)/16));ticks=xx[::step]
            ax.set_xticks(ticks,[textwrap.shorten(str(labels[i]),width=28,placeholder='…') for i in ticks],rotation=45,ha='right')
            ax.set_title(metric,loc='left',fontweight='bold');ax.set_ylabel('Raw reported value')
            ax.yaxis.set_major_formatter(FuncFormatter(lambda x,_:f'{x:.2e}' if x and (abs(x)<.01 or abs(x)>=1e6) else f'{x:,.2f}'))
            ax.grid(axis='y',alpha=.2);ax.set_axisbelow(True)
        for ax in list(axes.flat)[len(panels):]:ax.set_visible(False)
        status=self.record['status'];mode=self.record['mode']
        caution=('LOGGED CALCULATION ERRORS — ' if self.logged_errors else '')+('INCOMPLETE RUN — ' if status!='completed' else '')+mode+'; status: '+status
        fig.suptitle(self.record['name']+'\n'+title,fontsize=13,fontweight='bold')
        footer=caution+'\n'+note+(' Undefined/non-finite values are omitted, never replaced by zero.' if missing else '')
        fig.text(.015,.008,textwrap.fill(footer,150),fontsize=8,color='#475569')
        fig.tight_layout(rect=(0,.10,1,.93))
        stem='derived-%02d'%(len(self.items)+1)
        png=self.output/(stem+'.png');fig.savefig(png,dpi=140);plt.close(fig)
        csvfile=self.output/(stem+'.csv')
        with csvfile.open('w',newline='') as f:
            writer=csv.writer(f);writer.writerow(['metric','observation','value']);writer.writerows(data)
        self.items.append({'title':title,'image':png.relative_to(self.directory).as_posix(),
                           'data':csvfile.relative_to(self.directory).as_posix(),'source':source,
                           'source_sha256':hashlib.sha256((self.directory/source).read_bytes()).hexdigest(),
                           'source_precision':'CSV precision, or the precision printed by the demo',
                           'undefined_values':missing,'note':note})

    def reports(self,blocks,source):
        grouped=defaultdict(list)
        for header,rows in blocks:grouped[tuple(header)].append(rows)
        for header,blocks in grouped.items():
            if len(blocks)>1:
                selected=[next((r for r in block if r[0]=='Total'),block[-1]) for block in blocks]
                labels=['Report '+str(i+1) for i in range(len(selected))]
                note='Totals in printed report order; these are separate runs/configurations, not dates.'
                if self.record['name']=='Infantino2010ParamAnalysisDemo' and len(selected)==11:
                    labels=['H='+str(1+20*i) for i in range(11)]
                    note='H=1,21,…,201 observations, matching the source parameter sweep; in-sample sensitivity, not holdout performance.'
            else:
                selected=blocks[0];labels=[r[0] or 'Total' for r in selected]
                note='Products and aggregate shown exactly as reported; P/L is monetary, not percent. Currency conventions belong to the demo.'
            panels=[(h,labels,[r[i] for r in selected]) for i,h in enumerate(header) if i and any(math.isfinite(number(r[i])) for r in selected)]
            self.save('Reported performance statistics',panels,source,note=note)

    def csv_report(self,path):
        source=path.relative_to(self.directory).as_posix()
        rows=list(csv.DictReader(io.StringIO(path.read_text(encoding='utf-8-sig'))))
        if not rows:return
        headers=list(rows[0]);lower=path.name.lower()
        time=next((k for k in ['Time','Date/Time','Date'] if k in headers),None)
        if 'weights' in lower and time:
            groups=defaultdict(list)
            for row in rows:groups[tuple((k,row[k]) for k in headers if k in PARAMETERS)].append(row)
            # A full heatmap retains every date and asset, without selecting winners.
            for group,items in groups.items():
                assets=[k for k in headers if k not in PARAMETERS and k!=time]
                matrix=[[number(row[k]) for k in assets] for row in items]
                if not any(math.isfinite(x) for row in matrix for x in row):continue
                # Reuse one panel per date for compact histories; all values retained.
                panels=[(row[time],assets,[row[k] for k in assets]) for row in items]
                self.save(path.name.split('.')[-2]+' allocation — '+', '.join(k+'='+v for k,v in group),panels,source,note='Asset order follows CSV columns; each panel is one rebalance observation.')
        elif time and ('profit' in lower or '-pnl' in lower):
            groups=defaultdict(list)
            for row in rows:groups[tuple((k,row[k]) for k in headers if k in PARAMETERS)].append(row)
            panels=[]
            for group,items in groups.items():
                metrics=[k for k in headers if k not in PARAMETERS and k!=time]
                for metric in metrics:
                    panels.append(((', '.join(v for k,v in group)+' — ' if group else '')+metric,[r[time] for r in items],[r[metric] for r in items]))
            self.save('Reported P/L history',panels,source,kind='line',note='Source dates and raw P/L values are preserved; this is not reconstructed NAV or percentage return.')
        elif 'Product' in headers:
            # Summary rows are empty Product cells or explicit Total. Avoid mixing
            # per-asset contributions with independently funded strategy totals.
            summary=[r for r in rows if r['Product'] in ('','Total')]
            selected=summary or rows
            labels=[' | '.join(r[k] for k in headers if k in PARAMETERS) or r['Product'] or 'Total' for r in selected]
            if not summary:labels=[r['Product'] for r in selected]
            numeric=[k for k in headers if k not in PARAMETERS|{'Product'} and any(math.isfinite(number(r[k])) for r in selected)]
            for offset in range(0,len(numeric),6):
                panels=[(k,labels,[r[k] for r in selected]) for k in numeric[offset:offset+6]]
                self.save('CSV performance statistics (panel set %d)'%(offset//6+1),panels,source,note='One axis per measure; raw units and report definitions retained. Empty Product selects aggregate account rows.')
        elif 'Quantity' in headers and 'Symbol' in headers:
            groups=defaultdict(list)
            for row in rows:groups[row['Symbol']].append(row)
            panels=[]
            for symbol,items in groups.items():
                for metric in ['Quantity','Price','Profit']:
                    if metric in headers:panels.append((symbol+' — '+metric,[r.get('Time',r.get('Entry Time',str(i+1))) for i,r in enumerate(items)],[r[metric] for r in items]))
            self.save('Recorded executions/trades',panels,source,kind='line',note='Observations are recorded events in file order, not a continuous market-price series; quantities are not signed exposure.')

    def stdout(self,text):
        source='stdout.log'
        if self.record['name']=='Infantino2010ParamAnalysisDemo':
            # printReport omits a newline before the next H label; split only
            # the exact labels declared by newParamList, retaining all digits.
            for horizon in reversed(range(21,202,20)):
                text=text.replace(str(horizon)+':\nPerformance Report:', '\n'+str(horizon)+':\nPerformance Report:')
        self.reports(table_blocks(text),source)
        vectors=[]
        for match in re.finditer(r'([A-Za-z0-9][A-Za-z0-9-]* model weights|optimal weights|diversified weights|full w|w|x1|x2):\s*\[([^\]]+)\]',text):
            values=[number(v) for v in match[2].split(',')]
            vectors.append((match[1],list(range(1,len(values)+1)),values))
        if vectors:self.save('Optimizer allocation vectors',vectors,source,kind='line' if max(len(v[1]) for v in vectors)>30 else 'bar',note='Input asset-column indices; weights are fractions, not monetary P/L. Printed precision is retained.')
        # Each scenario has its own scale; compare estimators within a case,
        # without pooling distances from differently scaled random inputs.
        covariance_errors=re.findall(r'distances/errors:\s*\[([^\]]+)\]',text)
        covariance_panels=[]
        for index, values in enumerate(covariance_errors,1):
            cells=values.split(',')
            if len(cells)==3:
                covariance_panels.append(('Scenario '+str(index),
                    ['Sample covariance','Linear shrinkage','Nonlinear shrinkage'],cells))
        if covariance_panels:
            self.save('Covariance eigenvalue estimation errors',covariance_panels,source,
                note='Euclidean distance between covariance eigenvalue vectors. Scenario order follows main; dimensions, distributions and scales differ. These errors are not investment returns and do not establish a universal estimator ranking.')
        if self.record['name']=='Infantino2010PredictionAnalysisDemo':
            prices=re.findall(r'\[\d+\]\s+(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(?::\d{2})?):\s+\[([^\]]+)\]',text)
            if prices:
                width=len(prices[0][1].split(','))
                if all(len(row[1].split(','))==width for row in prices):
                    self.save('Loaded historical prices (input inspection)',
                        [('Asset column '+str(i+1),[row[0] for row in prices],
                          [row[1].split(',')[i] for row in prices]) for i in range(width)],
                        source,kind='line',note='These are input prices printed by the demo, not fitted forecasts or strategy returns. Asset-column indices follow source ordering.')
        dated=re.findall(r'^([0-9]{4}-[0-9]{2}-[0-9]{2}):\s*('+NUMBER+r')\s*$',text,re.M)
        if dated:self.save('Printed daily account series',[('Value as printed',[r[0] for r in dated],[r[1] for r in dated])],source,kind='line',note='Uses only the values actually printed; a flat zero series remains flat and is not evidence of profitable trading.')
        # OU diagnostic paths reset their time index for each demonstration path.
        pattern=r't = (\d+), price = ('+NUMBER+r'), price change = ('+NUMBER+r'), N = ('+NUMBER+r'), R = ('+NUMBER+r'), profit_\{t-1\} = ('+NUMBER+r'), cumulative wealth = ('+NUMBER+r')'
        paths=[]
        for match in re.finditer(pattern,text):
            row=match.groups()
            if not paths or int(row[0])<=int(paths[-1][-1][0]):paths.append([])
            paths[-1].append(row)
        for i,rows in enumerate(paths,1):
            self.save('OU diagnostic path '+str(i),[(metric,[r[0] for r in rows],[r[j] for r in rows]) for metric,j in [('Log spread (printed price)',1),('N (position)',3),('Cumulative wealth (printed)',6)]],source,kind='line',note='Generated-path diagnostics; fixed-W sizing in the source is retained. These are printed observations, not a reconstructed self-financing backtest.')
        scalar={}
        for label,pattern in [
            ('Holding-time probability',r'the probability of holding (?:till )?time = 1:\s*('+NUMBER+r')'),
            ('Expected P/L for a long position',r'the expected pnl for (?:a )?long position:\s*('+NUMBER+r')'),
            ('Estimated state parameter',r'(?:q|p|alpha0|alpha1|rate0|rate1|mu)\s*[:=]\s*('+NUMBER+r')')]:
            values=re.findall(pattern,text)
            if values:scalar[label]=values
        panels=[(label,['Printed value '+str(i+1) for i in range(len(vals))],vals) for label,vals in scalar.items() if label!='Estimated state parameter']
        if panels:self.save('Analytical trading-rule quantities',panels,source,note='Printed analytical quantities at the reported parameter estimates; not historical account returns.')
        params=defaultdict(list)
        for match in re.finditer(r'\b(q|p|alpha0|alpha1|rate0|rate1|mu)\s*[:=]\s*('+NUMBER+r')',text):params[match[1]].append(match[2])
        if params:self.save('Fitted distribution parameters',[(k,['Fit '+str(i+1) for i in range(len(v))],v) for k,v in params.items()],source,note='Parameter axes are separate. Record order is preserved; estimated parameters are not investment performance.')
        gamma=re.findall(r'gamma = ('+NUMBER+r'), number of sims = (\d+), min Sharpe = ('+NUMBER+r'), mean Sharpe = ('+NUMBER+r'), max Sharpe = ('+NUMBER+r')',text)
        if gamma:self.save('Reported Monte Carlo Sharpe summary',[(label,['γ='+r[0] for r in gamma],[r[j] for r in gamma]) for label,j in [('Minimum reported Sharpe',2),('Mean reported Sharpe',3),('Maximum reported Sharpe',4)]],source,note='Uses printed Monte Carlo summaries and the demo’s Sharpe convention; not independently annualized or validated.')

    def run(self):
        # Derived files are rebuilt from originals, never fed back as new input.
        for file in self.output.glob('*'):file.unlink()
        self.stdout((self.directory/'stdout.log').read_text())
        originals=[self.directory/x['path'] for x in self.record['exports'] if 'derived-charts' not in x['path']]
        for path in originals:
            if path.suffix=='.csv':self.csv_report(path)
            elif path.suffix=='.txt':self.reports(table_blocks(path.read_text()),path.relative_to(self.directory).as_posix())
        (self.output/'charts.json').write_text(json.dumps(self.items,indent=2)+'\n')
        self.record['exports']=[x for x in self.record['exports'] if 'derived-charts' not in x['path']]
        for path in sorted(self.output.iterdir()):
            self.record['exports'].append({'path':path.relative_to(self.directory).as_posix(),'bytes':path.stat().st_size,'sha256':hashlib.sha256(path.read_bytes()).hexdigest()})
        self.record['derived_charts']=self.items
        (self.directory/'result.json').write_text(json.dumps(self.record,indent=2)+'\n')
        return len(self.items)


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--results',type=Path,default=ROOT/'target/model-results')
    args=parser.parse_args();counts={}
    for path in sorted(args.results.glob('*/result.json')):
        record=json.loads(path.read_text());counts[record['name']]=Charts(path.parent,record).run()
    print(json.dumps({'charts':sum(counts.values()),'demos_with_charts':sum(bool(v) for v in counts.values()),'by_demo':counts},indent=2))
