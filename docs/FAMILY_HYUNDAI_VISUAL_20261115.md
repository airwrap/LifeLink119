# 현대차와 가족 합성 이미지 적용

사용자 요청에 따라 원본 차량 사진만 보여주던 홈을 가족과 함께 있는 장면으로 변경했다. 이전 가족 이미지의 따뜻한 분위기와 사용자가 제공한 현대차의 차체·전면 디자인·엠블럼을 참조해 내장 `image_gen`으로 합성했다. 일반 SUV 이미지의 차량 디자인을 재사용하지 않았다.

앱 리소스는 `app/src/main/res/drawable-nodpi/hyundai_family_hero.png`이며 `ui/AssistanceUi.kt`의 홈 배경에서 사용한다. 합성 이미지의 크기는 1536 × 1024다. UI 문구, 메뉴, 음성 응답 및 응급 안내 로직은 이번 이미지 수정의 대상이 아니다.

편집 가능한 설명자료의 표지와 홈 화면을 동일 이미지로 갱신했다. 표지에 AI 합성임을 표시했고, 사용자 제공 사진을 참조했다는 출처와 프롬프트 경로를 발표자 노트에 남겼다. 합성된 가족은 실제 사용자 가족의 사진이 아니며 현대자동차의 공식 광고·인증을 뜻하지 않는다.

저장소에 포함한 파일:

- [합성 이미지 원본](presentation/voice-family-20261115/assets/hyundai-family-hero.png)
- [내장 이미지 생성 도구에 사용한 전체 프롬프트](presentation/voice-family-20261115/assets/hyundai-family-hero.prompt.txt)
- [갱신한 PPTX/PDF와 제작 소스](presentation/voice-family-20261115/README.md)
- [최신 앱의 실제 홈 캡처](presentation/voice-family-20261115/assets/home.jpg)

`Build-And-Test.cmd` 통과 후 전용 QA 에뮬레이터 `emulator-5584`에 데이터 유지 설치했다. 홈 화면에서 차량과 가족의 잘림 및 글자와의 겹침을 확인했다. PPTX/PDF 1·2페이지를 직접 검수했고 3~16페이지의 렌더 이미지가 앞서 검수한 설명자료와 동일함을 해시로 확인했다. [검사 기록](FAMILY_HYUNDAI_VERIFICATION.json)을 저장소에도 보관했다. 이전 상세 기능 검증 기록은 당시 APK에 대한 결과로 유지한다.
