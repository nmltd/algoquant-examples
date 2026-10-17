#!/usr/bin/env python3
"""Exercise the real HTTP app. Default: deterministic offline prices; --live: Yahoo."""
import json, os, subprocess, sys, time, tempfile, shutil, urllib.request, urllib.parse, urllib.error
live = '--live' in sys.argv
port = '18081' if live else '18080'
report_dir=tempfile.mkdtemp(prefix='sma2-smoke-reports-')
env = dict(os.environ, PORT=port, SMA2_REPORT_DIRECTORY=report_dir)
env.pop('SMA2_REPORT_BUCKET',None)
if not live: env['SMA2_DATA_MODE'] = 'synthetic'
else: env.pop('SMA2_DATA_MODE', None)
proc = subprocess.Popen(['java','-Djava.awt.headless=true','-Dlogback.configurationFile=web/logback.xml',
    '-cp','target/classes:target/lib/*','tech.nmfin.algoquant.examples.web.Sma2Web'], env=env)
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
    for path in ['/','/sma2','/sma2/','/sma2/style.css','/sma2/app.js','/sma2/api/stocks']:
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
    for p in r['series']:
        assert abs(p['benchmark']/r['initialCapital']-p['close']/r['series'][0]['close'])<1e-10
    assert abs(r['executions'][0]['quantity']*r['executions'][0]['price']-10000)<1e-7
    assert len(r['series'])>200 and len(r['executions'])>0
    assert ('SYNTHETIC' in r['source']) != live
    assert abs(r['finalNav']-10000-r['pnl'])<1e-7
    assert len(r['performanceMeasures'])>150 and len(r['periodMeasures'])==12
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
    print('Saved report survived server restart:',r['reportPath'])
    os.makedirs('target/web-reports',exist_ok=True)
    with open('target/web-reports/'+('live' if live else 'offline')+'.json','w') as f: json.dump(r,f,indent=2)
    print('HTTP smoke passed:',r['source'],len(r['series']),'NAV observations;',len(r['executions']),'executions')
finally:
    proc.terminate()
    try: proc.wait(timeout=5)
    except subprocess.TimeoutExpired: proc.kill(); proc.wait()
    shutil.rmtree(report_dir)
