'use strict';
const BASE='/performance',$=id=>document.getElementById(id);
let current,groupsOpen=null,range=null,drag=null,hidden=new Set();
const reportPath=r=>BASE+'/reports/'+r.reportId;
const accounts=r=>r.accounts.map(a=>({name:a.name,stats:a.statistics,measures:a.performanceMeasures,valueUnit:'normalized units'}));
function message(text,error=false){$('status').textContent=text;$('status').className=error?'error':'hint analysis-status';}
function renderLibrary(){SharedReport.renderLibrary(accounts(current),current.benchmarkSource+' Values in normalized units; unavailable trade statistics are not estimated.',groupsOpen);}
function render(){
 groupsOpen=null;range=null;hidden.clear();$('measure-filter').value='';$('empty').hidden=true;$('report').hidden=false;
 $('report-title').textContent=current.accounts.filter(a=>!a.benchmark).map(a=>a.name).join(' / ')+' · Performance';
 $('report-meta').textContent=current.start+' → '+current.end+' · '+current.accounts[0].series.length+' common observations · normalized to 10,000';
 $('saved-report-note').textContent='Saved '+current.generatedAt+' · Report '+current.reportId;
 $('report-link').href=reportPath(current);$('source-link').href=current.sourceUrl;$('source-link').textContent=current.sourceUrl;
 $('analysis-notes').replaceChildren(...current.warnings.map(w=>SharedReport.element('li',w)));
 SharedReport.renderComparison(accounts(current),'Same observed dates and 10,000 initial normalized units. First-series differences are descriptive, not rankings.');
 renderLibrary();drawHistory();
 $('csv-series').replaceChildren(...current.accounts.map((a,i)=>new Option(a.name,String(i))));
}
$('analysis-form').addEventListener('submit',async event=>{
 event.preventDefault();$('analyze').disabled=true;$('report').hidden=true;$('empty').hidden=false;
 message('Downloading public data and computing AlgoQuant statistics…');
 const controller=new AbortController(),timer=setTimeout(()=>controller.abort(),160000);
 try{
  const response=await fetch(BASE+'/api/analyze',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:new URLSearchParams(new FormData($('analysis-form'))),signal:controller.signal});
  const data=await response.json();if(!response.ok)throw Error(data.error||'Analysis failed.');
  current=data;history.pushState(null,'',reportPath(current));render();message('Complete · '+current.accounts.length+' accounts compared.');
 }catch(error){message(error.name==='AbortError'?'The analysis timed out. Please retry with a smaller input.':error.message,true);}
 finally{clearTimeout(timer);$('analyze').disabled=false;}
});
async function loadSaved(){
 $('analyze').disabled=true;message('Loading saved report…');
 try{
  const id=location.pathname.split('/')[3];if(!/^[a-f0-9]{32}$/.test(id))throw Error('Invalid report link.');
  const response=await fetch(BASE+'/api/reports/'+id),data=await response.json();if(!response.ok)throw Error(data.error||'Saved report unavailable.');
  current=data;$('source-url').value=current.sourceUrl;$('start').value=current.start;$('end').value=current.end;render();message('Saved report loaded.');
 }catch(error){message(error.message,true);}
 finally{$('analyze').disabled=false;}
}
window.addEventListener('popstate',()=>{if(location.pathname.startsWith(BASE+'/reports/'))loadSaved();else{current=null;$('report').hidden=true;$('empty').hidden=false;}});
$('copy-report-link').onclick=async()=>{if(!current)return;const url=new URL(reportPath(current),location.origin).href;try{await navigator.clipboard.writeText(url);message('Saved report link copied.');}catch(error){message('Saved report: '+url);}};
async function download(format,name){
 if(!current)return;
 try{const response=await fetch(BASE+'/api/reports/'+current.reportId+'/'+format);if(!response.ok){const error=await response.json();throw Error(error.error||'Export failed.');}SharedReport.saveFile(name,await response.blob(),response.headers.get('Content-Type')||'application/octet-stream');}
 catch(error){message(error.message,true);}
}
$('download').onclick=()=>download('report.xlsx','performance-report.xlsx');
$('download-csv').onclick=()=>download('series-'+$('csv-series').value+'.csv','performance-series-'+$('csv-series').value+'.csv');
$('download-json').onclick=()=>{if(current)SharedReport.saveFile('performance-report.json',JSON.stringify(current,null,2),'application/json');};
$('measure-filter').oninput=()=>{if(current)renderLibrary();};
$('clear-search').onclick=()=>{$('measure-filter').value='';if(current)renderLibrary();};
$('expand-all').onclick=()=>{groupsOpen=true;document.querySelectorAll('#library-measures details').forEach(d=>{d.open=true;});};
$('collapse-all').onclick=()=>{groupsOpen=false;document.querySelectorAll('#library-measures details').forEach(d=>{d.open=false;});};
function zoom(factor){if(!current)return;const total=current.accounts[0].series.length,[start,size]=range||[0,total],next=Math.min(total,Math.max(2,Math.round(size*factor)));range=[Math.max(0,Math.min(total-next,start+Math.round((size-next)/2))),next];drawHistory();}
function pan(offset){if(!current)return;const total=current.accounts[0].series.length,[start,size]=range||[0,total];range=[Math.max(0,Math.min(total-size,start+offset)),size];drawHistory();}
function drawHistory(){
 if(!current)return;const svg=$('history'),total=current.accounts[0].series.length,[start,size]=range||[0,total],first=current.accounts[0].series;
 $('history-window').textContent=first[start].date+' → '+first[start+size-1].date+' · '+size+' observations';
 svg.replaceChildren();$('history-legend').replaceChildren();
 const add=(tag,attrs,text)=>{const n=document.createElementNS('http://www.w3.org/2000/svg',tag);for(const [k,v]of Object.entries(attrs))n.setAttribute(k,v);if(text!=null)n.textContent=text;svg.append(n);return n;};
 const values=current.accounts.flatMap((a,i)=>hidden.has(i)?[]:a.series.slice(start,start+size).map(p=>p.nav));let low=values.length?Math.min(...values):0,high=values.length?Math.max(...values):1;if(high===low)high=low+1;
 const x=i=>55+i/(size-1)*710,y=v=>250-(v-low)/(high-low)*220;
 for(let i=0;i<4;i++){const v=low+(high-low)*i/3;add('line',{x1:55,x2:765,y1:y(v),y2:y(v),stroke:'#dce4e0'});add('text',{x:50,y:y(v)+4,'text-anchor':'end','font-size':10,fill:'#657a7c'},SharedReport.value(v,'normalized units'));}
 current.accounts.forEach((a,k)=>{
  const button=SharedReport.element('button',a.name,'series-'+k);button.type='button';button.setAttribute('aria-pressed',String(!hidden.has(k)));button.onclick=()=>{hidden.has(k)?hidden.delete(k):hidden.add(k);drawHistory();};$('history-legend').append(button);
  if(hidden.has(k))return;
  const d=a.series.slice(start,start+size).map((p,i)=>(i?'L':'M')+x(i).toFixed(2)+','+y(p.nav).toFixed(2)).join(' ');
  add('path',{d,fill:'none',stroke:SharedReport.colors[k%SharedReport.colors.length],'stroke-width':2});
 });
 add('text',{x:55,y:280,'font-size':11,fill:'#657a7c'},first[start].date);add('text',{x:765,y:280,'text-anchor':'end','font-size':11,fill:'#657a7c'},first[start+size-1].date);
 svg.onpointerdown=event=>{drag={x:event.clientX,start,size};svg.setPointerCapture(event.pointerId);};
 svg.onpointerup=()=>{drag=null;};
 svg.onpointermove=event=>{
  const box=svg.getBoundingClientRect(),index=Math.max(0,Math.min(size-1,Math.round(((event.clientX-box.left)/box.width*800-55)/710*(size-1))))+start;
  $('history-hover').textContent=first[index].date+' · '+current.accounts.map(a=>a.name+': '+SharedReport.value(a.series[index].nav,'normalized units')).join(' · ');
  if(drag){const moved=Math.round((drag.x-event.clientX)/box.width*drag.size);range=[Math.max(0,Math.min(total-drag.size,drag.start+moved)),drag.size];drawHistory();}
 };
 svg.onwheel=event=>{if(event.ctrlKey||event.metaKey){event.preventDefault();zoom(event.deltaY<0?.7:1.4);}};
 svg.onkeydown=event=>{if(['+','=','-','ArrowLeft','ArrowRight','Home'].includes(event.key)){event.preventDefault();if(event.key==='Home'){range=null;drawHistory();}else if(event.key==='ArrowLeft'||event.key==='ArrowRight')pan(Math.max(1,Math.round(size*.15))*(event.key==='ArrowLeft'?-1:1));else zoom(event.key==='-'?1.4:.7);}};
 svg.ondblclick=()=>{range=null;drawHistory();};
}
$('history-in').onclick=()=>zoom(.7);$('history-out').onclick=()=>zoom(1.4);$('history-reset').onclick=()=>{range=null;drawHistory();};
SharedReport.installPrint({
 getReport:()=>current,title:r=>'Performance Analyzer '+r.start+' to '+r.end,url:r=>new URL(reportPath(r),location.origin).href,
 prepare:()=>{const state={filter:$('measure-filter').value,groups:groupsOpen,details:new Map([...document.querySelectorAll('#report details')].map(d=>[d.querySelector('summary')?.textContent,d.open])),range,hidden:new Set(hidden),comparison:[...document.querySelectorAll('#comparison-charts button')].map(b=>b.getAttribute('aria-pressed'))};$('measure-filter').value='';groupsOpen=true;renderLibrary();document.querySelectorAll('#report details').forEach(d=>{d.open=true;});range=null;hidden.clear();drawHistory();SharedReport.renderComparison(accounts(current),'All accounts start at 10,000 normalized units.');return state;},
 restore:state=>{$('measure-filter').value=state.filter;groupsOpen=state.groups;renderLibrary();document.querySelectorAll('#report details').forEach(d=>{d.open=state.details.get(d.querySelector('summary')?.textContent)??false;});range=state.range;hidden=state.hidden;drawHistory();document.querySelectorAll('#comparison-charts button').forEach((b,i)=>{if(state.comparison[i]==='false')b.click();});}
});
$('export-pdf').onclick=()=>{if(current)window.print();};
if(location.pathname.startsWith(BASE+'/reports/'))loadSaved();
