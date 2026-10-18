#!/usr/bin/env python3
"""Organize AlgoQuant's wiki as tutorials first, then quantitative model references.

Run after copying generated pages into a wiki checkout. Stable demo URLs and
recorded outputs remain intact; the Home page is a learning/research directory,
while the full execution inventory has its own page.
"""
import argparse
import json
from pathlib import Path
import re

BASE='https://github.com/nmltd/algoquant-examples/wiki/'
START='<!-- MODEL_RESULTS_START -->'
END='<!-- MODEL_RESULTS_END -->'
INTRO_START='<!-- MODEL_INTRO_START -->'
INTRO_END='<!-- MODEL_INTRO_END -->'
TUTORIALS=[
 ('SMA2Demo010','Technical tutorials','Build a first SMA crossover simulation and read its report.'),
 ('SMA2Demo011','Technical tutorials','Explore another SMA strategy configuration.'),
 ('SMA2Demo020','Technical tutorials','Configure signals, execution and performance measures.'),
 ('SMA2DynamicDemo','Technical tutorials','Run parameter/rebalance experiments and export reports.'),
 ('AccumulationDistributionLineDemo','Technical tutorials','Use a price/volume indicator in a simulation.'),
 ('ChaikinMoneyFlowDemo','Technical tutorials','Configure a rolling volume-flow indicator.'),
 ('TutorialStrategyDemo','Framework and data tutorials','Follow market events, orders, fills and reporting.'),
 ('BootstrapSimulationDemo','Framework and data tutorials','Use resampling and repeated simulations; a framework lesson.'),
 ('GMA12Demo','Framework and data tutorials','Call return-model and analytical-rule APIs.'),
 ('CoinBacktestDemo','Framework and data tutorials','Apply backtesting infrastructure to a crypto dataset.'),
 ('CSI300SimulationDemo','Framework and data tutorials','Configure a constituent universe and historical-data simulation.'),
 ('PortfolioOptimizationAlgorithmDemo','Portfolio workflow tutorials','Call an optimizer and map weights to products.'),
 ('PortfolioOptimizationBacktestDemo','Portfolio workflow tutorials','Connect rolling weight estimation with simulation.'),
 ('PortfolioOptimComparisons1Demo','Portfolio workflow tutorials','Compare allocation algorithms on common generated inputs.'),
 ('PortfolioOptimComparisons2Demo','Portfolio workflow tutorials','Compare allocation workflows, reports and rebalancing.'),
 ('SOCPMarkowitzComparisonDemo','Portfolio workflow tutorials','Compare solver formulations and numerical outputs.'),
 ('CovarianceComparisonDemo','Portfolio workflow tutorials','Compare covariance estimators and inspect numerical diagnostics.'),
 ('MarkowitzBigDemo','Portfolio workflow tutorials','Exercise optimizer scaling on generated high-dimensional inputs.'),
 ('PerformanceOptimizedYahooEODPortfolioDemo','Portfolio workflow tutorials','Assemble a search objective from performance measures.'),
]
MODELS=[
 ('Trend following and regimes',[
  ('Dai2011LongOnlyDemo','Dai–Zhang–Zhu regime-switching trend following.')]),
 ('Statistical arbitrage and mean reversion',[
  ('KagiMRBasketSimulationDemo','Sparse mean-reverting basket construction with a Kagi trading adaptation.'),
  ('MultiPeriodKagiMRStrategyDemo','Repeated-period basket estimation and simulation.'),
  ('Elliott2005StrategyDemo1','Latent-spread pairs-trading model and signal customization.'),
  ('Elliott2005StrategyDemo2','Alternative pairs-trading configuration.'),
  ('MeanReversionBasedOnSpreadQuantileDemo','Spread-quantile rule related to pairs trading.'),
  ('OULogSpreadCRRASignalDemo','OU spread dynamics and CRRA allocation calculations.')]),
 ('Forecasting and factor models',[
  ('Infantino2010ParamAnalysisDemo','PCA-based accumulated-return forecasts and parameter sensitivity.'),
  ('Infantino2010PredictionAnalysisDemo','Analyze forecasts against observed returns.'),
  ('EconomicModelStrategyDemo','Construct a strategy using economic factor inputs.'),
  ('FundamentalModelStrategyDemo','Construct a strategy using fundamental factor inputs.')]),
 ('Portfolio allocation',[
  ('MarkowitzDemo','Mean–variance allocation in a historical simulation.'),
  ('Corvalan2005Demo','Diversification among nearly equivalent risk–return allocations.'),
  ('Lai2010NPEBModelDemo','Allocation accounting for uncertain estimates through resampling.')]),
 ('Statistical and seasonal models',[
  ('KnightSatchellTran1995MLEDemo','Fit an asymmetric return-distribution model; a statistical foundation.'),
  ('JanuaryEffectStrategyDemo','Study an explicitly configured calendar-seasonality policy.')]),
 ('Grid and inventory policies',[
  ('MRModelFixedGridDemo','Fixed-grid inventory policy.'),
  ('MRModelFixedGridOnSGXDataDemo','Fixed-grid policy applied to SGX observations.'),
  ('MRModelPeriodicDemo','Periodically updated mean-reversion policy.'),
  ('MRModelRanged1Demo','Range-based mean-reversion policy.'),
  ('MRModelRanged2Demo','Alternative range-based policy.'),
  ('MeanReversionPortfolioDemo','Simulate a portfolio of mean-reversion components.')]),
]


def table(rows):
    return '| Example | What to study |\n| --- | --- |\n'+''.join('| ['+name+']('+BASE+'Model-'+name+') | '+focus+' |\n' for name,focus in rows)+'\n'


def tutorial_directory():
    text=''
    for group in dict.fromkeys(row[1] for row in TUTORIALS):
        text+='### '+group+'\n\n'+table([(name,focus) for name,category,focus in TUTORIALS if category==group])
    return text


def model_directory():
    return ''.join('### '+group+'\n\n'+table(rows) for group,rows in MODELS)


def apply(wiki,introductions):
    data=json.loads(introductions.read_text())
    tutorial_names={row[0] for row in TUTORIALS}
    model_names={name for _,rows in MODELS for name,_ in rows}
    assert not tutorial_names&model_names
    assert tutorial_names|model_names==set(data),'Every demo needs exactly one classification'
    for name in data:
        if not (wiki/('Model-'+name+'.md')).exists():raise ValueError('Missing page: '+name)
    home_path=wiki/'Home.md';old_home=home_path.read_text()
    index=wiki/'Model-demo-results.md';summary=index.read_text()
    # Fresh generated inventory takes precedence; otherwise retain the existing
    # full execution archive. Navigation-only pages never replace measured data.
    if '| Demo tutorial | Status |' in summary:
        record=re.sub(r'^# [^\n]+\n\n','',summary,count=1).strip()
    elif START in old_home and END in old_home:
        record=old_home.split(START,1)[1].split(END,1)[0]
        record=re.sub(r'<a[^>]+></a>\s*','',record)
        record=re.sub(r'^\s*## [^\n]+\n','',record,count=1).strip()
    elif (wiki/'Execution-records.md').exists():
        record=(wiki/'Execution-records.md').read_text().split('<!-- EXECUTION_DATA -->',1)[1].strip()
    else:raise ValueError('Missing full execution inventory')
    (wiki/'Execution-records.md').write_text('# Execution records and downloadable evidence\n\n[Wiki home]('+BASE+'Home) · [Tutorials]('+BASE+'Tutorials) · [Quantitative model references]('+BASE+'Quantitative-models)\n\nThese are execution observations, not a strategy ranking. Tutorial results are incidental to teaching the framework and have no significance as investment evidence. Incomplete runs and logged errors remain documented.\n\n<!-- EXECUTION_DATA -->\n'+record+'\n')
    # Retain the earlier synthetic report as a historical appendix, off Home.
    legacy='## Earlier technical-demo results (17 October 2026)'
    if legacy in old_home:
        archived=old_home.split(legacy,1)[1].strip()
        (wiki/'Technical-tutorial-run-20261017.md').write_text('# Earlier technical tutorial run — 17 October 2026\n\n[Start with the tutorials]('+BASE+'Tutorials). These numbers are incidental teaching outputs and are irrelevant to assessing investment performance.\n\n'+archived+'\n')
    framework='**AlgoQuant is a framework and a host/hub for studying, simulating, researching and applying quantitative strategies.** It provides the building blocks for data handling, strategy logic, orders, execution, portfolio construction and performance analysis. You can bring your own quantitative models, adapt the supplied implementations and compare alternatives within the same framework.'
    tutorial_purpose='**These examples are tutorials that teach how to use AlgoQuant. Their numerical results are incidental and irrelevant to their teaching purpose.** Use them to learn API calls, simulation setup, data flow and reporting. The technical examples use synthetic quick-mode data. Read a result here as an illustration of a library call, not as investment evidence.'
    model_purpose='We provide quantitative model implementations as references for study, further research, actual usage and application. They offer starting points for understanding published ideas, testing assumptions and developing your own strategies. Each page explains the underlying idea, its publication where identified, the Java adaptation, and the recorded outputs. Allocation methods, statistical models and trading policies are identified by their role; they are not all standalone trading systems.'
    home='# AlgoQuant: tutorials and quantitative strategy research\n\n'+framework+'\n\n'
    home+='## 1. Tutorials — learn how to use AlgoQuant\n\n'+tutorial_purpose+'\n\nStart with **SMA2Demo010**, then explore strategy configuration, indicators, simulation workflows and portfolio APIs.\n\n'+tutorial_directory()
    home+='<a id="model-demo-results"></a>\n## 2. Quantitative model references — study, research and application\n\n'+model_purpose+'\n\n'+model_directory()
    home+='## 3. Build and investigate your own strategies\n\nUse the framework to connect data → model/signal → position sizing → orders/execution → account valuation → performance analysis. Compare assumptions and implementations, reproduce a calculation, and extend it with your own data and evaluation design. Research-oriented adaptations and policies without an identified paper are labelled on their pages.\n\n'
    home+='## 4. Documentation and execution evidence\n\n- [Public source and setup](https://github.com/nmltd/algoquant-examples)\n- [AlgoQuant API documentation](https://nmltd.github.io/algoquant/)\n- [Example Javadoc](https://nmltd.github.io/algoquant-examples/)\n- [Original publications and implementation guide](https://github.com/nmltd/algoquant-examples/blob/main/docs/model-literature.md)\n- [Complete execution records, run status and downloads]('+BASE+'Execution-records)\n- [Technical tutorial methodology]('+BASE+'Data-and-methodology) and [reproduction instructions]('+BASE+'Reproduce-the-results)\n'
    home_path.write_text(home)
    (wiki/'Tutorials.md').write_text('# Tutorials: learn how to use AlgoQuant\n\n[Wiki home]('+BASE+'Home)\n\n'+tutorial_purpose+'\n\n'+tutorial_directory()+'## Tutorial support\n\n[DemoSupport]('+BASE+'DemoSupport) explains shared setup. [Methodology]('+BASE+'Data-and-methodology) and [reproduction instructions]('+BASE+'Reproduce-the-results) describe the earlier technical run. Full numerical records remain on each example page and in [Execution records]('+BASE+'Execution-records).\n')
    (wiki/'Quantitative-models.md').write_text('# Quantitative model references\n\n[Wiki home]('+BASE+'Home) · [Start with the tutorials]('+BASE+'Tutorials)\n\n'+framework+'\n\n'+model_purpose+'\n\n'+model_directory())
    for name,item in data.items():
        path=wiki/('Model-'+name+'.md');text=path.read_text()
        intro=INTRO_START+'\n## '+item['heading']+'\n\n'+item['text'].strip()+'\n'+INTRO_END+'\n\n'
        if INTRO_START in text:text=re.sub(re.escape(INTRO_START)+r'.*?'+re.escape(INTRO_END)+r'\s*',lambda _:intro,text,flags=re.S)
        else:text=text.replace('## What this example teaches',intro+'## What this example teaches',1)
        tutorial=name in tutorial_names
        title=name+(' — AlgoQuant usage tutorial' if tutorial else ' — quantitative model reference')
        text=re.sub(r'^# [^\n]+','# '+title,text,count=1)
        nav='[Wiki home]('+BASE+'Home) · ['+('Tutorials' if tutorial else 'Quantitative model references')+']('+BASE+('Tutorials' if tutorial else 'Quantitative-models')+') · [Execution records]('+BASE+'Execution-records)'
        notice=tutorial_purpose if tutorial else 'This quantitative model reference is provided for study, further research and application within the AlgoQuant framework. Read the research idea and implementation choices separately from the execution record.'
        banner='<!-- PAGE_CATEGORY_START -->\n'+nav+'\n\n'+notice+'\n<!-- PAGE_CATEGORY_END -->\n\n'
        if '<!-- PAGE_CATEGORY_START -->' in text:text=re.sub(r'<!-- PAGE_CATEGORY_START -->.*?<!-- PAGE_CATEGORY_END -->\s*',lambda _:banner,text,flags=re.S)
        else:
            first,rest=text.split('\n\n',1);text=first+'\n\n'+banner+rest
        text=text.replace('[All quantitative model results]','[All examples and documentation]')
        text=text.replace(BASE+'Home#model-demo-results',BASE+'Home')
        text=text.replace(BASE+'Model-demo-results',BASE+'Execution-records')
        path.write_text(text)
    index.write_text('# Example execution records\n\nStart with the [wiki home]('+BASE+'Home) for tutorials first, followed by quantitative model references. The complete measured run inventory is retained under [Execution records]('+BASE+'Execution-records).\n')
    sidebar='# AlgoQuant\n\n[Home]('+BASE+'Home)\n\n## Learn the framework\n\n[Tutorial directory]('+BASE+'Tutorials)\n\n'
    sidebar+=''.join('- ['+name+']('+BASE+'Model-'+name+')\n' for name,_,_ in TUTORIALS[:6])
    sidebar+='\n## Research and application\n\n[Quantitative model references]('+BASE+'Quantitative-models)\n\n'+''.join('- ['+group+']('+BASE+'Quantitative-models#'+re.sub(r'[^a-z0-9 -]','',group.lower()).replace(' ','-')+')\n' for group,_ in MODELS)
    sidebar+='\n## Reference\n\n- [Execution records and downloads]('+BASE+'Execution-records)\n- [API documentation](https://nmltd.github.io/algoquant/)\n- [Example source](https://github.com/nmltd/algoquant-examples)\n- [Technical methodology]('+BASE+'Data-and-methodology)\n- [Reproduce the tutorials]('+BASE+'Reproduce-the-results)\n'
    (wiki/'_Sidebar.md').write_text(sidebar)
    assert len(re.findall(r'^\| \[[^\n]+/wiki/Model-',home,re.M))==len(data)
    assert '| Demo tutorial | Status |' in (wiki/'Execution-records.md').read_text()
    print('Organized %d usage tutorials and %d quantitative model references; execution evidence preserved.'%(len(tutorial_names),len(model_names)))


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--wiki',required=True,type=Path)
    parser.add_argument('--introductions',type=Path,default=Path(__file__).resolve().parents[1]/'docs/model-introductions.json')
    args=parser.parse_args();apply(args.wiki,args.introductions)
