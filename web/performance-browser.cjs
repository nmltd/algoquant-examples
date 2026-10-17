const {chromium}=require('playwright'),fs=require('node:fs'),assert=require('node:assert/strict');
(async()=>{
 const report=JSON.parse(fs.readFileSync('target/web-reports/performance.json','utf8'));
 const browser=await chromium.launch({headless:true,executablePath:process.env.CHROME_BIN||'/usr/bin/google-chrome',args:['--no-sandbox']});
 try{
  const page=await browser.newPage({viewport:{width:1440,height:1100}}),errors=[];let posts=0;
  page.on('pageerror',error=>errors.push(error.message));
  await page.route('http://performance.test/**',async route=>{
   const path=new URL(route.request().url()).pathname;
   if(path==='/performance/api/analyze'){posts++;return route.fulfill({json:report});}
   if(path.startsWith('/performance/api/reports/')){
    if(path.endsWith('.xlsx'))return route.fulfill({body:fs.readFileSync('target/web-reports/performance.xlsx'),contentType:'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'});
    if(path.endsWith('.csv'))return route.fulfill({body:'Product,Test\r\nTotal,1\r\n',contentType:'text/csv'});
    return route.fulfill({json:report});
   }
   const file=path.startsWith('/shared/')?path.slice(1):path==='/performance/app.js'?'performance/app.js':'performance/index.html';
   return route.fulfill({body:fs.readFileSync('src/main/resources/web/'+file),contentType:file.endsWith('.js')?'application/javascript':file.endsWith('.css')?'text/css':'text/html'});
  });
  await page.goto('http://performance.test/performance/');
  await page.locator('#source-url').fill('https://example.com/nav.csv');await page.locator('#analyze').click();
  await page.locator('#report').waitFor({state:'visible'});assert.equal(posts,1);
  assert.equal(new URL(page.url()).pathname,report.reportPath);
  assert.equal(await page.locator('.comparison-chart').count(),4);
  assert.equal(await page.locator('.comparison-chart').first().locator('.comparison-legend button').count(),3);
  assert.match(await page.locator('.paired-metric').first().textContent(),/S&P 500/);
  const full=await page.locator('#history-window').textContent();
  await page.locator('#history-in').click();assert.notEqual(await page.locator('#history-window').textContent(),full);
  await page.locator('#history-reset').click();assert.equal(await page.locator('#history-window').textContent(),full);
  const svg=page.locator('#history');await svg.scrollIntoViewIfNeeded();const box=await svg.boundingBox();
  await page.mouse.move(box.x+box.width*.5,box.y+box.height*.5);
  assert.match(await page.locator('#history-hover').textContent(),/Alpha:/);
  await page.locator('#expand-all').click();assert.equal(await page.locator('#library-measures details:not([open])').count(),0);
  await page.locator('#collapse-all').click();assert.equal(await page.locator('#library-measures details[open]').count(),0);
  await page.locator('#measure-filter').fill('Sortino');await page.locator('#clear-search').click();
  const waiting=page.waitForEvent('download');await page.locator('#download').click();const download=await waiting;
  assert.equal(download.suggestedFilename(),'performance-report.xlsx');
  const stream=await download.createReadStream(),chunks=[];for await(const c of stream)chunks.push(c);
  assert.deepEqual(Buffer.concat(chunks),fs.readFileSync('target/web-reports/performance.xlsx'));
  await page.reload();await page.locator('#report').waitFor({state:'visible'});assert.equal(posts,1);
  const pdf=await page.pdf({path:'target/web-reports/performance.pdf',format:'A4',landscape:true,printBackground:true});assert.equal(pdf.subarray(0,4).toString(),'%PDF');
  assert.deepEqual(errors,[]);
  await page.screenshot({path:'target/web-reports/performance.png',fullPage:true});
  console.log('Performance Analyzer browser checks passed.');
 }finally{await browser.close();}
})().catch(error=>{console.error(error);process.exitCode=1;});
