#!/usr/bin/env python3
"""Exercise the real HTTP app. Default: deterministic offline prices; --live: Yahoo."""
import json, os, subprocess, sys, time, urllib.request, urllib.parse, urllib.error
live = '--live' in sys.argv
port = '18081' if live else '18080'
env = dict(os.environ, PORT=port)
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
            if request('/api/health')[0]==200: break
        except OSError: pass
        time.sleep(.1)
    else: raise RuntimeError('Server did not start')
    for path in ['/','/style.css','/app.js','/api/stocks']:
        status,headers,body=request(path); assert status==200 and body,path
        assert 'nosniff'==headers.get('X-Content-Type-Options')
    assert request('/api/backtest')[0]==405
    fields=dict(symbol='AAPL',start='2020-01-01',end='2020-12-31',fast=5,slow=20)
    assert request('/api/backtest',dict(fields,fast=30))[0]==400
    assert request('/api/backtest',dict(fields,symbol='../../etc/passwd'))[0]==400
    status,_,body=request('/api/backtest',fields)
    assert status==200,(status,body)
    r=json.loads(body)
    assert len(r['series'])>200 and len(r['executions'])>0
    assert ('SYNTHETIC' in r['source']) != live
    assert abs(r['finalNav']-10000-r['pnl'])<1e-7
    assert len(r['performanceMeasures'])>150 and len(r['periodMeasures'])==12
    assert any(m['sourceClass']=='HistoricalVaR' for m in r['performanceMeasures'])
    assert any(m['sourceClass']=='SortinoRatio' for m in r['performanceMeasures'])
    assert all(p['date']>='2020-01-01' and p['date']<='2020-12-31' for p in r['series'])
    status,_,body=request('/api/backtest',fields)
    assert status==200 and json.loads(body)['series']==r['series']
    os.makedirs('target/web-reports',exist_ok=True)
    with open('target/web-reports/'+('live' if live else 'offline')+'.json','w') as f: json.dump(r,f,indent=2)
    print('HTTP smoke passed:',r['source'],len(r['series']),'NAV observations;',len(r['executions']),'executions')
finally:
    proc.terminate()
    try: proc.wait(timeout=5)
    except subprocess.TimeoutExpired: proc.kill(); proc.wait()
