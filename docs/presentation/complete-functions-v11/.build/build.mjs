import fs from 'node:fs/promises';
import path from 'node:path';
import { Presentation, PresentationFile } from '@oai/artifact-tool';

const base=path.resolve('docs/presentation/complete-functions-v11');
const tmp=path.join(base,'.build'),screens=path.join(tmp,'screens'),out=path.join(base,'output');
const C={ink:'#192639',muted:'#52647A',blue:'#2452AA',red:'#AC2D26',line:'#D5DFEC',bg:'#F6F8FC',paper:'#FFFFFF',warm:'#FFF4E6'};
const deck=Presentation.create({slideSize:{width:1280,height:720}});
const notes=[],missing=[];
function shape(s,x,y,w,h,fill='none',stroke='none',round=false){return s.shapes.add({geometry:round?'roundRect':'rect',position:{left:x,top:y,width:w,height:h},fill,line:{style:'solid',fill:stroke,width:stroke==='none'?0:1}})}
function text(s,value,x,y,w,h,size=22,color=C.ink,bold=false,align='left'){
 const p=s.shapes.add({geometry:'textbox',position:{left:x,top:y,width:w,height:h},fill:'none',line:{style:'solid',fill:'none',width:0}});
 p.text=value;p.text.style={typeface:'Malgun Gothic',fontSize:size,color,bold,alignment:align,verticalAlignment:'middle',wrap:'square',autoFit:'shrinkText',insets:0};return p;
}
async function image(s,file,x,y,w,h,alt){const b=await fs.readFile(file);s.images.add({blob:b.buffer.slice(b.byteOffset,b.byteOffset+b.byteLength),contentType:file.endsWith('.jpg')?'image/jpeg':'image/png',alt,fit:'contain',position:{left:x,top:y,width:w,height:h}})}
function baseSlide(title){
 const s=deck.slides.add();s.background.fill=C.bg;shape(s,0,0,1280,10,C.blue);text(s,'LifeLink 119',52,22,250,26,17,C.blue,true);
 text(s,title,52,73,1170,58,35,C.ink,true);text(s,'공모전용 · 기관 연결과 전송은 모의 기능',52,676,900,22,14,C.muted);
 text(s,String(deck.slides.items.length).padStart(2,'0'),1180,676,48,22,14,C.muted,false,'right');return s;
}
function note(s,body,src){s.speakerNotes.text=body+'\n\n근거: '+src;notes.push({title:'',body,source:src});}
async function screen(title,sub,desc,file,source){
 const s=baseSlide(title);text(s,sub,56,177,372,60,26,C.blue,true);text(s,desc,56,255,370,245,21,C.ink);
 const f=path.join(screens,file+'.jpg');
 try{await fs.access(f);await image(s,f,455,139,765,492,title+'의 실제 앱 화면');text(s,'설치된 v0.11 앱 화면',532,640,610,18,14,C.muted,false,'center')}
 catch{missing.push({title,file});shape(s,470,170,735,408,C.paper,C.line,true);text(s,'화면 캡처를 확보하지 못했습니다',510,280,650,50,24,C.muted,true,'center');text(s,'설명은 현재 앱 소스와 기능 문서를 기준으로 작성했습니다.',510,344,650,55,19,C.muted,false,'center')}
 note(s,sub+'\n'+desc,source+'; 앱 화면 '+file+'; APK 51d5bec8061983321ddd80d132e072da78eca849aa7d7ca1331b6bc63bf04545');
}
function diagram(title,sub,desc,items,source,reason='개인정보 보호로 캡처 제한'){
 const s=baseSlide(title);text(s,sub,56,177,370,62,26,C.blue,true);text(s,desc,56,264,370,250,21,C.ink);
 shape(s,464,148,744,466,C.paper,C.line,true);text(s,'화면 구조 설명 · '+reason,492,167,686,32,16,C.muted,true);
 let y=223;for(const item of items){shape(s,492,y,685,68,C.bg,C.line,true);text(s,item,514,y+9,638,50,20,C.ink,true);y+=82}
 text(s,'실제 화면 캡처가 아닌 설명용 도식',492,558,686,25,16,C.muted);
 note(s,sub+'\n'+desc+'\n'+reason+'; 소스 기준 구조도로 설명합니다.',source);
}
// Cover: reused AI-generated concept art from the previous presentation.
{
 const s=deck.slides.add();s.background.fill=C.paper;await image(s,path.join(tmp,'cover.png'),0,0,1280,720,'가족과 자동차의 서비스 콘셉트 이미지');
 text(s,'LifeLink 119',58,65,390,36,24,C.blue,true);text(s,'나와 내 가족의\n안전을 위한 자동차',58,154,520,190,48,C.ink,true);
 text(s,'전체 기능과 화면으로 살펴보는 서비스',58,399,500,60,24,C.muted);text(s,'공모전용 · 실제 신고·상담 연결 아님',58,656,600,22,15,C.muted);
 note(s,'LifeLink 119의 전체 기능을 실제 앱 화면과 함께 설명합니다. 표지는 서비스 콘셉트를 표현한 AI 생성 이미지입니다.','기존 발표자료 표지; README.md');
}
await screen('어떤 도움이 필요한가요?','나와 가족을 위한 시작 화면','119 구조·구급, 상담, 112, 우리 가족 안심콜을 목적에 따라 선택합니다.','01-home','ExpansionUi.homePage');
await screen('119 구조·구급 바로 요청','등록 없이 한 번에 시작','차 안에서 위급한 상황이 발생하면 정차 확인이나 의료정보 인증을 기다리지 않고 모의 요청을 시작합니다.','02-driving','ExpansionUi.drivingPage; DrivingEmergency.kt');
await screen('상황을 가상 상황실에 전달','현재 상황을 함께 확인','상황 전달과 확인 절차를 보여줍니다. 위치와 차량 정보는 별도 선택이며 실제 GPS는 사용하지 않습니다.','03-driving-sent','DrivingEmergency.kt; ExpansionUi.drivingPage');
await screen('상황실 영상 안내 동의','요청 후 동의를 선택','가상의 영상 안내를 시작하기 전에 합성 장면 사용 범위를 확인합니다.','04-video-consent','ExpansionUi.guidedVideoPage');
await screen('상황실 영상 안내','가상 담당자의 안내','합성 영상과 고정 안내 문구를 보여줍니다. 실제 카메라, 음성 통화, 영상 전송은 없습니다.','05-video-guide','ExpansionUi.guidedVideoPage');
await screen('상담이 필요할 때','목적에 따라 선택','119 응급의료상담과 109 심리위기 상담을 구분합니다. 현재 연결과 상담원 응답은 앱 안의 모의 기능입니다.','07-consult-choice','ExpansionUi.entry');
await screen('119 응급의료상담 연결','필요한 지원을 선택','가상 상담원 응답 후 도움받는 사람과 추가 지원을 고를 수 있습니다. 가족 등록은 시작 조건이 아닙니다.','08-consult','ExpansionUi.sessionPage');
await screen('위치와 차량 정보','공유 여부를 직접 선택','가상 위치와 차량 정보를 선택적으로 공유하고 철회합니다. 실제 GPS나 차량 식별정보를 보내지 않습니다.','09-location','ExpansionUi.locationPage');
await screen('가상 위치 공유','선택한 항목만 전달','버튼을 눌러 가상의 위치를 선택해 공유합니다. 실제 위치 조회나 기관 전송은 이루어지지 않습니다.','10-location-shared','ExpansionUi.locationPage');
await screen('추가 지원 선택','상황에 맞게 더하기','상황 변경, 문자·수어, 위치, 협력 요청 등 현재 연결에 맞는 추가 기능으로 이동합니다.','11-support','ExpansionUi.supportPage');
await screen('112 긴급신고','119와 구분된 진입 경로','홈에서 112를 별도로 선택할 수 있습니다. 경찰 발신이나 실제 출동은 연결되지 않습니다.','12-112','ConnectionTarget.EMERGENCY_112; ExpansionUi.sessionPage');
await screen('문자와 의사표현','말하기 어려울 때','정해진 문구를 선택해 앱 내 가상 상황실에 표시합니다. 실제 SMS 발송은 없습니다.','13-text','ExpansionUi.textPage');
await screen('선택한 문구 전달','화면에서 내용 확인','사용자가 고른 예시 문구와 가상 답장을 앱 안에서 확인합니다.','14-text-sent','ExpansionUi.textPage');
await screen('112 공동대응 요청','별도의 가상 검토 절차','가상 경찰 역할의 요청·검토·승인 또는 거절을 보여줍니다. 실제 출동, 호송이나 우선통행 권한은 없습니다.','15-police','ExpansionUi.policePage');
await screen('109 심리위기 상담','상담 경로를 따로 선택','109 상담은 프라이버시를 우선하는 모의 연결입니다. 실제 전문 상담원과 통화하지 않습니다.','16-109','ConnectionTarget.COUNSEL_109');
await screen('청각장애 상황 예시','상황에 맞는 지원','문자, 수어 연결 모형, 위치 등 해당 예시에 맞는 기능을 우선 표시합니다.','17-hearing','experience-catalog.json S08');
await screen('수어통역 연결 모형','3자 연결 상태 표현','사용자·가상 상담원·가상 통역사의 연결 상태만 표현합니다. 실제 영상·수어 통역은 아닙니다.','18-sign','ExpansionUi.signPage');
await screen('쉬운 안내','정보를 짧은 단계로','인지 접근성이 필요한 상황에 맞춰 간단한 안내를 제공합니다.','19-easy','ExpansionUi.easyPage; experience-catalog.json S12');
await screen('상황별 19가지 예시','다양한 가족과 상황','어린이·고령자·임산부·장애 접근성·교통사고·주행 중 위급 상황 등의 예시를 선택합니다.','20-gallery','experience-catalog.json; ExpansionUi.gallery');
await screen('접근성 설정','큰 글씨와 쉬운 안내','큰 글씨와 짧은 안내를 선택합니다. 화면 읽기는 기기의 시스템 설정을 사용합니다.','21-access','ExpansionUi.accessPage');
await screen('EV 전력 계산','입력값에 따른 가정 계산','배터리 용량, 잔량과 부하 등을 입력해 이용 가능 시간을 계산합니다. 실제 V2L 제어는 아닙니다.','22-power','ExpansionUi.powerPage');
await screen('차량 SDK 진단','도어와 배터리 조회','지원 환경에서 Pleos Vehicle SDK의 읽기 진단을 확인합니다. 실제 차량 제어나 응급정보 전송은 하지 않습니다.','23-sdk','MainActivity vehicle diagnostic; README.md');
await screen('기본 응급상황 매뉴얼','오프라인 학습용 안내','기본 대응 항목을 앱에서 확인합니다. 의료진 검토 전 학습용 자료이며 실제 상황에서는 119 안내가 우선입니다.','24-manual','MainActivity manual page');
await screen('주변 차량 협력 동의','가상 위치 공유 후 시작','동의를 선택한 뒤 주변 차량이 도움에 참여하는 오프라인 가상 절차를 시작합니다.','25-coop-consent','ExpansionUi.cooperationPage');
await screen('주변 차량 참여 검토','자발적 참여가 먼저','가상 차량의 참여 가능 조건을 확인하고 초대·수락하는 흐름을 보여줍니다.','26-coop-review','ExpansionUi.cooperationPage; CooperationPolicy');
diagram('비상 표시 승인 요청','기관 역할의 별도 검토','가상 119 또는 112 역할의 확인과 한시 승인을 거쳐 화면 표시를 시작하도록 구성했습니다. 이 단계는 최신 화면을 확보하지 못해 구조도로 설명합니다.',['119 또는 112 역할에 표시 승인 요청','가상 기관 역할의 검토와 승인','승인된 화면 표시 모형 시작'], 'ExpansionUi.cooperationPage','해당 단계 캡처 미확보');
diagram('응급지원 차량 표시 모형','앱 화면의 표현','승인 후 빨간 화면 표시 모형을 보여줍니다. 실제 차량 등화 제어나 긴급차 권한은 없습니다. 이 단계는 구조도로 설명합니다.',['가상 승인 후 화면 표시 모형 시작','응급지원 차량 · DEMO','표시 승인 철회 · 모형 중단'], 'ExpansionUi.cooperationPage','해당 단계 캡처 미확보');
await screen('음성 도움말','문장 강조 예시','음성 도움말은 화면에서 문장을 강조하는 방식입니다. 실제 마이크, 음성 인식이나 음성 재생은 연결되지 않았습니다.','29-voice','ExpansionUi.voicePage');
await screen('만남 장소 안내','가상 안내와 사용자 확인','가상 전문가의 안내와 위치 공유 뒤 목적지를 제안·확인합니다. 실제 내비게이션이나 병원 수용 여부 조회는 없습니다.','30-navigation','ExpansionUi.navigationPage');
await screen('주행 중 음성 요청 예시','정해진 예시 버튼','가상의 음성 요청 문장을 눌러 119 요청 흐름을 시작합니다. 실제 음성 인식은 사용하지 않습니다.','31-driving-voice','ExpansionUi.drivingSetup');
await screen('의료정보 잠금 설정','인증과 잠금 관리','시스템 인증 또는 직접 설정한 앱 PIN을 사용합니다. 정보 삭제·초기화도 이 화면에서 선택합니다.','32-security','ExpansionUi.securityPage; MedicalPrivacyManager');
diagram('우리 가족 안심콜','인증 후 내 정보 관리','본인이 직접 입력한 병력, 알레르기와 투약 내역을 기기에 암호화해 보관합니다. 실제 119 안심콜에는 등록되지 않습니다.',['시스템 인증 또는 사용자가 정한 PIN','내 정보 등록 · 조회 · 수정 · 삭제','가족 가상 예시 관리'], 'ExpansionUi.personalHealthPage; docs/PERSONAL_HEALTH_V10.md');
diagram('건강정보 고속도로 가입 동의','동의 후 예시 화면 확인','가입과 데이터 제공 동의를 선택하는 형태를 보여줍니다. 실제 가입·본인 확인·의료정보 전송은 없습니다.',['가입 및 정보 제공 동의','가입 및 예시 건강정보 확인에 동의','동의하고 예시 보기 · 취소'], 'ExpansionUi.healthLinkPage; docs/HEALTH_LINK_EXAMPLE_V11.md');
diagram('투약 내역 예시와 동의 철회','가상의 기록으로 설명','예시 약품과 날짜만 보여주며 실제 의료기관 조회 결과가 아닙니다. 동의 철회 또는 앱 전환 시 예시 동의는 초기화됩니다.',['건강정보 예시 · 실제 조회 결과 아님','2026.01 예시 약품 A / 2026.02 예시 약품 B','동의 철회 · 내 건강정보로 돌아가기'], 'ExpansionUi.healthExamplePage; docs/HEALTH_LINK_EXAMPLE_V11.md');
diagram('응급정보 선택과 공유','필요한 항목만 동의','가족 예시의 병력·복용약·알레르기 등은 인증 후 항목별로 선택하고 철회합니다. 직접 입력한 내 정보는 자동 공유되지 않습니다.',['도움받는 사람 선택은 선택사항','항목별 동의 · 가상 상황실에만 반영','공유 철회 · 세션 종료 시 정리'], 'ExpansionUi.cardPage; PersonalHealthRecord.kt');
diagram('상담 전달용 정보 요약','검토 후 선택적으로 공유','관찰 내용과 발견 시각 등을 사용자가 확인하고 수정합니다. 규칙 기반 요약이며 진단이나 위험도 판정은 하지 않습니다.',['상황 설명 · 발견 시각 확인','사용자가 요약 검토 및 수정','동의한 요약만 가상 상황실에 반영'], 'ExpansionUi.summaryPage; Experience.kt');
diagram('가상 상황실과 안심콜 모형','추가 지원은 별도로 선택','앱 내 상황실과 안심콜 모형은 가상 데이터 기반입니다. 공식 119 안심콜 조회·등록과 실제 기관 연계는 없습니다.',['앱 내 가상 상황실 확인','119 안심콜 등록 여부 예시','개인정보 공유는 개별 동의'], 'ExpansionUi.centerPage; ExpansionUi.safePage');
diagram('운전자와 탑승자 선택','가상 프로필은 독립적으로 관리','가상 운전자와 도움이 필요한 탑승자를 별도로 선택합니다. 실제 디지털키 인식이나 OEM 연계는 없습니다.',['가상 운전자 선택 또는 해제','도움받는 사람은 별도 선택','실제 디지털키 자동 인식 미연결'], 'ExpansionUi.driverPage; Experience.kt');
await screen('종료와 공유정보 정리','현재 세션을 마무리','종료 시 세션의 공유정보와 동의를 정리합니다. 저장한 개인 건강정보는 별도로 관리합니다.','06-finish','ExpansionUi.finishPage; SessionEngine.finish');
{
 const s=baseSlide('실제 기능과 연계 범위');
 text(s,'앱에서 직접 동작',55,175,420,40,26,C.blue,true);text(s,'화면 탐색 · 동의와 철회\n보안 잠금 · 로컬 암호화 저장\n전력 계산 · 제한 적용',55,229,470,160,23,C.ink);
 text(s,'가상으로 표현',665,175,420,40,26,C.red,true);text(s,'119·112·109 연결과 출동\n상황실·영상·위치·경찰 승인\n건강정보 고속도로와 차량 등화',665,229,500,160,23,C.ink);
 text(s,'실제 기관 연결과 실차 적용에는 별도 협력·검증이 필요합니다.',55,504,1120,55,22,C.muted);
 note(s,'앱의 UI, 동의 상태와 잠금, 로컬 저장 및 계산은 직접 동작합니다. 기관 통신과 건강정보 연계, 카메라·위치 전송, 경찰 승인과 차량 외부 등화는 가상 기능입니다. SDK의 도어와 배터리 읽기는 지원 환경에 한정됩니다.','README.md; docs/HEALTH_LINK_EXAMPLE_V11.md; docs/FEATURE_MATRIX.md');
}
await fs.mkdir(out,{recursive:true});
for(let i=0;i<deck.slides.items.length;i++){
 const png=await deck.export({slide:deck.slides.items[i],format:'png',scale:1});
 await fs.writeFile(path.join(tmp,`slide-${String(i+1).padStart(2,'0')}.png`),new Uint8Array(await png.arrayBuffer()));
}
await (await PresentationFile.exportPptx(deck)).save(path.join(tmp,'draft.pptx'));
await fs.writeFile(path.join(tmp,'notes.json'),JSON.stringify(notes,null,2),'utf8');
await fs.writeFile(path.join(tmp,'missing.json'),JSON.stringify(missing,null,2),'utf8');
console.log('Slides',deck.slides.items.length,'missing captures',missing.length);
