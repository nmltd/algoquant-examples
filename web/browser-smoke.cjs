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
   const path=new URL(route.request().url()).pathname;
   if(path==='/api/stocks')return route.fulfill({json:['AAPL','MSFT']});
   if(path.startsWith('/api/reports/'))return route.fulfill({json:report});
   if(path==='/api/backtest'){posts++;return route.fulfill({json:report});}
   const file=path==='/app.js'?'app.js':path==='/style.css'?'style.css':'index.html';
   return route.fulfill({body:fs.readFileSync('src/main/resources/web/'+file),contentType:file.endsWith('.js')?'application/javascript':file.endsWith('.css')?'text/css':'text/html'});
  });
  await page.goto('http://sma2.test'+report.reportPath);
  await page.locator('#report').waitFor({state:'visible'});
  const full=await page.locator('#prices-window').textContent();
  await page.locator('.chart-panel').first().getByRole('button',{name:'Zoom in +',exact:true}).click();
  const zoom=await page.locator('#prices-window').textContent();
  assert.notEqual(zoom,full);
  assert.equal(await page.locator('#nav-window').textContent(),zoom);
  const svg=page.locator('#prices'),box=await svg.boundingBox();
  await page.mouse.move(box.x+box.width*.5,box.y+box.height*.5);
  assert.match(await page.locator('#prices-hover').textContent(),/Adjusted close: \$/);
  const legend=page.locator('[data-series="close"]');await legend.click();
  assert.equal(await legend.getAttribute('aria-pressed'),'false');await legend.click();
  await svg.focus();await page.keyboard.press('ArrowRight');
  assert.notEqual(await page.locator('#prices-window').textContent(),zoom);
  await page.keyboard.press('Home');
  assert.equal(await page.locator('#prices-window').textContent(),full);
  await page.locator('.chart-panel').first().getByRole('button',{name:'Zoom in +',exact:true}).click();
  const beforeDrag=await page.locator('#prices-window').textContent();
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
  assert.deepEqual(errors,[]);
  await page.screenshot({path:'target/web-reports/browser.png',fullPage:true});
  console.log('Browser checks passed: zoom, synchronized windows, hover, legend, pan, reset, report controls and refresh without rerun.');
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
