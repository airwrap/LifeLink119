import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import zlib from 'node:zlib';
const BUILD=path.dirname(fileURLToPath(import.meta.url));
const BASE=path.dirname(BUILD), OUT=path.join(BASE,'output');
const model=JSON.parse(await fs.readFile(path.join(BUILD,'slide-models.json'),'utf8'));
const slides=model.slides, W=model.width, H=model.height, FONT=model.font;
const xml=s=>String(s??'').replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('>','&gt;').replaceAll('"','&quot;').replaceAll("'",'&apos;');
// OOXML package parts. All instructions remain native editable text objects.
const nsP='http://schemas.openxmlformats.org/presentationml/2006/main';
const nsA='http://schemas.openxmlformats.org/drawingml/2006/main';
const nsR='http://schemas.openxmlformats.org/officeDocument/2006/relationships';
const nsRel='http://schemas.openxmlformats.org/package/2006/relationships';
const decl='<?xml version="1.0" encoding="UTF-8" standalone="yes"?>';
const emu=n=>Math.round(n*9525);
const files=new Map();
const add=(n,s)=>files.set(n,Buffer.isBuffer(s)?s:Buffer.from(s,'utf8'));
const rels=entries=>decl+`<Relationships xmlns="${nsRel}">${entries.map(r=>`<Relationship Id="${r[0]}" Type="${nsR}/${r[1]}" Target="${xml(r[2])}"/>`).join('')}</Relationships>`;
const grp='<p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="0" cy="0"/><a:chOff x="0" y="0"/><a:chExt cx="0" cy="0"/></a:xfrm></p:grpSpPr>';
const tx=(el,id)=>`<p:sp><p:nvSpPr><p:cNvPr id="${id}" name="${xml(el.name)}"/><p:cNvSpPr txBox="1"/><p:nvPr/></p:nvSpPr><p:spPr><a:xfrm><a:off x="${emu(el.x)}" y="${emu(el.y)}"/><a:ext cx="${emu(el.w)}" cy="${emu(el.h)}"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom><a:noFill/><a:ln><a:noFill/></a:ln></p:spPr><p:txBody><a:bodyPr wrap="none" lIns="0" tIns="0" rIns="0" bIns="0" anchor="t"><a:noAutofit/></a:bodyPr><a:lstStyle/>${el.lines.map(line=>`<a:p><a:pPr algn="${el.align==='right'?'r':'l'}" fontAlgn="base"><a:lnSpc><a:spcPts val="${Math.round(el.lh*75)}"/></a:lnSpc><a:spcBef><a:spcPts val="0"/></a:spcBef><a:spcAft><a:spcPts val="0"/></a:spcAft></a:pPr><a:r><a:rPr lang="ko-KR" sz="${Math.round(el.size*75)}" b="${el.bold?1:0}" dirty="0"><a:solidFill><a:srgbClr val="${el.color}"/></a:solidFill><a:latin typeface="${FONT}"/><a:ea typeface="${FONT}"/><a:cs typeface="${FONT}"/></a:rPr><a:t xml:space="preserve">${xml(line)}</a:t></a:r><a:endParaRPr lang="ko-KR" sz="${Math.round(el.size*75)}"/></a:p>`).join('')}</p:txBody></p:sp>`;
const cropRect=el=>{
  if(!el.crop)return '';
  const [l,t,r,b]=el.crop, sw=el.sourceWidth,sh=el.sourceHeight;
  return `<a:srcRect l="${Math.round(l/sw*100000)}" t="${Math.round(t/sh*100000)}" r="${Math.round((1-r/sw)*100000)}" b="${Math.round((1-b/sh)*100000)}"/>`;
};
const pic=(el,id,rid)=>`<p:pic><p:nvPicPr><p:cNvPr id="${id}" name="${xml(el.file)}" descr="${xml(el.file)}"/><p:cNvPicPr><a:picLocks noChangeAspect="1"/></p:cNvPicPr><p:nvPr/></p:nvPicPr><p:blipFill><a:blip r:embed="${rid}"/>${cropRect(el)}<a:stretch><a:fillRect/></a:stretch></p:blipFill><p:spPr><a:xfrm><a:off x="${emu(el.x)}" y="${emu(el.y)}"/><a:ext cx="${emu(el.w)}" cy="${emu(el.h)}"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></p:spPr></p:pic>`;


const table=(tb,id)=>{
  const rows=tb.rows.map((row,ri)=>`<a:tr h="${emu(tb.rowHeight)}">${row.map((cell,ci)=>{
    const fill=ri===0?tb.headerFill:(ri%2?tb.bodyFill:'F5F6F1');
    const col=ri===0?tb.headerColor:tb.bodyColor;
    return `<a:tc><a:txBody><a:bodyPr wrap="none" lIns="0" rIns="0" tIns="0" bIns="0"/><a:lstStyle/><a:p><a:pPr algn="l"/><a:r><a:rPr lang="ko-KR" sz="${Math.round(tb.fontSize*75)}" b="${ri===0?1:0}"><a:solidFill><a:srgbClr val="${col}"/></a:solidFill><a:latin typeface="${FONT}"/><a:ea typeface="${FONT}"/><a:cs typeface="${FONT}"/></a:rPr><a:t xml:space="preserve">${xml(cell)}</a:t></a:r><a:endParaRPr lang="ko-KR" sz="${Math.round(tb.fontSize*75)}"/></a:p></a:txBody><a:tcPr marL="${emu(20)}" marR="${emu(12)}" marT="${emu(8)}" marB="${emu(8)}" anchor="ctr"><a:solidFill><a:srgbClr val="${fill}"/></a:solidFill></a:tcPr></a:tc>`;
  }).join('')}</a:tr>`).join('');
  return `<p:graphicFrame><p:nvGraphicFramePr><p:cNvPr id="${id}" name="Automatic demonstration dialogue"/><p:cNvGraphicFramePr/><p:nvPr/></p:nvGraphicFramePr><p:xfrm><a:off x="${emu(tb.x)}" y="${emu(tb.y)}"/><a:ext cx="${emu(tb.w)}" cy="${emu(tb.rowHeight*tb.rows.length)}"/></p:xfrm><a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/table"><a:tbl><a:tblPr firstRow="1" bandRow="0"/><a:tblGrid>${tb.columns.map(w=>`<a:gridCol w="${emu(w)}"/>`).join('')}</a:tblGrid>${rows}</a:tbl></a:graphicData></a:graphic></p:graphicFrame>`;
};

const media=new Map();let mid=0;
for(const s of slides)for(const im of s.images)if(!media.has(im.file)){
  const target=`image${++mid}${path.extname(im.file)}`;media.set(im.file,target);
  add('ppt/media/'+target,await fs.readFile(path.join(BASE,'assets',im.file)));
}
for(const s of slides){
  const n=s.number;let id=2;
  const list=[['rId1','slideLayout','../slideLayouts/slideLayout1.xml'],['rId2','notesSlide',`../notesSlides/notesSlide${n}.xml`]];
  const imgs=s.images.map((im,i)=>{const rid=`rId${i+3}`;list.push([rid,'image','../media/'+media.get(im.file)]);return pic(im,id++,rid);}).join('');
  add(`ppt/slides/slide${n}.xml`,decl+`<p:sld xmlns:a="${nsA}" xmlns:r="${nsR}" xmlns:p="${nsP}"><p:cSld name="${xml(s.title.replaceAll('\n',' '))}"><p:bg><p:bgPr><a:solidFill><a:srgbClr val="${s.background}"/></a:solidFill><a:effectLst/></p:bgPr></p:bg><p:spTree>${grp}${imgs}${s.texts.map(el=>tx(el,id++)).join('')}${(s.tables??[]).map(el=>table(el,id++)).join('')}</p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sld>`);
  add(`ppt/slides/_rels/slide${n}.xml.rels`,rels(list));
  const paras=s.notes.split('\n').map(line=>`<a:p><a:r><a:rPr lang="ko-KR" sz="1200"/><a:t xml:space="preserve">${xml(line)}</a:t></a:r></a:p>`).join('');
  add(`ppt/notesSlides/notesSlide${n}.xml`,decl+`<p:notes xmlns:a="${nsA}" xmlns:r="${nsR}" xmlns:p="${nsP}"><p:cSld><p:spTree>${grp}<p:sp><p:nvSpPr><p:cNvPr id="2" name="Notes"/><p:cNvSpPr txBox="1"/><p:nvPr><p:ph type="body" idx="1"/></p:nvPr></p:nvSpPr><p:spPr/><p:txBody><a:bodyPr/><a:lstStyle/>${paras}</p:txBody></p:sp></p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:notes>`);
  add(`ppt/notesSlides/_rels/notesSlide${n}.xml.rels`,rels([['rId1','notesMaster','../notesMasters/notesMaster1.xml'],['rId2','slide',`../slides/slide${n}.xml`]]));
}
const clrMap='<p:clrMap accent1="accent1" accent2="accent2" accent3="accent3" accent4="accent4" accent5="accent5" accent6="accent6" bg1="lt1" bg2="lt2" folHlink="folHlink" hlink="hlink" tx1="dk1" tx2="dk2"/>';
const fontDef=`<a:defRPr sz="2400"><a:latin typeface="${FONT}"/><a:ea typeface="${FONT}"/><a:cs typeface="${FONT}"/></a:defRPr>`;
add('ppt/presentation.xml',decl+`<p:presentation xmlns:a="${nsA}" xmlns:r="${nsR}" xmlns:p="${nsP}" saveSubsetFonts="1"><p:sldMasterIdLst><p:sldMasterId id="2147483648" r:id="rId1"/></p:sldMasterIdLst><p:notesMasterIdLst><p:notesMasterId r:id="rId2"/></p:notesMasterIdLst><p:sldIdLst>${slides.map((s,i)=>`<p:sldId id="${256+i}" r:id="rId${i+3}"/>`).join('')}</p:sldIdLst><p:sldSz cx="${emu(W)}" cy="${emu(H)}" type="screen16x9"/><p:notesSz cx="6858000" cy="9144000"/><p:defaultTextStyle><a:defPPr>${fontDef}</a:defPPr></p:defaultTextStyle></p:presentation>`);
add('ppt/_rels/presentation.xml.rels',rels([['rId1','slideMaster','slideMasters/slideMaster1.xml'],['rId2','notesMaster','notesMasters/notesMaster1.xml'],...slides.map((s,i)=>[`rId${i+3}`,'slide',`slides/slide${i+1}.xml`])]));
add('ppt/slideMasters/slideMaster1.xml',decl+`<p:sldMaster xmlns:a="${nsA}" xmlns:r="${nsR}" xmlns:p="${nsP}"><p:cSld><p:spTree>${grp}</p:spTree></p:cSld>${clrMap}<p:sldLayoutIdLst><p:sldLayoutId id="2147483649" r:id="rId1"/></p:sldLayoutIdLst><p:txStyles><p:titleStyle><a:lvl1pPr>${fontDef}</a:lvl1pPr></p:titleStyle><p:bodyStyle><a:lvl1pPr>${fontDef}</a:lvl1pPr></p:bodyStyle><p:otherStyle><a:defPPr>${fontDef}</a:defPPr></p:otherStyle></p:txStyles></p:sldMaster>`);
add('ppt/slideMasters/_rels/slideMaster1.xml.rels',rels([['rId1','slideLayout','../slideLayouts/slideLayout1.xml'],['rId2','theme','../theme/theme1.xml']]));
add('ppt/slideLayouts/slideLayout1.xml',decl+`<p:sldLayout xmlns:a="${nsA}" xmlns:r="${nsR}" xmlns:p="${nsP}" type="blank" preserve="1"><p:cSld name="Blank"><p:spTree>${grp}</p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sldLayout>`);
add('ppt/slideLayouts/_rels/slideLayout1.xml.rels',rels([['rId1','slideMaster','../slideMasters/slideMaster1.xml']]));
add('ppt/notesMasters/notesMaster1.xml',decl+`<p:notesMaster xmlns:a="${nsA}" xmlns:r="${nsR}" xmlns:p="${nsP}"><p:cSld><p:spTree>${grp}</p:spTree></p:cSld>${clrMap}<p:notesStyle><a:lvl1pPr>${fontDef}</a:lvl1pPr></p:notesStyle></p:notesMaster>`);
add('ppt/notesMasters/_rels/notesMaster1.xml.rels',rels([['rId1','theme','../theme/theme1.xml']]));
const colors=['192A35','FFFFFF','52646E','FAF9F5','1F6577','9A5B2B','556B79','7F9DA8','C8D7DF','EDC58E','0563C1','954F72'];
const names=['dk1','lt1','dk2','lt2','accent1','accent2','accent3','accent4','accent5','accent6','hlink','folHlink'];
add('ppt/theme/theme1.xml',decl+`<a:theme xmlns:a="${nsA}" name="LifeLink Guide"><a:themeElements><a:clrScheme name="LifeLink">${names.map((n,i)=>`<a:${n}><a:srgbClr val="${colors[i]}"/></a:${n}>`).join('')}</a:clrScheme><a:fontScheme name="Malgun Gothic"><a:majorFont><a:latin typeface="${FONT}"/><a:ea typeface="${FONT}"/><a:cs typeface="${FONT}"/></a:majorFont><a:minorFont><a:latin typeface="${FONT}"/><a:ea typeface="${FONT}"/><a:cs typeface="${FONT}"/></a:minorFont></a:fontScheme><a:fmtScheme name="Simple"><a:fillStyleLst>${'<a:solidFill><a:schemeClr val="phClr"/></a:solidFill>'.repeat(3)}</a:fillStyleLst><a:lnStyleLst>${'<a:ln w="9525"><a:solidFill><a:schemeClr val="phClr"/></a:solidFill><a:prstDash val="solid"/></a:ln>'.repeat(3)}</a:lnStyleLst><a:effectStyleLst>${'<a:effectStyle><a:effectLst/></a:effectStyle>'.repeat(3)}</a:effectStyleLst><a:bgFillStyleLst>${'<a:solidFill><a:schemeClr val="phClr"/></a:solidFill>'.repeat(3)}</a:bgFillStyleLst></a:fmtScheme></a:themeElements></a:theme>`);
add('_rels/.rels',decl+`<Relationships xmlns="${nsRel}"><Relationship Id="rId1" Type="${nsR}/officeDocument" Target="ppt/presentation.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/><Relationship Id="rId3" Type="${nsR}/extended-properties" Target="docProps/app.xml"/></Relationships>`);
add('docProps/core.xml',decl+`<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:dcterms="http://purl.org/dc/terms/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"><dc:title>LifeLink 119 음성 동행 사용 안내</dc:title><dc:subject>최초 설정, 음성 대화, 자동 시연, 인계 요약</dc:subject><dc:creator>LifeLink 119</dc:creator><dcterms:created xsi:type="dcterms:W3CDTF">2026-11-15T09:00:00Z</dcterms:created></cp:coreProperties>`);
add('docProps/app.xml',decl+`<Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/extended-properties" xmlns:vt="http://schemas.openxmlformats.org/officeDocument/2006/docPropsVTypes"><Application>LifeLink119 User Guide</Application><PresentationFormat>On-screen Show (16:9)</PresentationFormat><Slides>${slides.length}</Slides><Notes>${slides.length}</Notes><Company>LifeLink 119</Company></Properties>`);
const overrides=[['/ppt/presentation.xml','application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml'],['/ppt/slideMasters/slideMaster1.xml','application/vnd.openxmlformats-officedocument.presentationml.slideMaster+xml'],['/ppt/slideLayouts/slideLayout1.xml','application/vnd.openxmlformats-officedocument.presentationml.slideLayout+xml'],['/ppt/notesMasters/notesMaster1.xml','application/vnd.openxmlformats-officedocument.presentationml.notesMaster+xml'],['/ppt/theme/theme1.xml','application/vnd.openxmlformats-officedocument.theme+xml'],['/docProps/core.xml','application/vnd.openxmlformats-package.core-properties+xml'],['/docProps/app.xml','application/vnd.openxmlformats-officedocument.extended-properties+xml'],...slides.flatMap(s=>[[`/ppt/slides/slide${s.number}.xml`,'application/vnd.openxmlformats-officedocument.presentationml.slide+xml'],[`/ppt/notesSlides/notesSlide${s.number}.xml`,'application/vnd.openxmlformats-officedocument.presentationml.notesSlide+xml']])];
add('[Content_Types].xml',decl+`<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Default Extension="jpg" ContentType="image/jpeg"/><Default Extension="png" ContentType="image/png"/>${overrides.map(([p,t])=>`<Override PartName="${p}" ContentType="${t}"/>`).join('')}</Types>`);

// A small dependency-free ZIP writer with standard deflate, CRC and central directory.
const crcTable=Array.from({length:256},(_,n)=>{let c=n;for(let k=0;k<8;k++)c=(c&1)?0xedb88320^(c>>>1):c>>>1;return c>>>0;});
const crc32=buf=>{let c=0xffffffff;for(const b of buf)c=crcTable[(c^b)&255]^(c>>>8);return(c^0xffffffff)>>>0;};
let offset=0;const body=[],central=[];
for(const [name,raw] of files){
  const nb=Buffer.from(name,'utf8'),compressed=zlib.deflateRawSync(raw,{level:6}),crc=crc32(raw);
  const local=Buffer.alloc(30);local.writeUInt32LE(0x04034b50,0);local.writeUInt16LE(20,4);local.writeUInt16LE(0x0800,6);local.writeUInt16LE(8,8);local.writeUInt32LE(crc,14);local.writeUInt32LE(compressed.length,18);local.writeUInt32LE(raw.length,22);local.writeUInt16LE(nb.length,26);
  body.push(local,nb,compressed);
  const head=Buffer.alloc(46);head.writeUInt32LE(0x02014b50,0);head.writeUInt16LE(20,4);head.writeUInt16LE(20,6);head.writeUInt16LE(0x0800,8);head.writeUInt16LE(8,10);head.writeUInt32LE(crc,16);head.writeUInt32LE(compressed.length,20);head.writeUInt32LE(raw.length,24);head.writeUInt16LE(nb.length,28);head.writeUInt32LE(offset,42);central.push(head,nb);offset+=local.length+nb.length+compressed.length;
}
const directory=Buffer.concat(central),end=Buffer.alloc(22);end.writeUInt32LE(0x06054b50,0);end.writeUInt16LE(files.size,8);end.writeUInt16LE(files.size,10);end.writeUInt32LE(directory.length,12);end.writeUInt32LE(offset,16);
const out=path.join(BUILD,'candidate.pptx');
await fs.writeFile(out,Buffer.concat([...body,directory,end]));
console.log(JSON.stringify({slides:slides.length,images:media.size,pptx:out,models:path.join(BUILD,'slide-models.json')},null,2));
