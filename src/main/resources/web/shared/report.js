'use strict';
window.SharedReport=(()=>{
 const $=id=>document.getElementById(id),decimal=n=>n.toLocaleString('en-US',{minimumFractionDigits:2,maximumFractionDigits:2});
 const pct=n=>(n*100).toFixed(2)+'%',usd=n=>new Intl.NumberFormat('en-US',{style:'currency',currency:'USD',maximumFractionDigits:2}).format(n);
 const colors=['#188663','#cf9647','#426db4','#9a5ca8','#d16b57','#608864','#796f4f','#4e97a2','#8b5964','#646aaf','#9a7b35'];
 const key=m=>[m.group,m.label,m.sourceClass,m.unit].join('|');
 function element(tag,text,cls){const e=document.createElement(tag);if(text!=null)e.textContent=text;if(cls)e.className=cls;return e;}
 function value(n,unit){return !Number.isFinite(n)?'N/A':unit==='fraction'?pct(n):unit==='USD'?usd(n):decimal(n);}
 function readable(m){return m.status!=='ok'||!Number.isFinite(m.value)?m.display||'N/A':value(m.value,m.unit);}
 function saveFile(name,content,type){const u=URL.createObjectURL(new Blob([content],{type})),a=document.createElement('a');a.href=u;a.download=name;a.click();setTimeout(()=>URL.revokeObjectURL(u),1000);}
 function renderLibrary(accounts,source,allOpen){
  $('measure-benchmark').textContent=source;
  const query=$('measure-filter').value.trim().toLowerCase(),groups=new Map(),maps=accounts.map(a=>new Map((a.measures||[]).map(m=>[key(m),m])));
  $('clear-search').disabled=!query;
  const metrics=accounts[0]?.measures||[];
  for(const m of metrics){if(query&&!((m.group+' '+m.label+' '+m.sourceClass+' '+m.unit).toLowerCase().includes(query)))continue;if(!groups.has(m.group))groups.set(m.group,[]);groups.get(m.group).push(m);}
  $('measure-count').textContent=metrics.length+' scalar results per account. Long period tables are omitted; unsupported statistics retain their status.';
  const content=[];
  for(const [group,rows]of groups){
   const details=element('details',null,'measure-group');details.append(element('summary',group+' ('+rows.length+')'));details.open=!!query||allOpen===true;
   const wrap=element('div',null,'table-wrap'),table=element('table',null,'measure-table comparison-table'),thead=element('thead'),tr=element('tr');
   for(const label of ['Measure',...accounts.map(a=>a.name),...accounts.slice(1).map(a=>accounts[0].name+' − '+a.name),'Unit / parameters'])tr.append(element('th',label));
   thead.append(tr);table.append(thead);const body=element('tbody');
   for(const m of rows){
    const row=element('tr');row.append(element('td',m.label));
    const peers=maps.map(map=>map.get(key(m)));
    for(const peer of peers)row.append(element('td',peer?readable(peer):'Not saved'));
    for(const peer of peers.slice(1)){
     const difference=m.status==='ok'&&peer?.status==='ok'&&Number.isFinite(m.value)&&Number.isFinite(peer.value)?(m.unit==='fraction'?decimal((m.value-peer.value)*100)+' pp':value(m.value-peer.value,m.unit)):'—';
     row.append(element('td',difference));
    }
    const note=element('td');note.append(element('code',m.sourceClass+' · '+m.unit),element('p',m.detail||'','hint'));
    peers.forEach((p,i)=>{if(p&&p.status!=='ok')note.append(element('p',accounts[i].name+': '+p.status.replaceAll('_',' ')+'. '+(i?p.detail||'':''),'hint'));});
    row.append(note);body.append(row);
   }
   table.append(body);wrap.append(table);details.append(wrap);content.push(details);
  }
  $('library-measures').replaceChildren(...content);$('period-measures')?.replaceChildren();
 }
 function renderComparison(accounts,note){
  $('comparison-panel').hidden=false;$('comparison-note').textContent=note;
  const unit=accounts[0].valueUnit||'USD';
  const cards=[['Total return','totalReturn','fraction'],['Annualized growth (CAGR)','cagr','fraction'],['Maximum drawdown','maxDrawdown','fraction'],['Annualized volatility','annualizedVolatility','fraction'],['Sharpe ratio','sharpe','ratio'],['Final portfolio value','finalNav',unit],['Net profit / loss','pnl',unit]];
  $('metrics').replaceChildren(...cards.map(([label,field,u])=>{
   const card=element('div',null,'metric paired-metric');card.append(element('p',label));
   accounts.forEach((a,i)=>{const row=element('div',null,'paired-line'),name=element('span',a.name);name.classList.add('series-'+i);row.append(name,element('strong',value(a.stats[field],u)));card.append(row);});return card;
  }));
  const metric=(a,c)=>a.measures.find(m=>m.sourceClass===c&&m.status==='ok')?.value;
  const stat=(label,field)=>[label,...accounts.map(a=>a.stats[field])],measure=(label,c)=>[label,...accounts.map(a=>metric(a,c))];
  const charts=[
   ['Growth and return','fraction',[stat('Total return','totalReturn'),stat('CAGR','cagr')]],
   ['Risk','fraction',[stat('Maximum drawdown','maxDrawdown'),stat('Annualized volatility','annualizedVolatility')]],
   ['Risk-adjusted ratios','ratio',[stat('Sharpe · daily × √252','sharpe'),measure('Sortino · monthly, annualized','SortinoRatioMonthlyAnnualized'),measure('Calmar · library annual return','CalmarRatio')]],
   ['Portfolio and profit',unit,[stat('Final value','finalNav'),stat('Profit / loss','pnl')]]
  ];
  $('comparison-charts').replaceChildren(...charts.map(([title,u,rows])=>comparisonChart(title,u,rows,accounts.map(a=>a.name))));
 }
 function comparisonChart(title,unit,rows,names){
  const panel=element('section',null,'comparison-chart'),heading=element('h3',title),legend=element('div',null,'comparison-legend'),hidden=new Set();
  const svg=document.createElementNS('http://www.w3.org/2000/svg','svg'),output=element('output','Hover or focus a bar for its value.','comparison-hover'),height=42+names.length*29;
  svg.setAttribute('viewBox','0 0 520 '+(rows.length*height+25));svg.setAttribute('role','img');svg.setAttribute('aria-label',title+' comparison: '+names.join(', '));
  names.forEach((name,k)=>{const button=element('button',name);button.classList.add('series-'+k);button.type='button';button.setAttribute('aria-pressed','true');button.onclick=()=>{hidden.has(k)?hidden.delete(k):hidden.add(k);button.setAttribute('aria-pressed',String(!hidden.has(k)));draw();};legend.append(button);});
  panel.append(heading,legend,svg,output);
  function draw(){
   svg.replaceChildren();const finite=rows.flatMap(row=>row.slice(1).filter((v,k)=>!hidden.has(k)&&Number.isFinite(v)));
   let low=Math.min(0,...finite),high=Math.max(0,...finite);if(high===low)high=low+1;const x=v=>28+(v-low)/(high-low)*310;
   const add=(tag,attrs,text)=>{const n=document.createElementNS('http://www.w3.org/2000/svg',tag);for(const [k,v]of Object.entries(attrs))n.setAttribute(k,v);if(text!=null)n.textContent=text;svg.append(n);return n;};
   rows.forEach(([label,...values],i)=>{
    const top=i*height;add('text',{x:28,y:top+18,class:'bar-label'},label+' · '+(unit==='fraction'?'%':unit));add('line',{x1:x(0),x2:x(0),y1:top+28,y2:top+height-10,stroke:'#b6c8c0'});
    values.forEach((v,k)=>{if(hidden.has(k))return;const y=top+32+k*29;if(!Number.isFinite(v)){add('text',{x:28,y:y+16,fill:colors[k%colors.length]},names[k]+': N/A');return;}
     const tip=names[k]+' · '+label+': '+value(v,unit),bar=add('rect',{x:Math.min(x(0),x(v)),y,width:Math.max(1,Math.abs(x(v)-x(0))),height:20,rx:3,fill:colors[k%colors.length],tabindex:0,role:'img','aria-label':tip});
     bar.onpointerenter=()=>output.textContent=tip;bar.onfocus=()=>output.textContent=tip;add('text',{x:500,y:y+15,'text-anchor':'end',class:'bar-number'},value(v,unit));
    });
   });
  }draw();return panel;
 }
 function installPrint(config){
  let state;
  window.addEventListener('beforeprint',()=>{const r=config.getReport();if(!r||state)return;state={title:document.title,app:config.prepare()};document.title=config.title(r);$('pdf-report-url').textContent='Saved report: '+config.url(r);});
  window.addEventListener('afterprint',()=>{if(!state)return;const saved=state;state=null;document.title=saved.title;config.restore(saved.app);});
 }
 return {element,value,readable,saveFile,renderLibrary,renderComparison,installPrint,colors};
})();
