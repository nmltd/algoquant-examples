// Exercise the actual page with a saved API snapshot; no market-data or browser-network dependency.
const {chromium}=require('playwright');
const fs=require('node:fs'),assert=require('node:assert/strict');
(async()=>{
 const report=JSON.parse(fs.readFileSync('target/web-reports/offline.json','utf8'));
 const browser=await chromium.launch({headless:true,executablePath:process.env.CHROME_BIN||'/usr/bin/google-chrome',args:['--no-sandbox']});
 try{
  const page=await browser.newPage({viewport:{width:1440,height:1100}}),errors=[];let posts=0;
  page.on('pageerror',e=>errors.push(e.message));
  await page.route('http://sma2.test/**',async route=>{
   const incoming=new URL(route.request().url()).pathname;
   const path=incoming.startsWith('/sma2/')?incoming.slice('/sma2'.length):incoming;
   if(path==='/api/stocks')return route.fulfill({json:['AAPL','MSFT']});
   if(path.startsWith('/api/reports/')){
    if(path.endsWith('00000000000000000000000000000001')){
     const legacy=JSON.parse(JSON.stringify(report));delete legacy.sizingModel;delete legacy.benchmarkTotalReturn;legacy.reportSchemaVersion=1;delete legacy.buyHoldStatistics;delete legacy.sma2Statistics;delete legacy.buyHoldPerformanceMeasures;legacy.reportId='00000000000000000000000000000001';legacy.reportPath='/reports/'+legacy.reportId;
     legacy.series.forEach(p=>p.benchmark=10000+p.close-legacy.series[0].close);
     return route.fulfill({json:legacy});
    }
    return route.fulfill({json:report});
   }
   if(path==='/api/backtest'){posts++;return route.fulfill({json:report});}
   const file=path==='/app.js'?'sma2/app.js':path==='/style.css'?'sma2/style.css':path==='/tools.css'?'tools.css':incoming==='/'?'tools.html':'sma2/index.html';
   return route.fulfill({body:fs.readFileSync('src/main/resources/web/'+file),contentType:file.endsWith('.js')?'application/javascript':file.endsWith('.css')?'text/css':'text/html'});
  });
  await page.goto('http://sma2.test/');
  assert.equal(await page.getByRole('link',{name:'Open SMA2'}).getAttribute('href'),'/sma2/');
  await page.goto('http://sma2.test'+report.reportPath);
  await page.locator('#report').waitFor({state:'visible'});
  assert.match(await page.locator('#report-sizing-note').textContent(),/Full-equity sizing/);
  assert.match(await page.locator('#benchmark-legend').textContent(),/fully invested/);
  assert.equal(await page.locator('.comparison-chart').count(),4);
  assert.match(await page.locator('.paired-metric').first().textContent(),/Buy & hold/);
  assert.equal(await page.locator('#period-measures').isVisible(),false);
  const bar=page.locator('.comparison-chart rect').first();await bar.focus();
  assert.match(await page.locator('.comparison-hover').first().textContent(),/SMA2/);
  await page.locator('.comparison-legend button').first().click();
  assert.equal(await page.locator('.comparison-legend button').first().getAttribute('aria-pressed'),'false');
  await page.locator('.comparison-legend button').first().click();
  await page.locator('#market-history > summary').click();
  const full=await page.locator('#prices-window').textContent();
  await page.locator('.chart-panel').first().getByRole('button',{name:'Zoom in +',exact:true}).click();
  const zoom=await page.locator('#prices-window').textContent();
  assert.notEqual(zoom,full);
  assert.equal(await page.locator('#nav-window').textContent(),zoom);
  const svg=page.locator('#prices');await svg.scrollIntoViewIfNeeded();let box=await svg.boundingBox();
  await page.mouse.move(box.x+box.width*.5,box.y+box.height*.5);
  await page.waitForFunction(()=>document.getElementById('prices-hover').textContent.includes('Adjusted close: $'));
  assert.match(await page.locator('#prices-hover').textContent(),/Adjusted close: \$/);
  const legend=page.locator('[data-series="close"]');await legend.click();
  assert.equal(await legend.getAttribute('aria-pressed'),'false');await legend.click();
  await svg.focus();await page.keyboard.press('ArrowRight');
  assert.notEqual(await page.locator('#prices-window').textContent(),zoom);
  await page.keyboard.press('Home');
  assert.equal(await page.locator('#prices-window').textContent(),full);
  await page.locator('.chart-panel').first().getByRole('button',{name:'Zoom in +',exact:true}).click();
  const beforeDrag=await page.locator('#prices-window').textContent();
  await svg.scrollIntoViewIfNeeded();box=await svg.boundingBox();
  await page.mouse.move(box.x+box.width*.65,box.y+box.height*.5);await page.mouse.down();
  await page.mouse.move(box.x+box.width*.8,box.y+box.height*.5,{steps:5});await page.mouse.up();
  assert.notEqual(await page.locator('#prices-window').textContent(),beforeDrag);
  await page.locator('#expand-all').click();
  assert.equal(await page.locator('#library-measures details:not([open]),#period-measures details:not([open])').count(),0);
  await page.locator('#collapse-all').click();
  assert.equal(await page.locator('#library-measures details[open],#period-measures details[open]').count(),0);
  await page.locator('#measure-filter').fill('Sortino');await page.locator('#clear-search').click();
  assert.equal(await page.locator('#measure-filter').inputValue(),'');
  const links=page.locator('#strategy-mechanics p').filter({has:page.locator('a')}).locator('a');
  assert.equal(await links.count(),1);assert.match(await links.getAttribute('href'),/SMA2App.java$/);
  await page.reload();await page.locator('#report').waitFor({state:'visible'});assert.equal(posts,0);
  await page.locator('#run').click();await page.waitForFunction(()=>document.getElementById('status').textContent.startsWith('Complete'));
  assert.equal(posts,1);assert.equal(new URL(page.url()).pathname,report.reportPath);
  await page.reload();await page.locator('#report').waitFor({state:'visible'});assert.equal(posts,1);
  await page.goto('http://sma2.test/sma2/reports/00000000000000000000000000000001');await page.locator('#report').waitFor({state:'visible'});
  assert.match(await page.locator('#report-sizing-note').textContent(),/Legacy saved report/);
  assert.match(await page.locator('#benchmark-legend').textContent(),/one share/);
  assert.equal(await page.locator('#comparison-panel').isVisible(),false);
  assert.equal(await page.locator('#report-link').getAttribute('href'),'/sma2/reports/00000000000000000000000000000001');
  await page.goto('http://sma2.test'+report.reportPath);await page.locator('#report').waitFor({state:'visible'});
  assert.deepEqual(errors,[]);
  await page.screenshot({path:'target/web-reports/browser.png',fullPage:true});
  console.log('Browser checks passed: zoom, synchronized windows, hover, legend, pan, reset, report controls and refresh without rerun.');
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
