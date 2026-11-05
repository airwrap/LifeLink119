import fs from 'node:fs/promises';
import path from 'node:path';
import { Presentation, PresentationFile } from '@oai/artifact-tool';

const base=path.resolve('docs/presentation/family-safety-v09');
const tmp=path.join(base,'.build');
const out=path.join(base,'output');
const W=1280,H=720;
const C={ink:'#192639', muted:'#52647A', blue:'#2452AA', red:'#AC2D26', line:'#D5DFEC', bg:'#F6F8FC', paper:'#FFFFFF', warm:'#FFF4E6'};
const deck=Presentation.create({slideSize:{width:W,height:H}});
const notes=[];
function box(slide,x,y,w,h,fill='none',line='none',radius=false){return slide.shapes.add({geometry:radius?'roundRect':'rect',position:{left:x,top:y,width:w,height:h},fill,line:{style:'solid',fill:line,width:line==='none'?0:1}})}
function text(slide,value,x,y,w,h,size=24,color=C.ink,bold=false,align='left'){
 const s=slide.shapes.add({geometry:'textbox',position:{left:x,top:y,width:w,height:h},fill:'none',line:{style:'solid',fill:'none',width:0}});
 s.text=value;s.text.style={typeface:'Malgun Gothic',fontSize:size,color,bold,alignment:align,verticalAlignment:'middle',wrap:'square',autoFit:'shrinkText',insets:0};return s;
}
async function pic(slide,file,x,y,w,h,alt){const b=await fs.readFile(file);slide.images.add({blob:b.buffer.slice(b.byteOffset,b.byteOffset+b.byteLength),contentType:'image/png',alt,fit:'contain',position:{left:x,top:y,width:w,height:h}})}
function chrome(s,n){
 box(s,0,0,W,10,C.blue);text(s,'LifeLink 119',52,23,260,28,18,C.blue,true);
 text(s,'공모전 시연 · 실제 신고·상담 연결 아님',52,675,760,22,14,C.muted);
 text(s,String(n).padStart(2,'0'),1180,675,48,22,14,C.muted,false,'right');
}
function slide(title,n){const s=deck.slides.add();s.background.fill=C.bg;chrome(s,n);text(s,title,52,76,1174,64,36,C.ink,true);return s}
function mini(s,title,body,x,y,w,color=C.blue){
 box(s,x,y,w,112,C.paper,C.line,true);text(s,title,x+22,y+16,w-44,32,22,color,true);text(s,body,x+22,y+53,w-44,46,18,C.muted);
}
function addNotes(s,t,source){s.speakerNotes.text=t+'\n\n근거: '+source;notes.push(t)}

// 1. Cover: AI-generated concept illustration was used in the preceding deck.
{
 const s=deck.slides.add();s.background.fill=C.paper;await pic(s,path.join(tmp,'previous-cover.png'),0,0,1280,720,'가족과 자동차의 서비스 콘셉트 이미지');
 text(s,'LifeLink 119',58,65,390,36,24,C.blue,true);
 text(s,'나와 내 가족의\n안전을 위한 자동차',58,154,520,190,48,C.ink,true);
 text(s,'차 안에서 시작하는 긴급 도움과 상담',58,399,470,60,24,C.muted);
 text(s,'공모전 시연 · 실제 신고·상담 연결 아님',58,656,600,22,15,C.muted);
 addNotes(s,'LifeLink 119는 나와 가족이 함께 타는 자동차에서 긴급 도움과 상담을 요청하는 과정을 제안합니다. 표지의 가족과 차량은 서비스 콘셉트 이미지이며 실차 운영 사진이 아닙니다. 현재 앱은 공모전용 모의 시연입니다.','기존 발표자료 표지 이미지, README.md');
}
// 2. Home and first choice.
{
 const s=slide('홈에서 필요한 도움을 선택합니다',2);
 text(s,'위급하면',58,192,360,32,19,C.red,true);text(s,'119 구조·구급\n바로 요청',58,228,390,84,31,C.ink,true);
 text(s,'걱정되는 상황이라면\n119 의료상담 또는 109 심리상담',58,344,390,85,22,C.muted);
 text(s,'112 신고와 가족정보도 별도로 선택합니다.',58,484,405,60,19,C.muted);
 await pic(s,path.join(tmp,'home.png'),506,152,728,410,'v0.9 에뮬레이터 홈 화면');
 addNotes(s,'홈에서 119 구조·구급 요청은 한 번에 시작할 수 있습니다. 상담은 119 의료상담과 109 심리상담 중 고릅니다. 112 신고와 가족정보 역시 각자의 경로로 구분했습니다. 화면은 실제 설치된 APK에서 캡처한 화면입니다.','ExpansionUi.kt homePage, verification/v09/ux/after/01-home.jpg');
}
// 3. Overview using native editable diagram.
{
 const s=slide('긴급 요청과 상담의 전체 흐름',3);
 text(s,'필요에 맞는 도움을 선택하고, 연결 이후 필요한 기능만 사용합니다.',55,150,1100,35,22,C.muted);
 mini(s,'01  도움 요청','119 구조·구급 또는 상담 선택',55,238,360,C.red);
 mini(s,'02  가상 상황실','상황 설명과 선택 지원 시연',460,238,360,C.blue);
 mini(s,'03  마무리','공유 철회와 세션 정보 정리',865,238,360,C.blue);
 text(s,'가족정보 등록과 의료정보 인증은 긴급 요청의 선행 조건이 아닙니다.',55,404,1120,48,23,C.ink,true);
 text(s,'실제 전화·기관 연결·출동은 발생하지 않는 앱 내부 시연입니다.',55,481,1120,39,20,C.muted);
 addNotes(s,'사용자는 홈에서 긴급 요청과 상담 중 필요한 경로를 고릅니다. 앱 안의 가상 상황실 화면에서 상황 전달이나 필요한 지원을 시연하고, 종료 시 세션의 공유 항목을 정리합니다. 의료정보 인증은 긴급 요청을 막지 않습니다.','README.md; docs/DRIVING_EMERGENCY_DEMO.md; docs/MEDICAL_PRIVACY.md');
}
// 4. Emergency scenario.
{
 const s=slide('주행 중 위급 상황도 요청할 수 있습니다',4);
 text(s,'119 바로 요청',55,190,390,50,30,C.red,true);
 text(s,'위험 유형을 먼저 고르거나\n정차·PIN·위치를 확인할 필요 없이\n모의 요청을 시작합니다.',55,255,390,145,22,C.ink);
 text(s,'주행 상태는 시연값입니다.\n차량의 실제 조작 제한은 따릅니다.',55,449,390,76,18,C.muted);
 await pic(s,path.join(tmp,'driving.png'),492,156,735,429,'119 연결 후 차 안의 위급 상황 시연 화면');
 addNotes(s,'차 안에서 위급한 상황이 생기면 상황 종류를 고르는 단계 없이 119 구조·구급 요청 시연을 시작합니다. 정차 확인이나 가족정보 인증은 요청의 조건이 아닙니다. 화면의 주행 상태는 시연값이며 실제 차량의 주행 중 조작 제한은 그대로 적용합니다. 실제 119에 발신하지 않습니다.','docs/DRIVING_EMERGENCY_DEMO.md; verification/v09/ux/after/05-driving.jpg');
}
// 5. Consultation.
{
 const s=slide('상담이 필요할 때도 경로를 고릅니다',5);
 text(s,'119 응급의료상담',57,201,410,42,27,C.blue,true);
 text(s,'건강이나 응급 상황에 대한\n도움을 구하는 경로',57,252,400,75,21,C.muted);
 text(s,'109 심리위기 상담',57,367,410,42,27,C.blue,true);
 text(s,'마음의 어려움에 대해\n상담을 선택하는 경로',57,417,400,75,21,C.muted);
 await pic(s,path.join(tmp,'consult.png'),490,187,736,330,'119 응급의료상담과 109 심리위기상담 선택 화면');
 addNotes(s,'구조·구급 신고와 응급의료상담은 모두 119를 이용하지만 도움을 요청하는 목적은 다릅니다. 앱은 119 의료상담과 109 심리위기 상담을 별도의 선택지로 보여줍니다. 현재는 실제 상담원과 통화하지 않는 시연입니다.','ExpansionUi.kt entry; https://www.nfa.go.kr/nfa/news/pressrelease/explanation/?cntId=44&mode=view');
}
// 6. Connected assistance.
{
 const s=slide('연결 후 필요한 지원을 선택합니다',6);
 text(s,'상황 설명',55,187,380,40,26,C.blue,true);text(s,'무엇이 필요한지 전달하고\n추가 지원을 선택합니다.',55,235,385,70,21,C.muted);
 text(s,'정보는 선택적으로',55,350,380,40,26,C.blue,true);text(s,'가족과 의료정보는 인증한 뒤\n필요한 항목만 공유합니다.',55,398,385,75,21,C.muted);
 await pic(s,path.join(tmp,'connected.png'),505,156,715,440,'의료상담 가상 연결 이후 지원 선택 화면');
 addNotes(s,'가상 상담원 응답 화면에서 상황에 맞는 지원을 고릅니다. 도움이 필요한 사람의 선택과 의료정보 제공은 필수가 아닙니다. 정보는 인증한 다음 항목을 선택해 공유하며, 실제 기관으로 전송되지 않습니다.','ExpansionUi.kt sessionPage; docs/MEDICAL_PRIVACY.md; verification/v09/ux/after/04-consult-connected.jpg');
}
// 7. Simulated operator video guidance.
{
 const s=slide('상황실 영상 안내를 시연합니다',7);
 text(s,'요청 · 동의 · 시작',55,196,400,42,27,C.blue,true);
 text(s,'가상 담당자의 문구와\n합성 장면을 보여줍니다.',55,249,380,70,21,C.muted);
 text(s,'언제든 중단하고\n동의를 철회할 수 있습니다.',55,365,380,75,21,C.ink,true);
 text(s,'실제 영상통화·카메라·음성은 없습니다.',55,495,400,65,18,C.muted);
 await pic(s,path.join(tmp,'video.png'),488,157,740,395,'합성 장면과 가상 상황실 안내 화면');
 addNotes(s,'119 영상 안내 사례에서 착안해 가상 담당자가 화면으로 도움을 주는 과정을 구성했습니다. 요청하고 합성 영상 사용에 동의한 뒤 시작할 수 있으며 중단과 동의 철회가 가능합니다. 표시되는 것은 고정 문구와 합성 도형이며 실제 영상통화, 카메라 촬영, 음성 연결 또는 상황실 전송이 아닙니다. 차량 영상 제한을 따릅니다.','docs/UX_VIDEO_GUIDANCE_V08.md; https://imnews.imbc.com/replay/2026/nwdesk/article/6861437_37004.html');
}
// 8. Privacy.
{
 const s=slide('가족의 의료정보는 선택해서 보호합니다',8);
 mini(s,'01  잠금','시스템 인증 또는 사용자가 설정한 PIN',55,221,360);
 mini(s,'02  항목 선택','인증 후 필요한 정보만 공유 선택',460,221,360);
 mini(s,'03  철회','공유를 중단하고 세션 정보 정리',865,221,360);
 text(s,'긴급 요청은 인증 없이 바로 시연할 수 있습니다.',55,395,1120,49,25,C.ink,true);
 text(s,'저장된 가족 프로필의 관리는 세션 종료와 별도로 진행합니다.',55,465,1120,55,21,C.muted);
 addNotes(s,'가족의 의료정보는 기본 잠금 상태입니다. 사용 가능한 Android 시스템 인증이 있으면 이를 쓰고, 없으면 사용자가 직접 설정한 앱 PIN으로 엽니다. 인증 후에도 필요한 항목을 골라 공유하고 철회할 수 있습니다. 긴급 요청은 인증 없이 시작하며, 저장 프로필은 세션 종료와 별도로 관리합니다.','docs/MEDICAL_PRIVACY.md; ExpansionUi.kt; Experience.kt');
}
// 9. Additional support.
{
 const s=slide('가족에게 필요한 지원을 더합니다',9);
 text(s,'기본 응급 매뉴얼',55,184,420,43,26,C.blue,true);text(s,'앱에서 확인하는 학습용 안내',55,234,400,38,20,C.muted);
 text(s,'문자와 쉬운 안내',55,327,420,43,26,C.blue,true);text(s,'상황을 설명하는 가상 표현',55,377,400,38,20,C.muted);
 text(s,'주변 차량 협력',55,468,420,43,26,C.blue,true);text(s,'동의와 가상 승인 절차를 거치는 별도 시연',55,518,408,60,19,C.muted);
 await pic(s,path.resolve('verification/v09/ux/manual.jpg'),488,155,740,443,'기본 응급상황 매뉴얼 실제 앱 화면');
 addNotes(s,'기본 응급 매뉴얼과 문자·쉬운 안내 등은 필요한 경우 선택해 볼 수 있습니다. 주변 차량 협력과 승인형 화면 표시는 별도 동의와 가상 기관 역할의 승인을 거치는 모의 기능입니다. 실제 기관 승인이나 차량 외부 등화 제어는 없습니다. 매뉴얼은 의료진 검토 전 학습용 시연입니다.','docs/FEATURE_MATRIX.md; verification/v09/ux/manual.jpg');
}
// 10. Closing and scope.
{
 const s=slide('나와 내 가족의 안전을 위한 자동차',10);
 text(s,'위급할 때는 긴급 도움을,\n걱정될 때는 상담을 시작합니다.',58,193,1080,154,39,C.ink,true);
 box(s,58,395,1115,112,C.paper,C.line,true);
 text(s,'현재 구현',82,414,165,38,22,C.blue,true);
 text(s,'앱 안의 가상 연결 · 합성 영상 · 선택 공유 시연',250,414,880,38,22,C.ink);
 text(s,'실제 기관 연결과 차량 제어에는 별도 협력과 검증이 필요합니다.',58,546,1120,42,19,C.muted);
 addNotes(s,'LifeLink 119가 제안하는 자동차는 가족에게 도움이 필요할 때 긴급 요청과 상담을 시작할 수 있는 공간입니다. 현재는 실제 기관 연결과 영상통화, 차량 등화 제어가 없는 공모전용 시연입니다. 실제 운영을 위해서는 관련 기관과 제조사의 협력 및 검증이 필요합니다.','README.md; docs/WEB_STYLE_UI_V09.md; docs/UX_VIDEO_GUIDANCE_V08.md');
}
await fs.mkdir(tmp,{recursive:true});await fs.mkdir(out,{recursive:true});
for(let i=0;i<deck.slides.items.length;i++){
 const png=await deck.export({slide:deck.slides.items[i],format:'png',scale:1});
 await fs.writeFile(path.join(tmp,`slide-${String(i+1).padStart(2,'0')}.png`),new Uint8Array(await png.arrayBuffer()));
}
const ppt=await PresentationFile.exportPptx(deck);await ppt.save(path.join(tmp,'draft.pptx'));
await fs.writeFile(path.join(tmp,'notes.json'),JSON.stringify(notes,null,2),'utf8');
console.log('Created',deck.slides.items.length,'slides');
