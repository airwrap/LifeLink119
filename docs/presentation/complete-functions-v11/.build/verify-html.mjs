import {chromium} from 'playwright';
import path from 'node:path';
import fs from 'node:fs';
import {pathToFileURL} from 'node:url';
const dir=path.resolve('docs/presentation/complete-functions-v11/output');
const file=path.join(dir,fs.readdirSync(dir).find(x=>x.endsWith('.html')));
const browser=await chromium.launch({headless:true,executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe'});
try {
 const page=await browser.newPage({viewport:{width:1280,height:800}});
 await page.goto(pathToFileURL(file).href);
 if(await page.locator('.slide:visible').count()!==1) throw Error('visible slide');
 if((await page.locator('#count').innerText())!=='1 / 41') throw Error('initial count');
 await page.locator('#next').click(); if((await page.locator('#count').innerText())!=='2 / 41') throw Error('next');
 await page.keyboard.press('ArrowRight'); if((await page.locator('#count').innerText())!=='3 / 41') throw Error('keyboard');
 await page.locator('#prev').click(); if((await page.locator('#count').innerText())!=='2 / 41') throw Error('previous');
 await page.screenshot({path:'docs/presentation/complete-functions-v11/.build/html-page.png'});
 console.log('PASS: 41 slides, next, previous and keyboard navigation');
} finally { await browser.close(); }
