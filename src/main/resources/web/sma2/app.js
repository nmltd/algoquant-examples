'use strict';
const BASE='/sma2';
const reportPath = r => BASE+'/reports/'+r.reportId;
const $ = id => document.getElementById(id);
const usd = n => new Intl.NumberFormat('en-US',{style:'currency',currency:'USD',maximumFractionDigits:2}).format(n);
const decimal = n => n.toLocaleString('en-US',{minimumFractionDigits:2,maximumFractionDigits:2});
const pct = n => (n*100).toFixed(2)+'%';
let current;
let allGroupsOpen = null;
// Use the US market's calendar date, independent of the visitor's timezone.
function previousBusinessDay(now = new Date()) {
 const parts = new Intl.DateTimeFormat('en-US',{timeZone:'America/New_York',year:'numeric',month:'2-digit',day:'2-digit'}).formatToParts(now);
 const part = type => parts.find(p=>p.type===type).value;
 const day = new Date(part('year')+'-'+part('month')+'-'+part('day')+'T00:00:00Z');
 do { day.setUTCDate(day.getUTCDate()-1); } while(day.getUTCDay()===0 || day.getUTCDay()===6);
 return day.toISOString().slice(0,10);
}
$('end').max = previousBusinessDay();
$('end').value = $('end').max;
$('start').max = $('end').max;
fetch(BASE+'/api/stocks').then(r=>{if(!r.ok)throw Error('Stock list unavailable.');return r.json();}).then(stocks=>{
 $('symbol').replaceChildren(...stocks.map(s=>new Option(s,s)));
 $('run').disabled=false;
 if(location.pathname.startsWith(BASE+'/reports/'))return loadSavedReport();
 $('status').textContent='Ready. Historical prices are downloaded when you run.';
}).catch(e=>{$('status').textContent=e.message; $('status').className='error';});
$('backtest').addEventListener('submit',async e=>{
 e.preventDefault();
 if(Number($('fast').value)>=Number($('slow').value)){showError('Fast SMA must be smaller than slow SMA.');return;}
 $('run').disabled=true; $('status').className=''; $('status').textContent='Downloading prices and running SMA2. This can take up to two minutes…';
 $('report').hidden=true; $('empty').hidden=false;
 const controller=new AbortController(), timer=setTimeout(()=>controller.abort(),150000);
 try{
  const r=await fetch(BASE+'/api/backtest',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:new URLSearchParams(new FormData($('backtest'))),signal:controller.signal});
  const data=await r.json(); if(!r.ok)throw Error(data.error||'Backtest failed.');
  history.pushState(null,'',reportPath(data));
  current=data; render(data); $('status').textContent='Complete · '+data.series.length+' trading observations.';
 }catch(err){showError(err.name==='AbortError'?'The request timed out. Please retry with a shorter period.':err.message);}
 finally{clearTimeout(timer);$('run').disabled=false;}
});
function showError(msg){$('status').className='error';$('status').textContent=msg;}
function render(r){
 allGroupsOpen=null;chartRange=null;chartDrag=null;
 $('measure-filter').value='';
 const full=r.sizingModel==='FULL_EQUITY_V1';
 $('report-sizing-note').textContent=full
  ? 'Full-equity sizing: 100% long or short at entry/reversals; fractional shares. Buy & hold invests the full $10,000.'
  : 'Legacy saved report: fixed ±1-share strategy and one-share buy & hold. These original numbers are unchanged. Run a new backtest for full-equity results.';
 $('benchmark-legend').textContent=full?'Buy & hold · fully invested':'Buy & hold · one share (legacy)';
 chartDefinitions.nav.labels[1]=full?'Fully invested buy & hold':'One-share buy & hold (legacy)';
 $('nav').setAttribute('aria-label','SMA2 portfolio value versus '+(full?'fully invested':'legacy one-share')+' buy and hold');
 $('benchmark-convention').textContent=full
  ? 'Both start with $10,000. Buy & hold invests all capital at the first selected adjusted close and holds fractional shares. Its account return equals the stock’s adjusted-price return.'
  : 'Legacy benchmark buys one share at the first selected close; remaining cash stays idle.';
 $('report-link').href=reportPath(r);
 $('saved-report-note').textContent='Saved '+r.generatedAt+' · Report '+r.reportId;
 $('report-title').textContent=r.symbol+' / SMA '+r.fast+' days × '+r.slow+' days';
 $('report-meta').textContent=r.start+' → '+r.end+' · USD · '+r.source;
 $('fast-legend').textContent='Fast SMA · '+r.fast+' days';
 $('slow-legend').textContent='Slow SMA · '+r.slow+' days';
 const values=[['Net profit / loss',usd(r.pnl)],['Total return',pct(r.totalReturn)],['Maximum drawdown',pct(r.maxDrawdown)],['Final portfolio value',usd(r.finalNav)],['Annualized volatility',r.annualizedVolatility==null?'N/A':pct(r.annualizedVolatility)],['Sharpe · risk-free rate 0',r.sharpe==null?'N/A':r.sharpe.toFixed(2)]];
 values.push(['Buy & hold return',pct(r.series[r.series.length-1].benchmark/r.initialCapital-1)]);
 $('metrics').replaceChildren(...values.map(([title,value])=>{const d=document.createElement('div');d.className='metric';const p=document.createElement('p');p.textContent=title;const s=document.createElement('strong');s.textContent=value;d.append(p,s);return d;}));
 renderLibrary(r);
 renderComparison(r);
 chart('prices',r.series,['close','fast','slow'],['#344a52','#188663','#cf9647']);
 chart('nav',r.series,['nav','benchmark'],['#188663','#cf9647']);
 $('fill-count').textContent='('+r.executions.length+')';
 $('fills').replaceChildren(...r.executions.map(f=>{const tr=document.createElement('tr');for(const value of [f.date.replace('T',' '),f.side,decimal(f.quantity),usd(f.price)]){const td=document.createElement('td');td.textContent=value;tr.append(td);}return tr;}));
 $('fill-note').textContent=r.executions.length?'Executions are fills, not completed round-trip trades.':'No crossovers produced executions in this period.';
 $('provenance').textContent='Generated '+r.generatedAt+' · Simple daily returns; sample volatility and Sharpe annualized with 252 sessions. Account returns use $10,000 initial equity. Position sizing is stated above. Prices are adjusted; distributions are not modeled as separate cash payments.';
 $('empty').hidden=true;$('report').hidden=false;
}
// Shared date window keeps the price and NAV charts aligned.
let chartRange=null, chartDrag=null;
const hiddenSeries={prices:new Set(),nav:new Set()};
const chartDefinitions={
 prices:{keys:['close','fast','slow'],colors:['#344a52','#188663','#cf9647'],labels:['Adjusted close','Fast SMA','Slow SMA']},
 nav:{keys:['nav','benchmark'],colors:['#188663','#cf9647'],labels:['SMA2 portfolio','One-share buy & hold']}
};
function drawCharts(){
 if(!current)return;
 for(const [id,c] of Object.entries(chartDefinitions))chart(id,current.series,c.keys,c.colors);
}
function setChartRange(start,size){
 const total=current.series.length;size=Math.max(2,Math.min(total,Math.round(size)));
 start=Math.max(0,Math.min(total-size,Math.round(start)));chartRange=[start,start+size-1];drawCharts();
}
function zoomCharts(factor,anchor=.5){
 const range=chartRange||[0,current.series.length-1],size=range[1]-range[0]+1;
 const next=Math.max(2,Math.min(current.series.length,Math.round(size*factor)));
 setChartRange(range[0]+(size-next)*anchor,next);
}
function chartAction(action){
 if(!current)return;
 const range=chartRange||[0,current.series.length-1],size=range[1]-range[0]+1;
 if(action==='in')zoomCharts(.5);
 else if(action==='out')zoomCharts(2);
 else if(action==='reset'){chartRange=null;drawCharts();}
 else setChartRange(range[0]+(action==='left'?-1:1)*Math.max(1,Math.round(size*.25)),size);
}
document.querySelectorAll('[data-chart-action]').forEach(button=>button.addEventListener('click',()=>chartAction(button.dataset.chartAction)));
function chart(id,data,keys,colors){
 const svg=$(id),NS='http://www.w3.org/2000/svg';svg.replaceChildren();
 const [lo,hi]=chartRange||[0,data.length-1],visible=data.slice(lo,hi+1),size=hi-lo+1,hidden=hiddenSeries[id];
 const labels=chartDefinitions[id].labels;
 const active=keys.filter(k=>!hidden.has(k));
 let values=visible.flatMap(p=>(active.length?active:keys).map(k=>p[k])).filter(Number.isFinite);
 let low=Math.min(...values),high=Math.max(...values);const pad=(high-low)*.08||1;low-=pad;high+=pad;
 const x=i=>64+(i-lo)/Math.max(1,hi-lo)*718,y=v=>230-(v-low)/(high-low)*210;
 const add=(tag,attrs,text,parent=svg)=>{const n=document.createElementNS(NS,tag);for(const [k,v]of Object.entries(attrs))n.setAttribute(k,v);if(text!=null)n.textContent=text;parent.append(n);return n;};
 for(let i=0;i<5;i++){const v=low+(high-low)*i/4,yy=y(v);add('line',{x1:64,x2:782,y1:yy,y2:yy,class:'grid'});add('text',{x:55,y:yy+3,'text-anchor':'end'},decimal(v));}
 keys.forEach((key,k)=>{
  if(hidden.has(key))return;
  let d='';visible.forEach((p,j)=>{if(Number.isFinite(p[key]))d+=(d?' L':'M')+x(lo+j).toFixed(2)+','+y(p[key]).toFixed(2);});
  add('path',{d,stroke:colors[k]});
 });
 [lo,Math.floor((lo+hi)/2),hi].forEach((i,k)=>add('text',{x:x(i),y:256,'text-anchor':k===0?'start':k===2?'end':'middle'},data[i].date));
 const panel=svg.closest('.chart-panel'),tooltip=$(id+'-hover');
 $(id+'-window').textContent=data[lo].date+' → '+data[hi].date+' · '+size+' trading observations';
 tooltip.textContent='Hover or tap the chart to inspect a trading session.';
 panel.querySelectorAll('[data-chart-action]').forEach(b=>{
  b.disabled=(b.dataset.chartAction==='in'&&size<=2)||(b.dataset.chartAction==='out'&&size===data.length)||(b.dataset.chartAction==='left'&&lo===0)||(b.dataset.chartAction==='right'&&hi===data.length-1);
 });
 panel.querySelectorAll('[data-series]').forEach(button=>{
  const key=button.dataset.series;button.setAttribute('aria-pressed',String(!hidden.has(key)));
  button.onclick=()=>{hidden.has(key)?hidden.delete(key):hidden.add(key);drawCharts();};
 });
 const cross=add('g',{'pointer-events':'none',visibility:'hidden'});
 const crossLine=add('line',{x1:64,x2:64,y1:20,y2:230,stroke:'#80958c','stroke-dasharray':'4 3'},null,cross);
 const dots=keys.map((key,k)=>add('circle',{r:4,fill:colors[k],stroke:'#fff','stroke-width':1},null,cross));
 const fraction=e=>Math.max(0,Math.min(1,((e.clientX-svg.getBoundingClientRect().left)/svg.getBoundingClientRect().width*800-64)/718));
 function hover(e){
  const i=Math.round(lo+fraction(e)*(hi-lo)),point=data[i];cross.setAttribute('visibility','visible');
  crossLine.setAttribute('x1',x(i));crossLine.setAttribute('x2',x(i));
  const text=[point.date];
  keys.forEach((key,k)=>{const v=point[key],show=!hidden.has(key)&&Number.isFinite(v);dots[k].setAttribute('visibility',show?'visible':'hidden');if(show){dots[k].setAttribute('cx',x(i));dots[k].setAttribute('cy',y(v));text.push(labels[k]+': $'+decimal(v));}});
  tooltip.textContent=text.join(' · ');
 }
 svg.onpointerdown=e=>{
  if(e.button!==0&&e.pointerType==='mouse')return;
  chartDrag={id,startX:e.clientX,range:[lo,hi]};svg.setPointerCapture(e.pointerId);hover(e);
 };
 svg.onpointermove=e=>{
  if(chartDrag&&chartDrag.id===id){
   const range=chartDrag.range,span=range[1]-range[0]+1;
   const delta=(e.clientX-chartDrag.startX)/svg.getBoundingClientRect().width*800/718*Math.max(1,span-1);
   setChartRange(range[0]-delta,span);
  } else hover(e);
 };
 svg.onpointerup=e=>{chartDrag=null;if(svg.hasPointerCapture(e.pointerId))svg.releasePointerCapture(e.pointerId);hover(e);};
 svg.onpointercancel=()=>{chartDrag=null;};
 svg.onpointerleave=()=>{if(!chartDrag){cross.setAttribute('visibility','hidden');tooltip.textContent='Hover or tap the chart to inspect a trading session.';}};
 svg.onwheel=e=>{if(e.ctrlKey||e.metaKey){e.preventDefault();zoomCharts(e.deltaY<0?.8:1.25,fraction(e));}};
 svg.ondblclick=()=>chartAction('reset');
 svg.onkeydown=e=>{
  const actions={'+':'in','=':'in','-':'out',ArrowLeft:'left',ArrowRight:'right',Home:'reset'};
  if(actions[e.key]){e.preventDefault();chartAction(actions[e.key]);}
 };
}

async function downloadPerformance(strategy){
 if(!current)return;
 try{
  const response=await fetch(BASE+'/api/reports/'+current.reportId+'/'+strategy+'.csv');
  if(!response.ok){const error=await response.json();throw Error(error.error||'CSV export failed.');}
  saveFile(current.symbol+'-'+strategy+'-performance.csv',await response.text(),'text/csv');
 }catch(error){showError(error.message);}
}
$('download-sma2-csv').addEventListener('click',()=>downloadPerformance('sma2'));
$('download').addEventListener('click',async()=>{
 if(!current)return;
 try{
  const response=await fetch(BASE+'/api/reports/'+current.reportId+'/report.xlsx');
  if(!response.ok){const error=await response.json();throw Error(error.error||'Excel export failed.');}
  saveFile(current.symbol+'-sma2-backtest.xlsx',await response.blob(),'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet');
 }catch(error){showError(error.message);}
});

function readableMeasureValue(m){return SharedReport.readable(m);}
function element(tag,text,cls){return SharedReport.element(tag,text,cls);}
function reportAccounts(r){
 const accounts=[{name:'SMA2',stats:r.sma2Statistics,measures:r.performanceMeasures},{name:r.symbol+' buy & hold',stats:r.buyHoldStatistics,measures:r.buyHoldPerformanceMeasures||[]}];
 if(r.sp500)accounts.push({name:'S&P 500',stats:r.sp500.statistics,measures:r.sp500.performanceMeasures});
 return accounts;
}
function renderLibrary(r){SharedReport.renderLibrary(reportAccounts(r),r.benchmarkSource+' Summary measures use the same conventions for all accounts.',allGroupsOpen);}
$('measure-filter').addEventListener('input',()=>{if(current)renderLibrary(current);});
function saveFile(name,content,type){SharedReport.saveFile(name,content,type);}
$('download-report').addEventListener('click',()=>{if(current)saveFile(current.symbol+'-sma2-report.json',JSON.stringify(current,null,2),'application/json');});
$('download-measures').addEventListener('click',()=>downloadPerformance('buy-hold'));

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

async function loadSavedReport(){
 $('run').disabled=true;$('status').className='';$('status').textContent='Loading saved report…';
 try{
  const id=location.pathname.split('/')[3];
  if(!/^[a-f0-9]{32}$/.test(id))throw Error('Invalid report link.');
  const response=await fetch(BASE+'/api/reports/'+id);
  const report=await response.json();if(!response.ok)throw Error(report.error||'Saved report unavailable.');
  current=report;
  for(const field of ['start','end','fast','slow'])$(field).value=report[field];
  $('symbol').value=report.symbol;
  render(report);$('status').textContent='Saved report loaded · '+report.series.length+' trading observations.';
 }catch(e){current=null;$('report').hidden=true;$('empty').hidden=false;showError(e.message);}
 finally{$('run').disabled=false;}
}
window.addEventListener('popstate',()=>{
 if(location.pathname.startsWith(BASE+'/reports/'))loadSavedReport();
 else{current=null;$('report').hidden=true;$('empty').hidden=false;$('status').textContent='Ready for a new backtest.';}
});
$('copy-report-link').addEventListener('click',async()=>{
 if(!current)return;
 const link=new URL(reportPath(current),location.origin).href;
 try{await navigator.clipboard.writeText(link);$('status').className='';$('status').textContent='Saved report link copied.';}
 catch(e){$('status').textContent='Copy the saved report link: '+link;}
});

function renderComparison(r){
 const available=!!(r.sma2Statistics&&r.buyHoldStatistics&&r.buyHoldPerformanceMeasures);
 $('comparison-panel').hidden=!available;
 if(!available){$('comparison-note').textContent='This saved report predates strategy comparisons. Run a new backtest to calculate and save all statistics.';return;}
 SharedReport.renderComparison(reportAccounts(r),r.sp500?'Three independently funded accounts; same requested dates, $10,000 starting equity and zero costs. S&P 500 is Yahoo ^GSPC, a price index without reinvested dividends.':'This saved report has no S&P 500 simulation. Run a new backtest for the three-way comparison.');
}
SharedReport.installPrint({
 getReport:()=>current,
 title:r=>r.symbol+' SMA2 backtest '+r.start+' to '+r.end,
 url:r=>new URL(reportPath(r),location.origin).href,
 prepare:()=>{
  const saved={filter:$('measure-filter').value,groups:allGroupsOpen,details:new Map([...document.querySelectorAll('#report details')].map(d=>[d.querySelector('summary')?.textContent,d.open])),range:chartRange,prices:new Set(hiddenSeries.prices),nav:new Set(hiddenSeries.nav),comparison:[...document.querySelectorAll('.comparison-legend button')].map(b=>b.getAttribute('aria-pressed'))};
  $('measure-filter').value='';allGroupsOpen=true;renderLibrary(current);document.querySelectorAll('#report details').forEach(d=>{d.open=true;});
  chartRange=null;hiddenSeries.prices.clear();hiddenSeries.nav.clear();drawCharts();renderComparison(current);return saved;
 },
 restore:saved=>{
  $('measure-filter').value=saved.filter;allGroupsOpen=saved.groups;renderLibrary(current);
  document.querySelectorAll('#report details').forEach(d=>{d.open=saved.details.get(d.querySelector('summary')?.textContent)??false;});
  chartRange=saved.range;hiddenSeries.prices=saved.prices;hiddenSeries.nav=saved.nav;drawCharts();
  document.querySelectorAll('.comparison-legend button').forEach((b,i)=>{if(saved.comparison[i]==='false')b.click();});
 }
});
$('export-pdf').addEventListener('click',()=>{if(current)window.print();});
