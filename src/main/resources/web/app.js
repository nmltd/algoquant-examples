'use strict';
const $ = id => document.getElementById(id);
const usd = n => new Intl.NumberFormat('en-US',{style:'currency',currency:'USD',maximumFractionDigits:2}).format(n);
const pct = n => (n*100).toFixed(2)+'%';
let current;
let allGroupsOpen = null;
const yesterday = new Date(); yesterday.setDate(yesterday.getDate()-1);
$('end').max = yesterday.toISOString().slice(0,10);
$('start').max = $('end').max;
fetch('/api/stocks').then(r=>{if(!r.ok)throw Error('Stock list unavailable.');return r.json();}).then(stocks=>{
 $('symbol').replaceChildren(...stocks.map(s=>new Option(s,s))); $('run').disabled=false;
 $('status').textContent='Ready. Historical prices are downloaded when you run.';
}).catch(e=>{$('status').textContent=e.message; $('status').className='error';});
$('backtest').addEventListener('submit',async e=>{
 e.preventDefault();
 if(Number($('fast').value)>=Number($('slow').value)){showError('Fast SMA must be smaller than slow SMA.');return;}
 $('run').disabled=true; $('status').className=''; $('status').textContent='Downloading prices and running SMA2. This can take up to two minutes…';
 $('report').hidden=true; $('empty').hidden=false;
 const controller=new AbortController(), timer=setTimeout(()=>controller.abort(),150000);
 try{
  const r=await fetch('/api/backtest',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:new URLSearchParams(new FormData($('backtest'))),signal:controller.signal});
  const data=await r.json(); if(!r.ok)throw Error(data.error||'Backtest failed.');
  current=data; render(data); $('status').textContent='Complete · '+data.series.length+' trading observations.';
 }catch(err){showError(err.name==='AbortError'?'The request timed out. Please retry with a shorter period.':err.message);}
 finally{clearTimeout(timer);$('run').disabled=false;}
});
function showError(msg){$('status').className='error';$('status').textContent=msg;}
function render(r){
 allGroupsOpen=null;
 $('report-title').textContent=r.symbol+' / SMA '+r.fast+' days × '+r.slow+' days';
 $('report-meta').textContent=r.start+' → '+r.end+' · USD · '+r.source;
 $('fast-legend').textContent='Fast SMA · '+r.fast+' days';
 $('slow-legend').textContent='Slow SMA · '+r.slow+' days';
 const values=[['Net profit / loss',usd(r.pnl)],['Total return',pct(r.totalReturn)],['Maximum drawdown',pct(r.maxDrawdown)],['Final portfolio value',usd(r.finalNav)],['Annualized volatility',r.annualizedVolatility==null?'N/A':pct(r.annualizedVolatility)],['Sharpe · risk-free rate 0',r.sharpe==null?'N/A':r.sharpe.toFixed(2)]];
 $('metrics').replaceChildren(...values.map(([title,value])=>{const d=document.createElement('div');d.className='metric';const p=document.createElement('p');p.textContent=title;const s=document.createElement('strong');s.textContent=value;d.append(p,s);return d;}));
 renderLibrary(r);
 chart('prices',r.series,['close','fast','slow'],['#344a52','#188663','#cf9647']);
 chart('nav',r.series,['nav','benchmark'],['#188663','#cf9647']);
 $('fill-count').textContent='('+r.executions.length+')';
 $('fills').replaceChildren(...r.executions.map(f=>{const tr=document.createElement('tr');for(const value of [f.date.replace('T',' '),f.side,f.quantity.toString(),usd(f.price)]){const td=document.createElement('td');td.textContent=value;tr.append(td);}return tr;}));
 $('fill-note').textContent=r.executions.length?'Executions are fills, not completed round-trip trades.':'No crossovers produced executions in this period.';
 $('provenance').textContent='Generated '+r.generatedAt+' · Simple daily returns; sample volatility and Sharpe annualized with 252 sessions. Returns use the full $10,000 starting account, not a fully invested stock position. Prices are adjusted; distributions are not modeled as separate cash payments.';
 $('empty').hidden=true;$('report').hidden=false;
}
function chart(id,data,keys,colors){
 const svg=$(id),NS='http://www.w3.org/2000/svg';svg.replaceChildren();
 const values=data.flatMap(p=>keys.map(k=>p[k])).filter(Number.isFinite);
 let low=Math.min(...values), high=Math.max(...values);let pad=(high-low)*.08||1;low-=pad;high+=pad;
 const x=i=>64+i/(Math.max(1,data.length-1))*718,y=v=>230-(v-low)/(high-low)*210;
 const add=(tag,attrs,text)=>{const n=document.createElementNS(NS,tag);for(const [k,v] of Object.entries(attrs))n.setAttribute(k,v);if(text!=null)n.textContent=text;svg.append(n);return n;};
 for(let i=0;i<5;i++){const v=low+(high-low)*i/4,yy=y(v);add('line',{x1:64,x2:782,y1:yy,y2:yy,class:'grid'});add('text',{x:55,y:yy+3,'text-anchor':'end'},v.toLocaleString('en-US',{maximumFractionDigits:1}));}
 keys.forEach((key,k)=>{let d='';data.forEach((p,i)=>{if(Number.isFinite(p[key]))d+=(d?' L':'M')+x(i).toFixed(2)+','+y(p[key]).toFixed(2);});add('path',{d,stroke:colors[k]});});
 [0,Math.floor((data.length-1)/2),data.length-1].forEach((i,k)=>add('text',{x:x(i),y:256,'text-anchor':k===0?'start':k===2?'end':'middle'},data[i].date));
}
$('download').addEventListener('click',()=>{
 if(!current)return;
 const r=current, lines=['date,adjusted_close,fast_sma,slow_sma,sma2_nav,one_share_benchmark'];
 r.series.forEach(p=>lines.push([p.date,p.close,p.fast,p.slow,p.nav,p.benchmark].join(',')));
 const url=URL.createObjectURL(new Blob([lines.join('\r\n')],{type:'text/csv'}));
 const a=document.createElement('a');a.href=url;a.download=r.symbol+'-sma2-'+r.fast+'-'+r.slow+'.csv';a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);
});

function readableMeasureValue(m){
 if(m.status!=='ok'||!Number.isFinite(m.value))return m.display||'N/A';
 if(m.unit==='fraction')return (m.value*100).toLocaleString('en-US',{maximumFractionDigits:6})+'%';
 return m.value.toLocaleString('en-US',{maximumFractionDigits:8});
}
function element(tag,text,cls){const e=document.createElement(tag);if(text!=null)e.textContent=text;if(cls)e.className=cls;return e;}
function renderLibrary(r){
 $('measure-benchmark').textContent=r.benchmarkSource+' The chart benchmark remains one share of the selected stock.';
 const query=$('measure-filter').value.trim().toLowerCase(),groups=new Map();
 $('clear-search').disabled=!$('measure-filter').value;
 const measures=r.performanceMeasures||[];
 for(const m of measures){
  if(query&&!((m.group+' '+m.label+' '+m.sourceClass+' '+m.unit).toLowerCase().includes(query)))continue;
  if(!groups.has(m.group))groups.set(m.group,[]);groups.get(m.group).push(m);
 }
 $('measure-count').textContent=measures.length+' configured scalar results · '+measures.filter(m=>m.status==='ok').length+' finite · '+measures.filter(m=>m.status==='not_applicable').length+' not applicable. Expand a group for details.';
 const content=[];
 for(const [group,rows] of groups){
  const details=element('details',null,'measure-group'),summary=element('summary',group+' ('+rows.length+')');details.append(summary);
  details.open=!!query||(allGroupsOpen===null?group==='Balances and profit':allGroupsOpen);
  const wrap=element('div',null,'table-wrap'),table=element('table',null,'measure-table'),head=element('thead'),tr=element('tr');
  for(const text of ['Measure','Value','Unit','Library class / parameters'])tr.append(element('th',text));
  head.append(tr);table.append(head);const body=element('tbody');
  for(const m of rows){const row=element('tr');row.append(element('td',m.label),element('td',readableMeasureValue(m)),element('td',m.unit));const note=element('td');note.append(element('code',m.sourceClass),element('p',m.detail||'', 'hint'));if(m.status!=='ok')note.append(element('span',m.status.replaceAll('_',' '),'measure-status'));row.append(note);body.append(row);}
  table.append(body);wrap.append(table);details.append(wrap);content.push(details);
 }
 $('library-measures').replaceChildren(...content);
 const tables=[];
 for(const t of r.periodMeasures||[]){
  const details=element('details',null,'measure-group');details.open=allGroupsOpen===true;details.append(element('summary',t.label+' ('+t.values.length+' periods)'));
  details.append(element('p',t.sourceClass+' · '+t.unit+' · '+t.detail,'hint'));
  const wrap=element('div',null,'table-wrap'),table=element('table'),head=element('thead'),tr=element('tr');
  for(const name of ['Start','End (excluded)','Value'])tr.append(element('th',name));head.append(tr);table.append(head);
  const body=element('tbody');for(const p of t.values.slice(0,200)){const row=element('tr');row.append(element('td',p.start.replace('T',' ')),element('td',p.end.replace('T',' ')),element('td',readableMeasureValue({...p,unit:t.unit})));body.append(row);}
  table.append(body);wrap.append(table);details.append(wrap);tables.push(details);
 }
 $('period-measures').replaceChildren(...tables);
}
$('measure-filter').addEventListener('input',()=>{if(current)renderLibrary(current);});
function saveFile(name,content,type){const u=URL.createObjectURL(new Blob([content],{type})),a=document.createElement('a');a.href=u;a.download=name;a.click();setTimeout(()=>URL.revokeObjectURL(u),1000);}
$('download-report').addEventListener('click',()=>{if(current)saveFile(current.symbol+'-sma2-report.json',JSON.stringify(current,null,2),'application/json');});
$('download-measures').addEventListener('click',()=>{
 if(!current)return;
 const rows=[['kind','group','measure','library_class','unit','status','value','display','parameters_or_reason','start','end']];
 for(const m of current.performanceMeasures)rows.push(['scalar',m.group,m.label,m.sourceClass,m.unit,m.status,m.value??'',m.display,m.detail,'','']);
 for(const t of current.periodMeasures)for(const p of t.values)rows.push(['period',t.label,t.label,t.sourceClass,t.unit,p.status,p.value??'',p.display,t.detail,p.start,p.end]);
 const csv=rows.map(row=>row.map(v=>{let text=String(v??'');if(/^[=+@]/.test(text))text="'"+text;return '"'+text.replaceAll('"','""')+'"';}).join(',')).join('\r\n');
 saveFile(current.symbol+'-sma2-all-measures.csv',csv,'text/csv');
});

function setReportGroups(open){
 allGroupsOpen=open;
 document.querySelectorAll('#library-measures details, #period-measures details').forEach(d=>{d.open=open;});
}
$('expand-all').addEventListener('click',()=>setReportGroups(true));
$('collapse-all').addEventListener('click',()=>setReportGroups(false));
$('clear-search').addEventListener('click',()=>{
 $('measure-filter').value='';
 if(current)renderLibrary(current);
 $('measure-filter').focus();
});
