import {chromium} from 'playwright';
import path from 'node:path';
import {pathToFileURL} from 'node:url';
const file=path.resolve('docs/presentation/family-safety-v09/output/LifeLink119_가족안전_발표자료_v09.html');
const browser=await chromium.launch({headless:true,executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe'});
try{
 const page=await browser.newPage({viewport:{width:1280,height:800}});await page.goto(pathToFileURL(file).href);
 if(await page.locator('.slide:visible').count()!==1)throw Error('expected one slide');
 if((await page.locator('#count').innerText())!=='1 / 10')throw Error('bad initial count');
 await page.locator('#next').click();if((await page.locator('#count').innerText())!=='2 / 10')throw Error('next failed');
 await page.keyboard.press('ArrowRight');if((await page.locator('#count').innerText())!=='3 / 10')throw Error('keyboard failed');
 await page.locator('#prev').click();if((await page.locator('#count').innerText())!=='2 / 10')throw Error('previous failed');
 await page.screenshot({path:'docs/presentation/family-safety-v09/.build/html-page.png'});console.log('PASS: one visible slide; next, previous and keyboard navigation');
}finally{await browser.close()}
