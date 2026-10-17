#!/usr/bin/env python3
"""Exercise the real HTTP app. Default: deterministic offline prices; --live: Yahoo."""
import zipfile, xml.etree.ElementTree as ET, datetime, csv, io, math, json, os, subprocess, sys, time, tempfile, shutil, urllib.request, urllib.parse, urllib.error
live = '--live' in sys.argv
port = '18081' if live else '18080'
report_dir=tempfile.mkdtemp(prefix='sma2-smoke-reports-')
env = dict(os.environ, PORT=port, SMA2_REPORT_DIRECTORY=report_dir)
env.pop('SMA2_REPORT_BUCKET',None)
if not live: env['SMA2_DATA_MODE'] = 'synthetic'
else: env.pop('SMA2_DATA_MODE', None)
proc = subprocess.Popen(['java','-Djava.awt.headless=true','-Dlogback.configurationFile=web/logback.xml',
    '-cp','target/classes:target/lib/*','tech.nmfin.algoquant.web.ToolsWeb'], env=env)
base='http://127.0.0.1:'+port

def request(path, fields=None):
    data=urllib.parse.urlencode(fields).encode() if fields is not None else None
    req=urllib.request.Request(base+path, data=data)
    try:
        with urllib.request.urlopen(req,timeout=140) as r: return r.status, r.headers, r.read()
    except urllib.error.HTTPError as r: return r.code, r.headers, r.read()
try:
    for _ in range(100):
        if proc.poll() is not None: raise RuntimeError('Server exited')
        try:
            if request('/sma2/api/health')[0]==200: break
        except OSError: pass
        time.sleep(.1)
    else: raise RuntimeError('Server did not start')
    for path in ['/','/sma2','/sma2/','/tools.css','/sma2/style.css','/sma2/app.js','/sma2/api/stocks']:
        status,headers,body=request(path); assert status==200 and body,path
        assert 'nosniff'==headers.get('X-Content-Type-Options')
    assert b'Open SMA2' in request('/')[2]
    assert b'id="backtest"' in request('/sma2/')[2]
    assert request('/sma2/api/backtest')[0]==405
    fields=dict(symbol='AAPL',start='2020-01-01',end='2020-12-31',fast=5,slow=20)
    assert request('/sma2/api/backtest',dict(fields,fast=30))[0]==400
    assert request('/sma2/api/backtest',dict(fields,symbol='../../etc/passwd'))[0]==400
    status,_,body=request('/sma2/api/backtest',fields)
    assert status==200,(status,body)
    r=json.loads(body)
    assert r['sizingModel']=='FULL_EQUITY_V1'
    sp=r['sp500']
    assert sp['symbol']=='^GSPC' and sp['start']==r['start'] and sp['end']==r['end']
    assert len(sp['performanceMeasures'])==len(r['performanceMeasures']) and len(sp['executions'])==1
    assert abs(sp['executions'][0]['quantity']*sp['executions'][0]['price']-r['initialCapital'])<1e-7
    for point in sp['series']:
        assert abs(point['nav']/r['initialCapital']-point['close']/sp['series'][0]['close'])<1e-10
    assert abs(sp['statistics']['totalReturn']-(sp['series'][-1]['close']/sp['series'][0]['close']-1))<1e-10
    assert sp['statistics']['totalReturn']!=r['buyHoldStatistics']['totalReturn']
    for p in r['series']:
        assert abs(p['benchmark']/r['initialCapital']-p['close']/r['series'][0]['close'])<1e-10
    assert abs(r['executions'][0]['quantity']*r['executions'][0]['price']-10000)<1e-7
    assert len(r['series'])>200 and len(r['executions'])>0
    assert ('SYNTHETIC' in r['source']) != live
    assert abs(r['finalNav']-10000-r['pnl'])<1e-7
    assert len(r['performanceMeasures'])>150 and len(r['periodMeasures'])==0
    assert len(r['buyHoldPerformanceMeasures'])==len(r['performanceMeasures'])
    assert len(r['buyHoldExecutions'])==1
    assert abs(r['buyHoldStatistics']['totalReturn']-r['benchmarkTotalReturn'])<1e-10
    assert any(m['sourceClass']=='HistoricalVaR' for m in r['performanceMeasures'])
    assert any(m['sourceClass']=='SortinoRatio' for m in r['performanceMeasures'])
    assert all(p['date']>='2020-01-01' and p['date']<='2020-12-31' for p in r['series'])
    status,_,body=request('/sma2/api/backtest',fields)
    assert status==200 and json.loads(body)['series']==r['series']
    assert json.loads(body)['reportId'] != r['reportId']
    assert r['reportPath'].startswith('/sma2/reports/')
    assert request('/reports/'+r['reportId'])[0]==200
    assert request(r['reportPath'])[0] == 200
    assert json.loads(request('/sma2/api/reports/'+r['reportId'])[2]) == r
    assert request('/sma2/api/reports/00000000000000000000000000000000')[0] == 404
    assert request('/sma2/api/reports/invalid-id')[0] == 400
    # CSVs must contain the exact saved library values for both funded accounts.
    csv_bytes={}
    for name,key in [('sma2','performanceMeasures'),('buy-hold','buyHoldPerformanceMeasures')]:
        status,headers,body=request('/sma2/api/reports/'+r['reportId']+'/'+name+'.csv')
        assert status==200 and headers.get_content_type()=='text/csv'
        assert 'attachment;' in headers['Content-Disposition']
        rows=list(csv.reader(io.StringIO(body.decode())))
        assert len(rows)==2 and rows[0][0]=='Product' and rows[1][0]=='Total'
        assert len(rows[0])==len(r[key])+1
        for index,metric in enumerate(r[key],1):
            assert metric['label'] in rows[0][index] and 'status='+metric['status'] in rows[0][index]
            value=float(rows[1][index])
            if metric['status']=='ok' and metric.get('value') is not None:
                assert math.isclose(value,metric['value'],rel_tol=1e-12,abs_tol=1e-12)
            else: assert math.isnan(value)
        csv_bytes[name]=body
    status,headers,xlsx=request('/sma2/api/reports/'+r['reportId']+'/report.xlsx')
    assert status==200 and headers.get_content_type()=='application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
    ns={'s':'http://schemas.openxmlformats.org/spreadsheetml/2006/main'}
    with zipfile.ZipFile(io.BytesIO(xlsx)) as z:
        shared=[''.join(si.itertext()) for si in ET.fromstring(z.read('xl/sharedStrings.xml'))] if 'xl/sharedStrings.xml' in z.namelist() else []
        sheets=ET.fromstring(z.read('xl/workbook.xml')).findall('s:sheets/s:sheet',ns)
        assert [s.attrib['name'] for s in sheets]==['SMA2 statistics','Buy and hold statistics','S&P 500 statistics','Daily data']
        def cells(index):
            root=ET.fromstring(z.read('xl/worksheets/sheet'+str(index)+'.xml'))
            assert root.find('s:sheetViews/s:sheetView/s:pane',ns).attrib['state']=='frozen'
            assert root.find('s:autoFilter',ns) is not None
            result={}
            for cell in root.findall('.//s:sheetData/s:row/s:c',ns):
                v=cell.find('s:v',ns)
                if cell.attrib.get('t')=='inlineStr':result[cell.attrib['r']]=''.join(cell.find('s:is',ns).itertext())
                elif v is not None:result[cell.attrib['r']]=shared[int(v.text)] if cell.attrib.get('t')=='s' else float(v.text)
            return result
        for sheet,metrics in [(1,r['performanceMeasures']),(2,r['buyHoldPerformanceMeasures']),(3,sp['performanceMeasures'])]:
            values=cells(sheet)
            for index,m in enumerate(metrics,2):
                assert values['A'+str(index)]==m['label']
                expected=m.get('value') if m['status']=='ok' else None
                actual=values['B'+str(index)]
                assert (actual=='N/A' if expected is None else math.isclose(actual,expected,rel_tol=1e-12,abs_tol=1e-12))
        daily=cells(4)
        assert [daily[c+'1'] for c in 'ABCDEFGH']==['date','adjusted_close','fast_sma','slow_sma','sma2_nav','fully_invested_buy_hold_nav','sp500_close','sp500_nav']
        market_by_date={p['date']:p for p in sp['series']}
        for index,p in enumerate(r['series'],2):
            benchmark=market_by_date.get(p['date'])
            for col,key in [('G','close'),('H','nav')]:
                actual=daily.get(col+str(index))
                assert (actual is None if benchmark is None else math.isclose(actual,benchmark[key],rel_tol=1e-12,abs_tol=1e-12))
            assert daily['A'+str(index)]==(datetime.date.fromisoformat(p['date'])-datetime.date(1899,12,30)).days
            for col,key in zip('BCDEF',['close','fast','slow','nav','benchmark']):
                actual=daily.get(col+str(index));expected=p.get(key)
                assert (actual is None if expected is None else math.isclose(actual,expected,rel_tol=1e-12,abs_tol=1e-12))
    # Restart the actual HTTP process; the original snapshot must still load unchanged.
    command=proc.args
    proc.terminate();proc.wait(timeout=5)
    proc=subprocess.Popen(command,env=env)
    for _ in range(100):
        try:
            if request('/sma2/api/health')[0]==200:break
        except OSError:pass
        time.sleep(.1)
    else:raise RuntimeError('Server did not restart')
    status,_,saved=request('/sma2/api/reports/'+r['reportId'])
    assert status==200 and json.loads(saved)==r
    assert request(r['reportPath'])[0]==200
    assert request('/sma2/api/reports/'+r['reportId']+'/report.xlsx')[0]==200
    for name,original in csv_bytes.items():
        assert request('/sma2/api/reports/'+r['reportId']+'/'+name+'.csv')[2]==original
    print('Saved report survived server restart:',r['reportPath'])
    os.makedirs('target/web-reports',exist_ok=True)
    with open('target/web-reports/report.xlsx','wb') as f:f.write(xlsx)
    for name,content in csv_bytes.items():
        with open('target/web-reports/'+name+'.csv','wb') as f:f.write(content)
    with open('target/web-reports/'+('live' if live else 'offline')+'.json','w') as f: json.dump(r,f,indent=2)
    print('HTTP smoke passed:',r['source'],len(r['series']),'NAV observations;',len(r['executions']),'executions')
finally:
    proc.terminate()
    try: proc.wait(timeout=5)
    except subprocess.TimeoutExpired: proc.kill(); proc.wait()
    shutil.rmtree(report_dir)
