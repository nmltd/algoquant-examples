'use strict';
const $ = id => document.getElementById(id);
const usd = n => new Intl.NumberFormat('en-US',{style:'currency',currency:'USD',maximumFractionDigits:2}).format(n);
const pct = n => (n*100).toFixed(2)+'%';
let current;
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
 $('report-title').textContent=r.symbol+' / SMA '+r.fast+' × '+r.slow;
 $('report-meta').textContent=r.start+' → '+r.end+' · USD · '+r.source;
 const values=[['Net profit / loss',usd(r.pnl)],['Total return',pct(r.totalReturn)],['Maximum drawdown',pct(r.maxDrawdown)],['Final portfolio value',usd(r.finalNav)],['Annualized volatility',r.annualizedVolatility==null?'N/A':pct(r.annualizedVolatility)],['Sharpe · risk-free rate 0',r.sharpe==null?'N/A':r.sharpe.toFixed(2)]];
 $('metrics').replaceChildren(...values.map(([title,value])=>{const d=document.createElement('div');d.className='metric';const p=document.createElement('p');p.textContent=title;const s=document.createElement('strong');s.textContent=value;d.append(p,s);return d;}));
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
