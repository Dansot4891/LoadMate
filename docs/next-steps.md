# 인수인계 · 다음 작업

최종 갱신: **2026-11-10** · 접수 마감 **2026-11-15(일)** · 1차 결과 11/23 · 2차 PT 12/03~04 · 2월 행사 최종 APK
이 문서 하나로 새 세션을 시작할 수 있게 정리했다. 근거·규칙은 링크 문서를 본다.
[기획 대조 planning-review](planning-review.md) · [기능 현황 F01~F66 feature-status](feature-status.md) · [API 계약 api-contract](api-contract.md) · [구조·실행 architecture](architecture.md) · 기획서 원본 `docs/Pleos_물류동반자_기획개발안.docx`(Git 제외)

### GitHub 제출 · release APK (2026-11-15 22시)

- [x] 서명된 release APK `apk/TeamLoadMate_LoadMate_release.apk`(팀명 `Team LoadMate`, 앱명 `LoadMate`, 공식 파일명 규칙). 서버 주소 `http://10.0.2.2:8000`(에뮬레이터 → 같은 PC의 docker compose). release 전용 `src/release` 네트워크 설정은 10.0.2.2만 평문 허용.
- [x] 서명 키 `~/.loadmate/loadmate-release.jks`, 암호는 `android/keystore.properties`(둘 다 Git 제외). **키를 잃으면 같은 서명으로 업데이트할 수 없으니 백업.**
- [x] 검증: `assembleRelease`·`testDebugUnitTest`(129개, 실패 0)·`lintDebug`, pytest 150개(Docker 격리, `GEMINI_API_KEY` 해제). 에뮬레이터에 release 설치 → 운행 목록 표시 확인(debug 앱은 서명이 달라 삭제 후 설치).
- [x] README 필수 항목 보강: 화면 규격 2560×1440, APK 위치, release 실행 방법.
- [x] 지원서 PDF(개인정보 포함)는 `.gitignore` `docs/*지원서*`로 제외.
- [ ] 접수 메일에 저장소 주소와 최종 커밋 해시 기재. 비공개 저장소면 11/16까지 Public 전환, 11/23까지 유지.

### 지원서 PDF 수정본 DOCX 복원 (2026-11-15)

- [x] 사용자 수정 PDF를 기준으로 `docs/Loadmate_공모전_지원서_PDF수정반영.docx` 생성. 원본 PDF·기존 DOCX 보존, PDF의 기재 내용 반영.
- [x] 사용자 요청에 따라 팀·일반(스타트업 포함) 선택, 기재된 참가자 2명의 동의 체크. 빈 참가자 행과 서명란은 유지.
- [x] 3번 소제목 6개에 ①~⑥ 번호 부여, 소제목만 굵게 하고 본문은 일반 굵기로 정리.
- [x] PDF 본문 대조·ZIP/XML 무결성 검사 통과. 8쪽 렌더 육안 확인, 최종 참가 부문 변경 후 1쪽 재확인. 앱/서버 코드 변경 없어 코드 테스트 생략. DOCX는 Git 제외.
- 재실행: 런타임 Python으로 `/tmp/loadmate-final/build.py` 실행. 동일 Python으로 documents 스킬 `render_docx.py`에 결과 DOCX와 `--output_dir /tmp/loadmate-final/render3 --emit_pdf` 전달, `FONTCONFIG_FILE=/tmp/loadmate-final/fonts.conf` 지정. 임시 파일 소멸 시 최종 DOCX에서 편집.

### 제출 당일 점검 (2026-11-15)

> **2026-11-15 21시: 시연 영상 9장면 촬영 완료** (`docs/video/`, 장면별 파일은 demo-video-plan 1절). 촬영 중 수정 4건(운전 중 위쪽 알림 8초, 말하는 중 듣기 끊지 않음, 내역 출처 카메라, 짐 이름 박스 고정 해제)은 **미커밋** — demo-video-plan 1-1절.
>
> **시연 영상 촬영은 [demo-video-plan.md](demo-video-plan.md)부터.** 장면별 상태(3·4번만 사용, 나머지 다시 찍기), 장면별 진행 명령, 미커밋 코드 목록.
> 촬영 전 수정은 완료했다. 되묻기·확인 대답도 Gemini가 해석하고 하드코딩은 없다. 앱이 말하는 동안 듣지 않고, 질문은 30초 뒤 만료된다. 검증: 앱 단위 테스트 125개·lint 통과, pytest 148개 통과, 실제 Gemini와 에뮬레이터 음성으로 확인.

- [x] `docker compose up -d --build` → `/health` ok·db ok. pytest 142개 통과. `assembleDebug`·`testDebugUnitTest`(120개, 실패 0)·`lintDebug` 통과.
- [x] 에뮬레이터 `Pleos_Connect_v2.1` 부팅, debug APK 설치·실행. 운행 목록 → 샘플 운행 열기 확인. 샘플 운행 `planned`, A·B·C `pending`(초기 상태). Loadmate 비정상 종료 없음(크래시 로그는 시스템 앱 `ai.pleos.gleoai.model.provider`의 Firebase 오류뿐).
- [x] **Gemini 실제 호출 첫 확인**: `/assistant/interpret` 4문장 모두 `method: ai`, `ai_status: ok`. "다음 장소에 뭐 내려야 해"(답변), "짐 몇 개 남았어"(답변), "도착했어"(`arrived` 액션), 혼잣말(`ignore`). 서버 응답 3.4~4.0초로 목표 3초 초과(TTS 시간 제외).
- [ ] 출발 전 "짐 몇 개 남았어" 답이 "아직 최초 적재 기준 미확정 상태입니다"로 개발 용어(`assistant/service.py:38`).
- [x] README 필수 항목 중 에뮬레이터 **화면 규격**(2560×1440) 누락 → 2026-11-15 보강. 공식 요구: 앱 이름·소개, 실행 방법, 테스트 환경·설정, 에뮬레이터 버전과 화면 규격, APK 위치.
- [x] 음성 "도착했습니다/도착했어"에 Loadmate가 "A 도착 확인했습니다"라고 말하던 것 제거(내비 도착 음성과 겹침, 사용자 결정). 화면 전환만 한다. `VoiceViewModel`·테스트 수정, assembleDebug·testDebugUnitTest(120개)·lintDebug 통과. "도착했습니다"는 AI·규칙 모두 `arrived`로 해석 확인. **미커밋.**
- [x] 주행 중 적재 이상 알림은 음성만(도착 후·앱이 뒤에 있을 때만 팝업 허용, `cargoPopupAllowed`) — 사용자 요구와 일치 확인(코드).
- 녹화: `adb emu screenrecord start --time-limit 180 <파일.webm>`(최대 180초, VP9 + Vorbis 소리 트랙 있음). 맥 `say` → 호스트 마이크로 음성 입력 시 앱 답변 음성이 다시 마이크로 들어가 오인식("그렇죠")됨 → 말할 때만 `hostmicon`, 끝나면 바로 `hostmicoff`.
- 공식 제출 요건(재확인): 필수는 접수양식(기획서)과 전원 개별 동의서. 시연 영상·GitHub(소스+`apk/팀명_앱이름_release.apk`, release)은 선택 가산점. 비공개 저장소는 11/16까지 공개 전환, 메일에 저장소 주소와 커밋 해시.

### 공모전 지원서 1~6번 작성 (2026-11-10)

- [x] 공식 [개요](https://appdevchallenge.auto-hmg.io/guide/overview)·[제출 방법](https://appdevchallenge.auto-hmg.io/guide/submission)·[심사 기준](https://appdevchallenge.auto-hmg.io/guide/judging-awards)·[개발 환경](https://appdevchallenge.auto-hmg.io/guide/development)·[참가 규정](https://appdevchallenge.auto-hmg.io/guide/rules)·[FAQ](https://appdevchallenge.auto-hmg.io/faq), 기존 기획서 v2.1과 현재 코드·검증 기록을 대조.
- [x] `docs/Loadmate_공모전_지원서_1-6항목_작성본.docx` 작성. 전체 8쪽 중 2~6쪽에 앱 소개·배경·기능·시나리오·개발 계획·기대효과 작성. 원본 양식·기획서 보존, 참가자 정보·앱명 기입란·7~8번·동의·서명은 미작성 상태 유지. DOCX는 기존 `.gitignore` 적용으로 Git 제외.
- [x] 현재 변경사항 반영: 공유 차량 노트(교대 인계 제외), 뒷문 여닫기 후 관측 비교/기사 확인, 계획 변경과 실린 짐 분리, 2D·3D 상차 기록, 내비 중심 음성·도착 알림 복귀. 카메라·외부 배송 연동·실차·실제 AI 응답은 구현/검증 수준을 구분.
- [x] 문서 검증: ZIP/XML 정상, 원본 SHA-256 불변, `word/document.xml` 외 모든 패키지 파일 동일, 수정 범위는 답변 셀 6개와 질문 4개의 페이지 나눔 속성뿐. 렌더 8쪽 전부 육안 확인(잘림·겹침 없음). 렌더 환경의 한글 폰트 누락은 임시 fontconfig로 맑은 고딕→Pretendard 대체해 해결했고 DOCX 자체 폰트는 원본 유지.
- [x] 앱/서버 코드는 수정하거나 실행하지 않음. 문서의 테스트 142개·120개는 기존 2026-11-10 검증 기록 인용이며 이번 작업에서 재실행한 수치가 아님.
- [ ] 제출 전 사용자 검토: MAU 16명(운영사 2곳×기사 10명×월 이용 80%)·4주 유지율 60%는 작성 시 제안한 실증 가정/목표로, 확정 도입처나 실적이 아님. 실제 모집 계획에 맞춰 조정. 팀 정보·앱명·선택 제출 링크·개별 동의/서명은 사용자가 작성.
- 재편집/검증 도구: `/tmp/loadmate-proposal/content.json`, `build.py`, `fonts.conf`, `artifact.md`, `template-audit.json`, `final-render/` (임시 파일). 실행: `/Users/myung/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/bin/python3.12 /tmp/loadmate-proposal/build.py`. 렌더: 같은 Python으로 documents 스킬의 `render_docx.py`에 작성본과 `--output_dir /tmp/loadmate-proposal/final-render --emit_pdf`를 전달하고 `FONTCONFIG_FILE=/tmp/loadmate-proposal/fonts.conf`를 지정. 임시 파일 소멸 시 작성본 DOCX에서 이어서 편집.

### 분할 화면과 운전 중 앱 역할 (2026-11-10 사용자 결정)

- 역할: 운전 중 화면은 내비가 쓰고 Loadmate는 뒤에서 음성만 낸다. **분할 전용 화면은 만들지 않는다.** 분할은 되면 더 좋은 정도로 본다.
- 도착: 도착 안내 음성은 내비가 한다. Loadmate는 팝업과 적재 화면을 맡고 대체 도착 음성을 만들지 않는다. "도착했어"는 기사가 앱에 말하는 예비 명령이다.
- 적재 이상: 카메라가 없어 시연에서는 `POST /trips/{id}/issues`로 신호를 넣고, 실제로는 카메라 인식 결과가 같은 경로로 들어온다고 기획서에 쓴다. 앱 화면 문구는 위 "화면 문구 정리" 결정대로 두고 제출 자료에 구분을 적는다. 음성 알림은 `TripSession`에 구현돼 있다(운행 중 10초마다 서버 확인).
- Pleos 창 규칙(공식 Window Policy): Connect-S는 앱 2개면 1:1, 1개면 전체. Connect-W는 2:1 또는 1:2. 좌우는 운전자가 세 손가락 밀기나 창 막대로 바꾼다. **앱이 분할이나 좌우 위치를 정하는 API는 문서에 없다.**
- 에뮬레이터 실측: 왼쪽 853dp는 시스템 주행 정보 화면(`ai.umos.drivingview`), 앱 영역은 1682×1210dp 한 칸이고 내비와 Loadmate가 같은 칸을 쓴다. 두 번째 앱은 분할되지 않고 교체된다. 에뮬레이터 한계인지 조작을 못 찾은 것인지는 구분하지 못했다.
- **미확인:** 실제 분할 화면, 내비를 앞에 둔 상태의 적재 이상 음성, 내비 도착 신호로 뜨는 팝업.
- 낮은 우선순위: 제출 전에 시간이 남으면 약 840dp 폭(1:1 분할 계산값)에서 심하게 깨지는 화면만 고친다. 지금 앱에는 폭에 따른 레이아웃 전환이 없다. 공식 가이드는 가로·세로 대응을 요구한다.

### 하차 자동 확인 · 방문지 추가 규칙 (2026-11-09 사용자 결정)

- 기사가 하차를 입력하거나 누르지 않는다. \`현장 작업 시작\`·\`하차 완료\` 버튼, 현장 작업·불일치 화면을 없앴다.
- 흐름: 도착 처리 → 뒷문 열림 → 뒷문 닫힘 → 앱이 계획 수량으로 서버에 판정 요청. **관측과 맞으면 묻지 않고 완료**(\`matched\`). 수량이 다르면 팝업·음성으로 차이를 알리고 "넘어가기"(\`driver_confirmed\`) 또는 "다시 확인할게요". 관측이 없거나 오래됐으면 "A 하차 끝났나요?" 팝업·음성으로 한 번 묻는다. 지금은 카메라가 없어 항상 이 팝업이 뜬다. 앱이 뒤에 있으면 팝업이 필요할 때만 알림을 띄운다.
- 도착 처리: 내비 도착 신호, 음성 "도착했어", 앱을 보고 있을 때 뒷문 여닫기. 앱이 뒤에 있을 때는 뒷문만으로 도착 처리하지 않는다.
- 완료 후 다음 차례 방문지가 보낸 경로에 없으면 내비에 자동으로 다시 보낸다(기사 확인 없음, 사용자 결정). 내비는 NaviHelper \`WaypointIndex\`가 FIRST·SECOND뿐이라 한 번에 경유지 2 + 목적지 1까지 받는다.
- 방문지 추가·수량 변경(2026-11-10 사용자 결정): 출발 전후 모두 **경로 계획만 바뀌고 적재 화면 박스와 실린 짐은 그대로**. 카메라가 없는 동안 출발 전 실린 짐은 샘플이 처음 가진 적재 화면 박스 수로 본다(출발 확인 팝업 수량·기준 확정값). 경로 짐과 실린 짐(회수품·경로에서 뺀 방문지 제외)이 다르면 운행 화면 경고, 수량 조절 표시, \`안내 시작\` 때 팝업 문구·음성. 예상 잔량은 0 아래로 내려가지 않는다.
- 넘어가면 계획 수량을 내린 것으로 기록되므로, 실제로 덜 내렸어도 예상 잔량은 계획대로 줄어든다. 다음 관측에서 다시 차이로 나타난다.
- **남은 문제:** 뒷문 신호도 내비 도착 신호도 음성도 없으면 그 방문지를 완료할 수 없다. 실제 카메라 연결 시 뒷문이 닫힌 직후 관측이 하차 전 수량이면 차이로 판정될 수 있어 관측 시점 조정이 필요하다.
- 문구 정리: 내비 버튼은 모두 \`안내 시작\`, \`예상 잔량\` → \`남은 짐\`, \`관측\` → \`확인\`, 개발자용 문구·긴 설명 제거. 음성 답변이 AI인지 규칙인지는 \`AI 답변\`·\`기본 답변\`으로 계속 표시한다.

### 운전 중 음성 경고·도착 후 적재 화면 (2026-11-09 사용자 결정)

- 원칙: **운전 중에는 내비 화면을 가리지 않고 음성만** 쓴다. 팝업은 도착과 도착 후 짐칸 변화 때만 뜬다. 도착 안내 음성은 내비가 하므로 Loadmate는 말하지 않는다. 내비 경로 실패는 음성과 앱 화면으로만 알린다.
- 뒷문 경고: `내비 안내 중 + 내비가 앞에 있음 + 도착 전`에 뒷문이 열리면 "뒷문이 열려 있습니다. 안전한 곳에 정차해 확인해 주세요". 열려 있으면 30초 뒤 한 번 더. 차량 속도가 없어 이 조건을 운전 중으로 본다. 적재 화면에 "운행 중 뒷문 열림 · 시각" 표시(이번 실행 중에만, 서버 저장 없음).
- 도착: 내비 도착 신호(경유지·목적지) 또는 "도착했어" → 팝업 "A 도착 · 눌러서 적재 확인" → 누르면 적재 화면과 이번 방문지 꺼내는 순서 자동 재생, 뒷문 상태 표시. 팝업을 놓쳐도 앱 아이콘으로 돌아오면 같은 화면이 열리고, 다시 "도착했어"라고 하면 팝업이 다시 뜬다. 서버에는 아무것도 저장하지 않는다.
- 운행 유지: 기준 확정부터 운행 종료까지 포그라운드 서비스(`specialUse`)로 앱을 유지하고 뒷문 구독을 잡는다. 앱을 아예 켜지 않으면 동작하지 않는다.
- 상시 듣기에서 못 알아들은 말은 대꾸하지 않고 바로 다시 듣는다(기존에는 여기서 듣기가 멈췄음).
- 앱 전면 전환 실측(시험 앱, 내비 전면, 에뮬레이터): 앱·포그라운드 서비스·PendingIntent 직접 실행은 시스템 차단(`BAL_BLOCK`, 내비가 사라지고 앱은 멈춘 상태로 보여 사용 불가), 전체 화면 알림은 일반 팝업으로만 표시, `SYSTEM_ALERT_WINDOW`는 전환되지만 설정 화면이 없어 adb로만 허용 가능, **알림을 누르는 경로는 정상 전환(2회)**.
- 검증(2026-11-09): pytest 132개, `assembleDebug`·`testDebugUnitTest`(101개)·`lintDebug` 통과. 에뮬레이터에서 내비를 앞에 둔 채 트렁크 열기 → 음성 경고, 팝업 없음, 30초 뒤 반복 / 음성 "도착했어"(맥 음성 합성을 호스트 마이크로 입력, 규칙 기반) → 팝업 → 탭 → 적재 화면·재생·뒷문 표시 / 팝업 미탭 후 앱 복귀 → 같은 화면 재생 / 적재 화면에서 트렁크 열기 → "뒷문 열림", 경고 없음 / 서버 운행 상태 변화 없음. 증거 `docs/evidence/2026-11-09/`.
- **미확인:** 내비 도착 신호로 뜨는 경로(에뮬레이터에서 위치 이동 불가, 코드만 연결), AI 경로의 `arrived` 실제 호출(키 없음), 실차.
- **남은 문제:** 회수·메모처럼 확인이 필요한 음성은 확인 창이 앱 안에 있어 내비가 앞에 있으면 보이지 않고, 확인 전까지 다시 듣지 않는다. 음성으로 "맞아/취소" 답하는 경로가 필요하다.

### 운행 목록 브랜딩·아이콘 재적용 (2026-11-09)

- [x] 운행 목록 상단에 PROJECT 심볼과 `Loadmate` 이름 배치, 화면 제목을 `오늘의 운행`에서 `운행 목록`으로 변경.
- [x] 기존 Android 로고가 남아 있던 `mipmap-mdpi`~`xxxhdpi`의 일반·원형 WebP 10개를 Loadmate 심볼로 교체. 적응형 벡터와 함께 밀도별 리소스도 같은 디자인으로 맞춤.
- [x] `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug`, `git diff --check` 통과. Pleos 에뮬레이터에 APK 재설치 후 운행 목록에서 `Loadmate`·`운행 목록` 텍스트와 심볼 표시를 화면 캡처로 확인.
- [x] 차량 하단 도크 확인(2026-11-09): 녹색 Android 그림은 별도 PleosLab 앱 아이콘. Loadmate는 그 오른쪽에 새 상자 심볼로 표시되며, 해당 아이콘을 눌러 `com.loadmate.app/.MainActivity` 실행 확인. `adb shell cmd package uninstall -k com.loadmate.app`로 앱 데이터 보존 후 동일 서명 APK 재설치하여 도크의 별도 아이콘 등록 확인.

### Loadmate 로고·앱 아이콘 (2026-11-09)

- [x] PROJECT 심볼 시안: 상자(적재) + 확인 경로(기사 확인). `docs/brand/loadmate-logo.svg`에 로고 원본 저장.
- [x] Android 기본 런처 아이콘을 Loadmate 적응형 아이콘으로 교체. 배경은 Pleos `basic_700` light, 강조색은 `informative_active` light를 참고. 단색 테마용 벡터 별도 제공. 공식 Pleos 자산으로 표기하지 않는다.
- [x] 검증: 런처 XML·로고 SVG 파싱 통과, `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug` 통과(2026-11-09). 실제 런처에서의 육안 확인은 미실시.

### 실행 환경 종료 (2026-11-09)

- [x] 사용자 요청에 따라 `docker compose down`으로 Loadmate backend·DB 컨테이너 종료. `docker compose ps` 빈 목록 확인.
- [x] Pleos 에뮬레이터 `emulator-5554` 종료. `adb devices` 빈 목록 확인.
- 재실행: 루트에서 `docker compose up -d --build`, 에뮬레이터는 Android Studio AVD에서 다시 시작.

### 실행 환경 재시작 (2026-11-09)

- [x] `docker compose up -d --build`로 backend·DB 재실행. `docker compose ps`에서 backend Up, DB healthy; `GET /health`는 `status: ok`, `database: ok`.
- [x] `Pleos_Connect_v2.1` 에뮬레이터 재시작. `adb devices`에 `emulator-5554`, `sys.boot_completed=1` 확인. AAOS 사용자 10에서 `com.loadmate.app/.MainActivity` 시작, UI에서 `운행 목록` 확인.

---

## 1. 한눈에 보기

| 항목 | 상태 (2026-11-08) |
|---|---|
| 단계 | 기획서 14장(대표 시나리오)·15장(회수·메모·인계)까지 **앱+서버 구현**. A0 구조 이행과 대표 업무·내비 회귀 검증 완료. 제출 준비는 아직 시작 안 함 |
| 사용자 방침 | "기능을 먼저 끝까지 구현" (2026-11-08). 다음 세션에서는 남은 기능과 11/15 제출 준비의 우선순위를 확인할 것 |
| Android | 운행 선택 첫 화면 + 왼쪽 세로 메뉴 5개(운행 · 적재 · 메모 · 내역 · 연결 상태) + 음성 패널. 단위 테스트 63개, 빌드·lint 통과 |
| Backend | FastAPI + PostgreSQL(Docker). Feature: health·trip·fleet·memo·handover·assistant. Alembic 3개. pytest 97개 |
| Pleos SDK (앱 연결) | NaviHelper 3.0.5(목적지·경유지), Vehicle 2.3.2 Door REAR, SpeechToText 3.3.1 ON_DEVICE, TextToSpeech 3.3.1 ON_DEVICE. **모두 에뮬레이터 확인만**, 실차 미확인 |
| 데이터 출처 | 운행·관측·이상 감지는 **샘플**(화면에 표시). 음성 해석은 **AI(Google Gemini, API 키 필요, 실제 호출 미검증)** 가 먼저 판단하고 키가 없거나 끊기면 **규칙 기반**. 화면에 어느 쪽인지 표시. 차량 노트 초기 3건은 샘플 |
| Git | `master` 브랜치. Android 화면 구조 재점검·S10 상태 표시·문서 갱신에 **미커밋 변경 다수**(기존 작업 포함). 새 세션은 `git status`·`git diff`부터 확인하고 변경 사항을 보존할 것. **원격 저장소 없음, push 안 함** |
| 실행 상태 | 세션 종료 시 서버·에뮬레이터 실행 중일 수 있음. 샘플 운행은 2026-11-09 마지막 확인 시 `in_progress`(A 완료, B·C 대기, 잔량 박스 7). 재시연 전 초기화 필요 |

### 차량 노트 전환 (2026-11-09 사용자 결정)

- 장비·인계 화면을 없애고 **차량 노트**로 대체. 고정 장비 목록과 기사 선택 없이 누가 타든 같은 내용을 보고 고친다. 앱의 새 메모는 모두 공유.
- 운행 시작에 차량 문제, 납품처 도착에 장소 메모·도구 위치를 표시. 음성으로 메모 저장·찾기·수정·삭제(AI가 초안, 기사 확인 후 저장).
- AI: 서버에서 Google Gemini 호출(`integrations/assistant/note_ai.py`, 2026-11-09 무료 등급 사용 결정). 저장소 루트 `.env`에 `GEMINI_API_KEY`를 넣고 `docker compose up -d`. 모델은 `LOADMATE_AI_MODEL`(기본 gemini-3.5-flash-lite). 코드 제출이라 키는 Git에 넣지 않는다. **무료 등급은 입력이 Google 제품 개선과 사람 검토에 쓰일 수 있고 약관상 개인·민감 정보를 보내면 안 된다. 샘플 데이터 시연에만 쓰고 실제 운영은 유료 등급으로 바꾼다.** **키가 아직 없어 실제 Gemini 응답·응답 속도는 미검증.** 키가 없거나 끊기면 규칙 기반으로 동작하고 화면에 표시.
- 기획서 F56·F59~F62(교대 인계)를 앱에서 뺐다. 서버 API·테이블·샘플 장비(`fleet`, `handover`)는 복구할 수 있게 남겼다. 기획자와 범위 확인 필요.
- 제출 방식: 심사자가 GitHub 코드를 직접 실행할 것으로 예상(사용자 확인). 키는 Git에 넣지 않고 README로 안내. 키 없이도 전체 화면과 규칙 기반 음성이 동작. 심사 전용 키를 별도 전달할 수 있는지 공모전 규정 확인 필요.
- 음성 방향(사용자와 합의): 키워드 규칙은 범위가 좁아 대비책으로만 두고, 판단은 AI가 한다. 매 발화를 AI로 보내도 된다.
- 방문지 장소 검색(2026-11-09): 샘플 장소 2곳을 없애고 VWorld 검색 API 연결. 좌표는 저장하지 않고 내비 전달 직전 주소로 조회(`GET /trips/{id}/route-points`, 마이그레이션 `b9d4e1a7c2f3`). 검증: pytest 126개, assembleDebug·testDebugUnitTest(90개)·lintDebug 통과, PostgreSQL 적용·`alembic check` 차이 없음. 실제 VWorld 키로 12개 검색어 결과 79건 중 77건 좌표 조회 성공(오류동 1556은 VWorld 주소 검색에 없음), 호출 0.1초대, 키 도메인 오류 없음. VWorld 시간 초과가 한 번 발생해 재시도가 필요할 수 있음. 택배사 주소 표기(시·도 줄임, 시·도 생략, 건물번호 뒤 동·층·호)도 좌표 조회 가능(실제 VWorld 확인, 건물번호가 다르면 찾지 않음). 샘플 3곳도 좌표를 빼고 실제 도로명주소로 교체(시드·DB, pytest 140개). 에뮬레이터 앱 재시작 후 내비 전달 → 서버 `route-points` VWorld 조회 → 확인창 A→B→C → `RouteStarted`(2.9km·9분), DB 좌표 0건 유지 확인. 앱 화면에서 검색 결과를 골라 추가하는 동작은 미확인. **약관:** VWorld 이용약관 "오픈플랫폼에서 제공되는 데이터는 무단으로 저장하지 못합니다", 지오코더 문서도 DB 저장 불가. 좌표뿐 아니라 검색 결과에서 저장하는 이름·주소도 해당될 수 있어 운영기관(1661-0115, vworld@spacen.or.kr) 승낙 여부 확인 필요. 이후 방문지 검색 목록을 직접 입력(이름·주소·수량, VWorld는 위치 확인만)으로 교체하고 장소 수정 API 추가. 목록은 길게 누르기·손잡이 드래그, 오른쪽 수량·삭제, 삭제·운행 종료 팝업은 확인/취소만. 검증: pytest 141개, assembleDebug·testDebugUnitTest(112개)·lintDebug 통과, 에뮬레이터에서 손잡이 드래그·길게 눌러 드래그·팝업 표시·장소 수정 창 확인. 삭제 확정과 직접 추가 저장은 샘플 운행 보존을 위해 미실행. 택배사 서버 연동은 기획서 항목, 앱은 직접 추가·수정 제공.
- 다음 순서:
  1. 사용자가 저장소 루트 `.env`에 `GEMINI_API_KEY`를 넣으면 실제 답변·응답 속도 확인. 기사 말투 문장 20개 정도로 측정, 말이 끝나고 첫 소리까지 3초 이내가 목표.
  2. [x] 음성 전체를 AI로 전환 (2026-11-09 코드 완료, 실제 호출은 1번에서 확인). 한 번의 호출로 끝낸다: 남은 방문지·물품·잔량·회수품·메모를 처음부터 함께 보내고, 변경은 초안 후 기사 확인. 소음·혼잣말은 `ignore`(말하지 않고 팝업 없이 다시 듣기). 답변은 문장 단위로 읽기. AI 대기 5초. AI 답은 서버 사실·메모를 인용해야 하고, 인용에 없는 숫자는 서버 값으로 바꿔 읽는다. 재질문의 답은 규칙 기반으로 이어 간다. 1번 측정 때 소음·혼잣말을 업무로 잘못 듣거나 업무 말을 `ignore`로 버리지 않는지도 본다.
  3. 기준을 못 맞추면 자주 쓰는 질문만 규칙으로 즉답. 실제 호출을 확인한 뒤에 기획서에 "구현"으로 적는다.
  4. 11/15 접수 제출 준비. README 실행 순서 재확인.
- 미커밋 상태: 이 전환 작업 전체와 기존 Android 미커밋 작업이 섞여 있다. 앱 `feature/fleet` 삭제는 staged.

### 화면 문구 정리 (2026-11-09 사용자 결정)

- 앱 화면과 시드 이름에서 샘플·시뮬레이션·카메라 미연결·에뮬레이터 문구를 뺐다. 실제 앱처럼 보이게 하려는 사용자 요청. 서버 `data_source`·`source` 값과 이 문서·README의 구분은 유지한다. **카메라·AI 인식은 여전히 미구현**이고 상차·짐 밀림 소식은 앱 버튼이 보낸다. 제출 자료에는 구분을 적는다(planning-review 제안 4).
- 앱의 상차·짐 밀림 시뮬레이션과 이상 기록 버튼을 삭제(같은 날 사용자 결정). 박스는 시드에 있고 기준 확정 시 서버가 전부 실림으로 바꾼다. **지금 적재 이상을 만드는 경로는 `POST /trips/{id}/issues`뿐**이다. 샘플 관측 표기는 "적재함 관측". 시드 운행 이름은 "분당 납품 운행 · A·B·C". 아래 시연 순서의 시연 도구 항목은 더 이상 없다.
- 이상 카드: 미해결 이상이 있을 때만 빨간 문구와 "이상 없음" 버튼 하나를 적재 화면에 표시.
- 적재 공간(2026-11-09): 출처 표시("기사 확정" 등) 삭제, 3D·2D를 카드 전체 폭으로 키우고 실림 수·구역별 수·꺼내는 순서·방문지 색은 아래 카드로 분리. 에뮬레이터 확인. **칸 수는 앱(`CARGO_ZONES`·`CARGO_LANES`·`CARGO_LAYERS`)과 서버(`schemas.py` 자리 검증)에 3×3×3으로 고정**이라 차량·카메라별로 달라지려면 양쪽 수정과 API 계약 변경이 필요하다.
- 검증(2026-11-09): `assembleDebug`·`testDebugUnitTest`(59개)·`lintDebug`, pytest 97개 통과. 에뮬레이터에서 기준 확정 후 실림 10개, API로 넣은 이상의 빨간 문구 표시, "이상 없음" 탭 → 서버 `resolved/no_issue`, 카드 사라짐 확인. 검증 후 시드 초기화.
- 적재 화면 추가(2026-11-09): 적재 공간 상단 "재생" 버튼(꺼내는 순서 미리 보기, 3D·2D, 화면 전용, 서버 기록 없음), 3D 확대·축소(버튼·핀치·마우스 휠), 이상 물품 빨간 표시와 범례 아래 "자리 · 물품 · 내용" 문구. 이상 기록에 `unit_id` 추가(마이그레이션 `e3b8f1c04a77`). **`python -m app.seed`는 A 박스 3번에 이상 1건을 넣어 시작**한다(비전 AI 전 시연용, `demo_issue`). 테스트 시드에는 없다. 꺼내는 순서는 위층·나중 상차 우선 규칙이며 AI 판단이 아니다.
- 검증: 앱 단위 테스트 64개·lint, pytest 99개, PostgreSQL 마이그레이션 적용. 에뮬레이터에서 3D·2D 재생, + 버튼 확대, 이상 표시와 "이상 없음" 처리 확인. **마우스 휠과 핀치는 adb로 재현하지 못해 미확인.**
- 주의: 같은 에뮬레이터·저장소를 다른 세션이 함께 쓰면 탭이 어긋날 수 있다.
- 미결정: `CLAUDE.md` 원칙 5를 "문서에 표시"로 고칠지, 칸 수를 차량별 데이터로 바꿀지.

### 새 세션 인수인계 (2026-11-08)

1. `CLAUDE.md`와 이 문서를 읽고 `git status --short`·`git diff --stat`으로 미커밋 Android 구조 변경과 문서 변경을 확인한다. 기존 변경을 초기화하거나 덮어쓰지 않는다. Android 작업은 `android/AGENTS.md`·`android/CLAUDE.md`, 서버 작업은 `backend/AGENTS.md`·`backend/CLAUDE.md`를 추가로 따른다.
2. **완료된 검증:** Android `assembleDebug`·`testDebugUnitTest`(31개)·`lintDebug`, backend pytest 61개. 에뮬레이터에서 A 불일치(예상 7/관측 8) 미저장 → 재관측 7 → A/B/C 완료·잔량 0, 서버/앱 재시작 복원. 별도 초기화 운행에서 A→B→C 내비 경유지 2개 전달과 앱 복귀 후 경로 시작 결과 수신. 각각의 상세 근거는 10-3절.
3. **현재 샘플 데이터 (2026-11-09):** 전체 시연을 마친 `completed` 상태(A/B/C 완료, 회수품 반납, 잔량 0). 앱 재실행 시 이전 내비 결과는 조회하지 못한다. `app.seed --reset`은 운행 기록뿐 아니라 일부 샘플 장소·차량·장비 메모도 삭제하므로 재시연 전 범위를 확인한다.
4. **다음 선택지:** A절 남은 코드 기능(F51 사진, F66 오프라인 디스크/요청 대기열, F29 임시 반출·재적재, 내비 재실행 상태 조회), B절 영상 분석(녹화 영상 필요), C절 제출 준비(11/15 마감). 사용자 방침은 기능 우선이지만 마감이 가까워 새 세션에서 순서를 정한다. API 변경 전에는 `docs/api-contract.md`를 먼저 갱신한다.

## 2. 새 세션에서 다시 띄우기

```bash
cd ~/StudioProjects/loadmate
colima start                                   # 재부팅 후 필요 (Docker 엔진)
docker compose up -d --build                   # backend + db. 시작 시 alembic upgrade + 샘플 시드(없을 때만)
curl -s localhost:8000/health                  # {"status":"ok","version":"0.1.0","database":"ok"}
docker compose exec backend python -m app.seed --reset   # 샘플 운행 초기화(시연 반복)

~/Library/Android/sdk/emulator/emulator -avd Pleos_Connect_v2.1 -no-snapshot-save -no-boot-anim -feature -Vulkan,-GLDirectMem &
cd android && export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 shell am start --user 10 -n com.loadmate.app/.MainActivity   # AAOS 사용자 10

# 서버 테스트 (로컬 venv 없음 → Docker 이미지에 마운트)
cd .. && docker run --rm -v "$PWD/backend:/srv" -w /srv loadmate-backend sh -c "pip install -q -r requirements-dev.txt && pytest -q"
```

앱 첫 실행: **오늘의 운행 → 샘플 운행 선택**. 기사 선택은 없다(2026-11-09 차량 노트 전환).
종료: `docker compose stop`, `adb -s emulator-5554 emu kill`.

## 3. 시연 순서 (현재 구현 기준)

1. 오늘의 운행 → 샘플 운행 선택 → 운행 탭 S01에서 "이 차 확인" 카드 → "내비 안내 보내기 (A부터)"
2. 출발 팝업 "현재 적재 상태를 확인해주세요."(총 수량·방문지별 수량, 카메라가 없어 `계획 수량 기준` 표시) → "맞아요, 출발" = 최초 기준 확정
3. 이어서 내비 확인창 "보내기" → 내비가 화면을 덮음 → 앱으로 돌아오면 "안내 시작됨 · 3.1km · 11분"
4. 음성 "도착했어" → 적재 화면·꺼내는 순서 → 뒷문(Virtual Console 트렁크) 열었다 닫기 → 팝업 "A 하차 끝났나요?" → "맞아요"
5. 수량이 다르면(카메라가 없어 시연은 `POST /trips/{id}/observations`로 관측을 넣는다) 팝업에 차이 표시 → "넘어가기" 또는 "다시 확인할게요"
6. B를 내비에 다시 보내기 → B·C 진행 → 회수품 "반납 완료" → 운행 완료
7. 곁가지: 🎤 말하기("다음 장소에 뭐 내려야 해", "빈 용기 두 개 회수했어 창고로 반납해" → 확인), 메모 메뉴(차량·장소·이번 운행 메모 추가·수정·삭제), 내역 메뉴(기능별 드롭다운), 운행 종료(남은 업무 있으면 사유 선택), 적재 메뉴 3D·2D 전환과 "시연 도구 열기"(상차 시뮬레이션·이상 포함 상차·되감기, 기준 확정 뒤 주행 중 짐 밀림, 샘플 이상 의심)

## 4. 지금까지 한 일 (커밋 순)

| 커밋 | 내용 |
|---|---|
| `55124f6` | 저장소 뼈대(앱 S10·서버 /health·Docker) + `core/pleos`(NaviHelper·Door REAR·STT) + 에뮬레이터 실측 3건 |
| `1b01341` | 추가 실측 문서: 경유지 2개, 헤드업 알림 카테고리, TTS, 백그라운드 STT, 도착 신호 확인 불가 |
| `8bffa9b` | API 계약(운행) + 서버 `features/trip`(예상 잔량·비교·멱등 키·확인 필요 미반영) + 마이그레이션 1 + 시드 |
| `8b78386` | 앱 대표 시나리오(S01·S05·S03·S04·S06) + 에뮬레이터 연속 실행 검증 |
| `6406f9d` | 서버 확장: 운행 관리·회수·이상·이력, 기사·장비, 메모(개인/공유), 인계·인수, 음성 해석(규칙 기반) + 마이그레이션 2 |
| `cd7e87b` | 앱 확장: 탭 구조, 방문지 관리, 회수, 이상, 2D 적재 배치, 현장 메모, 기록·메모·장비·인계 화면, 음성 패널, TTS·헤드업, 오프라인 표시, `feature-status.md` |

코드 위치 요약 (상세: architecture.md)
- 앱 `android/app/src/main/java/com/loadmate/app/`: `core/{network,session,notify,pleos}`, `shared/{designsystem,format}`, `feature/{trip,records,fleet,assistant,settings}`, 조립 `AppContainer.kt`, 탭 `MainActivity.kt`
- 서버 `backend/app/`: `core/{config,db,errors,actor}`, `features/{health,trip,fleet,memo,handover,assistant}`, `integrations/assistant/rule_based.py`
- 실측 증거 화면 `docs/evidence/2026-11-08/` (01~25)

## 5. 확정된 결정 (사용자, 2026-11-08)

| 결정 | 내용 | 반영 위치 |
|---|---|---|
| 내비 리스너 | 운행 중(시작~종료) 계속 수신, 종료 시 해제. 앱이 내비 기반으로 동작하므로 당연한 기본값 | CLAUDE.md 원칙 8, `TripSession` |
| 목적지 전달 | 보내기 전 기사 확인, 실패 시 실패 표시·다시 보내기·목적지 유지. 출발 시 남은 방문지를 경유지로 한 번에 | `RouteDialog`, `routeRequestStatus` |
| 음성 변경 | 인식 문장을 보여 주고 기사가 맞다고 확인해야 저장 | `VoicePanel`·서버 `assistant` |
| 관측값 | Pleos SDK에 카메라 API 없음 → **샘플 관측·기사 입력**, 출처 표시 | `ObservationInput`, `sampleZones` |
| 작업 순서 | 제출 준비보다 기능 구현 먼저 | — |
| 코드 구조 | 앱: Hangari(Flutter)식 화면 폴더 + DTO/mapper(kotlinx.serialization), DI는 `AppContainer` 유지, 운행 탭은 화면별 ViewModel. 서버: 모든 기능 router·schemas·service·repository·models 통일, trip service 분리, 샘플 시드 `app/seed.py`, 기능 의존 한 방향. 운행 선택 화면(F02) 추가 | `android/CLAUDE.md`, `backend/CLAUDE.md` |
| 디자인 기준 | Pleos Connect 공식 배포 Figma(design assets·display guide 2.1.5)를 가장 우선하고 Figma에 없는 것만 직접 제작. Connect-S 값을 공식 값으로 사용 (2026-11-09). 원본 `.fig`는 Git 제외, 값만 코드에 반영 | `shared/designsystem` |
| 진행 중 기본값(사용자 확인 전) | 대화 이해 = 규칙 기반 해석기(LLM 교체 가능), 인계 요약 = 규칙 기반, 바코드(F06) 제외 | feature-status.md |

## 6. 다음 할 일

- [ ] 앱 아이콘 교체: Loadmate 로고 확정 후 공식 Figma 규칙(144×144dp, 브랜드별 마스킹)으로 제작 (2026-11-09 사용자가 나중에 넣기로 함)
- [ ] `shared/designsystem/components`의 미사용 컴포넌트는 처음 쓸 때 조작 확인 (슬라이더 끌기, 날짜·시각 휠)

A0. 구조 이행 (2026-11-08 결정, 영상 분석 붙이기 전에) — 규칙은 `android/CLAUDE.md`·`backend/CLAUDE.md`
- 서버
  - [x] `trip/seed.py` → `app/seed.py` (Dockerfile CMD·conftest·api-contract·architecture 명령 경로 함께)
  - [x] `core/time.py` 로 시각 함수 통일 (memo·fleet·trip 의 `_utc`/`as_utc` 중복 제거)
  - [x] memo·fleet·handover·assistant 를 router·schemas·service·repository·models 로 분리 (memo 는 멱등·공개 범위 규칙을 service 로)
  - [x] memo 가 `trip.models.Stop` 직접 쿼리 → trip repository 공개 메서드 호출
  - [x] `trip/service.py`(517줄) → `trip/service/{trips,stops,loading,pickups,issues,common}.py`
  - [x] pytest 61개 유지 + PostgreSQL 대표 시나리오 재확인
- 앱
  - [x] `ApiResult`·`ApiError` 를 `feature/trip/domain` → `core/network` 로 이동
  - [x] kotlinx.serialization 도입(AGP 8.7.3·Kotlin 2.0.0 호환 버전), org.json 파서 9개 파일 → `dto/` + `mapper/`
  - [x] `HttpXxxRepository` → `XxxRepositoryImpl`, 운행 데이터는 Repository StateFlow 로 보관
  - [x] 운행 탭 분리: `trip/presentation/{overview,loadcheck,driving,sitework,mismatch}/` 화면별 ViewModel, 내비 리스너는 운행 범위 객체로 이동
  - [x] 나머지 기능(records·fleet·assistant·settings)도 `presentation/<screen>/` 폴더로
  - [x] 운행 선택 첫 화면 (F02, `GET /trips`), `AppContainer.sampleTripId` 제거
  - [x] 단위 테스트 31개 통과 + 에뮬레이터 A→B→C 대표 업무 흐름 재확인. 샘플 초기화 후 최초 기준 10 → A 예상 7/관측 8 확인 필요(미저장) → 재관측 7로 A 완료 → B 관측 4·완료 → C 관측 0·완료 → 잔량 0. 서버·앱 재시작 복원 확인
  - [x] 내비 목적지·경유지 요청 재확인. 별도 샘플 초기화 후 기준 10 확정, 앱 확인창 `A → B → C`, Pleos 내비에서 경유지 2개 포함·안내 시작, 앱 복귀 후 `안내 시작됨 · A · 판교역 납품처 (샘플) · 3.1km · 11분` 수신. 업무 완료 흐름과 내비 흐름은 각각 재현했으며 같은 초기화 운행에서 한 번에 연속 실행하지는 않음

A. 남은 코드 기능 (선택)
- [x] 앱 내부 방문지 **2D 위치 개요** 1단계: 주행 화면에 A→B→C 좌표를 직선으로 연결하고 방문지 이름을 표시. 샘플 데이터이며 **도로 지도·실제 경로·현재 위치·회전 안내는 아님**을 화면에 명시. 도로 기반 안내는 지도 타일/스타일 공급자, 경로 계산 API, 차량 위치 권한, Pleos 차량 앱 배포 허용 범위를 확정한 뒤 진행.
- [x] 앱 내부 지도 지원 방식 재확인: Pleos NaviHelper 3.0.5 공식 가이드와 SDK 소스에서 `requestNavigationScreen()`은 Navi Service에 화면 요청을 전달하는 `Unit` 메서드(`NAVI_CUSTOM_MAP`)이고 앱에 삽입할 지도 View/Surface나 경로 geometry를 반환하지 않는다. **Loadmate 자체 Activity 안에 2D 지도 표시 자체는 가능**: MapLibre Native Android의 embeddable `MapView`가 공식 문서에 있다. 다만 지도 타일·스타일 데이터 제공자/이용 조건, 도로 경로 계산 API, 위치 갱신, 턴별 안내를 별도로 마련해야 하며 Pleos 내비가 계산한 경로선을 그대로 가져오는 방식은 확인되지 않았다. Android for Cars 내비 템플릿은 `CarAppService` 내비 카테고리·권한·품질 요건을 갖춘 별도 내비 앱 구조이므로 현재 Compose Activity에 자동 적용되지 않는다. Pleos 공모전/배포의 주행 중 자체 Activity 지도 허용 여부는 별도 확인 필요.
- [x] **앱 내부 실제 2D 지도 표시**: OSM 온라인 도로 타일을 Loadmate 운행 화면의 Compose Canvas에 직접 그림. 방문지 점과 직선 연결만 제공. Pleos 에뮬레이터에서 실제 지도 타일 표시 확인. OSM 타일 정책에 맞춰 앱 고유 User-Agent, 화면 출처 표기, 7일 디스크 캐시, 조건부 재요청을 적용. 타일 서비스는 최선 노력 제공이며 출시/대규모 사용 공급자로 확정한 것은 아님.
- [x] **앱 내부 지도 제거 (2026-11-09 사용자 결정)**: 안내를 시작하면 내비가 화면을 덮으므로 운행 화면에서 뺐다. `RouteOverview.kt` 삭제(복구는 Git 이력 `2c469c3`).
- [ ] 운행 화면 통합 후 미확인: 팝업 위치 수정본(방문지 추가·취소), 출발 전 화면, 현장 작업에서 목록 복귀. 샘플 운행이 `completed`라 남은 방문지가 없어 초기화 후 확인 필요
- [ ] **앱 내부 실제 경로 안내**: Pleos NaviHelper 3.0.5는 앱 내부 지도 View/경로선 좌표를 반환하지 않음. 도로 경로 계산 API·현재 위치 갱신·회전 안내·재탐색, 지도 공급자/라이선스, 주행 중 자체 화면 허용 여부를 확정해야 함. 기존 Pleos 내비 앱으로의 경로 전달은 계속 동작. 3D는 지도 엔진을 확정한 뒤 검토.
- [ ] 자동 도착 검증: NaviHelper의 `onWayPointArrived`·`onDestinationArrived` 콜백은 있으나 에뮬레이터 위치 이동이 재현되지 않아 미확인. 신호 수신 시 현장 작업 제안, 누락 시 기사 확인 유지. 좌표 기반 판정은 위치 SDK·오도착 방지 검증 후
- [ ] 메모 사진 첨부 (F51)
- [ ] 오프라인 디스크 보관·요청 대기열 (F66) — 지금은 이번 실행 중 메모리만
- [ ] 임시 반출·재적재 (F29 일부)
- [x] S10에 TTS·알림 권한 상태 표시 (F65). TTS는 사용 전/사용 가능/사용 불가, 알림은 현재 권한 상태 표시
- [ ] 앱 재실행 시 내비 현재 안내 조회 (`getRouteStateInfo`, 권한 `NAVI_ROUTE` 추가 필요)
- [x] 음성 상단 ON/OFF와 `꺼짐/듣는 중/대기 ON` 상태 표시, ON일 때 한 발화 후 다음 청취 자동 요청. TTS 출력 중에는 다음 청취를 보류하고, 변경 요청의 기사 확인 절차 유지. 실제 에뮬레이터 연속 음성 실측은 후속 필요
- [x] 상단 음성 표시 단순화: 기사 이름 문구 제거, Pleos `regulation_green`/`regulation_red` 토큰으로 `음성 ON · 끄기` / `음성 OFF · 켜기` 테두리 버튼 표시. 버튼 전체가 ON/OFF 토글이며 기사 선택 자체는 장비·인계 업무에 필요하므로 해당 화면에서 유지

B. 녹화 영상 분석 `vision/` (짐칸 녹화 영상 필요 — 사용자 준비 여부 확인)
- 관측 API(`POST /trips/{id}/observations`, `source: recorded_video`, `zones`)는 이미 받을 수 있음

C. 제출 준비 (마감 11/15)
- 우선순위 제안 (2026-11-08): **① 제출 실행물**(접근 가능한 서버 주소·서명 APK·설치 검증) → **② 시연 영상과 출처 표기** → **③ GitHub/기획서 정리** → **④ 남은 선택 기능**. F66 오프라인 영속화는 데이터 정합성·재전송 검증 범위가 크고, F51·F29는 API와 UI 확장이 필요하다. 영상 분석은 녹화 입력이 준비돼야 착수한다. 사용자 기존 방침(기능 우선)은 유지하되 11/15 제출에 필요한 선행 조건을 먼저 확인한다.
- [ ] 시연 영상 녹화 (실제 동작 / 에뮬레이터 신호 / 샘플 / 규칙 기반 자막 구분)
- [ ] release APK → `apk/Loadmate_Loadmate_release.apk` (팀명 확인), 서명 키 생성·Git 제외, release 서버 주소 `-Ploadmate.apiBaseUrl=`. `assembleRelease`는 성공했으나 산출물은 **서명 안 된** `android/app/build/outputs/apk/release/app-release-unsigned.apk`이고 서버 주소도 미설정. 제출·설치용으로 사용 불가
- [x] README 필수 항목(앱 이름·한 줄 소개·실행 방법·테스트 환경·APK 위치) 점검. 현재 샘플 상태, 서버 주소·서명·설치 검증 조건 명시
- [ ] GitHub 원격 저장소 생성(공개 여부 결정), push
- [ ] 기획서 수정 반영 요청 (planning-review §5) — 기획자와 공유

## 7. 남은 결정 (사용자·기획자)

- [ ] 2월 현장 시연 서버: 클라우드 배포 vs 앱 내 데모 모드(명확 표시) — release APK가 서버에 닿지 못하면 동작 안 함
- [x] 대화 AI 실행 위치: 서버에서 외부 API(Google Gemini 무료 등급) 호출 (2026-11-09). API 키 준비는 남음
- [ ] 외부 서버·외부 AI 사용 가능 여부 공모전 문의 (/guide/help)
- [ ] GitHub 공개 여부, 팀명(APK 파일명)

## 8. 알려진 문제·주의 (다음 세션이 같은 데서 막히지 않게)

- **내비가 화면을 덮는다**: `requestRoute` 시 내비 앱이 Loadmate와 같은 패널을 차지(Loadmate ON_STOP). 결과는 운행 범위 리스너로 받는다. 앱 재실행 후에는 이전 요청 결과를 모른다 → "이번 실행에서 보낸 기록 없음"
- **도착 신호 확인 불가**: `adb emu geo fix` 위치는 내비가 NMEA 체크섬 오류로 버림. Virtual Console 가상 주행은 속도만 바뀌고 내비 위치는 그대로 → 도착은 기사 확인 버튼
- **호스트 마이크**: `adb emu avd hostmicon/off`. 껐다 켠 뒤 입력이 안 들어오는 경우가 있었음(에뮬레이터 재시작 필요할 수 있음). 듣기 준비(`onReady`) 전에 말하면 인식되지 않음 → 앱에 12초 시간 제한. TTS 소리가 마이크로 다시 들어가 오인식될 수 있음
- **Virtual Console**: 열린 세션이 없으면 상태 변경 503. 세션 활성화 시 드라이빙뷰 주행가능거리가 0km로 초기화되는 부작용
- **탭 스크립트 오조작**: `scripts/emu-ui.sh`가 버튼이 촘촘한 화면에서 다른 버튼을 누른 적 있음(이상 의심 3건이 잘못 기록됨). 상태 변경 탭 뒤에는 `curl .../trips/sample-trip-1`로 서버 상태 확인. 한글은 `adb input text` 불가
- **음성 겹침**: 시스템이 내비와 Loadmate 음성 순서를 조정해 주지 않는다(Loadmate TTS는 오디오 포커스 요청 기록 없음). 앱이 내비 음성 재생을 감지해 기다리고, 끼어들면 멈춘 뒤 다시 말한다(`NaviAwareSpeechQueue`). 재생 상태는 공개 getter가 없어 `AudioPlaybackConfiguration.toString()`의 `state:started`를 본다. 에뮬레이터 확인만, 실차 미확인
- **헤드업 알림**: 알림 카테고리를 지정해야 내비 위에 뜸. Android 13+ 알림 권한 필요(앱 첫 실행 시 요청)
- **TTS**: `speak(text, isFinal=true)` 로 호출해야 끝남(false면 onDone 없음)
- **경유지**: 최대 2개(+목적지). 남은 방문지가 4곳 이상이면 앞의 3곳만 보내고 다음에 이어서
- **AGP 8.7.3 고정**(Studio 2024.2.1). 시스템 Python 3.9 → 서버 테스트는 Docker에서

## 9. 개발 환경 메모

- Docker 엔진: **Colima**(Homebrew). 재부팅 후 `colima start`. 자동 시작 `brew services start colima`. Compose 플러그인: `~/.docker/config.json` `cliPluginsExtraDirs` = `/opt/homebrew/lib/docker/cli-plugins`
- Android Studio는 SDK·JBR·에뮬레이터 때문에 삭제 금지. `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`
- 에뮬레이터 AVD `Pleos_Connect_v2.1`, AAOS 사용자 10
- Virtual Console: `adb forward tcp:8088 tcp:8088` → http://127.0.0.1:8088/ · CLI 세션 유지 `while true; do curl -s -X POST "http://127.0.0.1:8088/browser/session?client=cli"; sleep 1; done` · 트렁크 `curl -X PUT 'http://127.0.0.1:8088/vehicle/body?trunk=true'`
- SDK 바이너리 확인: `~/.gradle/caches/modules-2/files-2.1/ai.pleos.playground/` 의 aar → `javap` (공식 문서 페이지가 클라이언트 렌더링이라 내용이 안 읽힘)
- 형제 저장소 `../hyundai_app`(PleosLab): SDK 카탈로그·Vehicle API 실측. 공식 샘플 코드는 LICENSE 없음 → 복사 금지

---

## 10. 에뮬레이터 실측 기록

### 10-1. 1차 실측 (2026-11-08, Pleos Connect Emulator v2.1.0 · Navi Service 3.0.4 · CAAS 4.2.10 · user 10)

에뮬레이터 결과이며 실차 지원을 뜻하지 않는다. 증거 화면: `docs/evidence/2026-11-08/`.

**NaviHelper 3.0.5 `requestRoute(RouteInfo)`** (`NAVI_ROUTE_SEARCH`)
| 관찰 | 결과 |
|---|---|
| 서비스 연결 | `initialize(onConnected)` 즉시 성공 |
| 목적지 요청 (서울역 좌표) | 내비 앱(`ai.umos.maps.android.navigation.app`)이 **Loadmate와 같은 메인 패널을 차지하며 전면 표시**, 경로 탐색 후 안내 시작 (30분·27km). Loadmate는 ON_STOP |
| 성공 콜백 순서 | 요청 후 약 6.5초: `onRouteStarted(name, distance=27000m, duration=1800s)` → **곧바로 `onRouteEnded`** → `onNavigationStatus(ROUTE_DRIVE)`. `onRouteEnded` 는 도착 신호로 쓸 수 없음 |
| 기존 경로가 있을 때 새 요청 | 먼저 `onRouteCancelled` + `FREE_DRIVE` → 이후 새 경로 `onRouteStarted` |
| 탐색 실패 (바다 좌표 36.0,124.0) | `onRouteCancelled`(기존 경로 취소) → `onError(CAUSE_SEARCH_ROUTE)`. **실패해도 기존 안내가 사라짐** |
| 현재 위치와 같은 좌표 | 오류 없이 `onRouteStarted(distance=0, duration=60)` (TOO_SHORT 오류 안 남) |
| `cancelRoute()` | `onRouteCancelled` + `FREE_DRIVE` |
| 화면 단위 리스너 | ON_STOP 에서 release 하면 **요청 0.3초 뒤 해제되어 `onRouteStarted` 를 받지 못함** (시스템 앱들은 수신). 리스너를 유지한 임시 빌드로 위 콜백을 확인 |
| `onDestinationArrived` | 에뮬레이터에서 주행이 없어 미확인 |

**SpeechToText 3.3.1 ON_DEVICE** (`STT_SERVICE`, 서비스 `ai.pleos.playground.caas` 4.2.10 ≥ SDK 요구 4.1.29)
| 관찰 | 결과 |
|---|---|
| `initialize` (registerApp 없이) | `onSuccess`. `setLocale(ko_KR)` 동작 |
| `request()` | `onReady` 시점에 마이크 열림(상태 표시줄 마이크 아이콘). 호스트 마이크가 꺼져 있으면 `onReady` 뒤 아무 콜백 없음 |
| 호스트 마이크 | `adb emu avd hostmicon` 으로 켬 (끄기 `hostmicoff`). Extended Controls › Microphone 과 같은 설정 |
| 인식 | macOS `say` 음성 "에이 하차 완료" → 부분 결과 "에"→"에이 하"→"내일 하체 완" → `onEndedRecognition` → 최종 `onUpdated("내일 하체 완료", true)`. **최종 결과가 `onEndedRecognition` 뒤에 옴** |
| 오인식 | "에이"→"내일", "하차"→"하체". 말하지 않았는데 주변 소음을 "나도 엄마"로 확정한 경우도 있음 → 음성 명령은 **인식 문장 확인 후 실행** 필요 |
| 1회 요청 | 발화 1개 끝나면 자동 종료 (EPD) |

**Vehicle 2.3.2 Door Position REAR** (`CAR_DOORS`)
| 관찰 | 결과 |
|---|---|
| 범위 | `checkDoorPositionCapabilities` REAR **0..100** (planning-review 이전 기록 0..1 은 오기) |
| 구독 | `registerDoorPosition(REAR)` 직후 초기 알림 0 |
| Virtual Console Trunk Open/Close (`PUT /vehicle/body?trunk=true/false`) | 알림 100 / 0. 콘솔은 열림/닫힘 2값만 제공 → 중간값은 확인 불가 |
| 화면 이탈 중 Trunk Open | 알림 없음 (해제 확인). 복귀 시 재구독 초기 알림 100 |
| 부작용 | Virtual Console 세션 활성화 시 드라이빙뷰 주행가능거리 500→0km 로 초기화됨 (PleosLab 관찰과 동일) |

### 10-2. 추가 실측 (2026-11-08 오후, 임시 debug 리시버로 시험 · 코드는 커밋하지 않음)

| 항목 | 결과 | 시나리오 영향 |
|---|---|---|
| 경유지 한 번에 전달 `requestRouteWithWaypoints` (`NAVI_ROUTE_SEARCH`) | ✅ 경유지 A·B + 목적지 C 경로 생성. 내비 진행 막대에 경유지 2개 표시, 화면은 다음 방문지 A. `onRouteStarted.name` = 첫 경유지 이름. **경유지는 FIRST·SECOND 2개까지** | A·B·C 3곳은 한 번 요청으로 가능. 4곳 이상은 나눠서 요청 |
| `getWaypointInfo` | `NAVI_ROUTE` 권한 필요 (SecurityException) | 쓰려면 권한 추가 |
| 알림이 내비 위에 뜨는지 | 카테고리 미지정 → 헤드업 안 뜸 (`CarHeadsUpNotificationManager: category not set`). `CATEGORY_MESSAGE`·`CATEGORY_NAVIGATION` → **내비 위 헤드업 표시** | 주행 중 짧은 안내는 헤드업 알림 사용 가능. 카테고리 필수 |
| TextToSpeech 3.3.1 ON_DEVICE (`TTS_SERVICE`) | ✅ 자격 증명 없이 initialize·한국어 재생(화자 seoyoon). `speak(text, isFinal)` 두 번째 인자가 false 면 문장 미완료로 처리되어 `onDone` 안 옴 → **true 로 호출** | 내비 화면 위에서도 음성 안내 가능 |
| 앱이 백그라운드(내비 전면)일 때 STT | ✅ 인식 동작. "다음 장소에 뭐 내려야 해" → "다음 장소에 뭘 내려야 해?" | 이동 중 조회(14장 4단계) 가능. 단, 듣기 시작 트리거(버튼·알림 액션)는 별도 설계 필요 |
| 앱이 백그라운드일 때 내비 콜백 | ✅ 리스너를 유지하면 수신 | 결정 1 근거 |
| 도착 신호 `onWayPointArrived` / `onDestinationArrived` | ❌ 확인 불가. `adb emu geo fix` 위치는 내비가 NMEA 체크섬 오류로 버림. Virtual Console 가상 주행(D·60km/h)은 차량 속도만 바뀌고 내비 위치는 그대로 | 도착은 **기사 확인 버튼**을 기본 유지 (기존 방침과 같음) |

---

## 10-3. 운행 구조 재점검 (2026-11-08)

- [x] 운행 선택으로 돌아가거나 다른 운행을 열 때 이전 Coordinator scope 취소, 뒷문 구독 해제, 운행별 ViewModelStore 정리. AppContainer는 활성 Coordinator 하나만 보관. 내비 리스너는 기존 운행 범위 정책에 따라 TripSession이 유지한다.
- [x] 공통 오류 문구를 `core/network`, 공통 입력 칸과 시각 표시를 `core/designsystem`으로 이동. `TripCoordinator`, `TripScreen`, `TripParts`, `TripRules`, `TripComponents`의 폴더와 패키지를 `trip/presentation/shared`로 일치시킴.
- [x] 화면별 작업 처리 분리: 관측·기준 확정은 `loadcheck`, 경로·현장 진입은 `driving`, 하차 완료·회수는 `sitework`, 방문지 관리·이상 기록은 `overview`의 Operations 파일로 이동. 다섯 ViewModel이 해당 화면 액션을 직접 처리하고 화면별 UiState/UiAction은 전용 필드·타입으로 변경. 공유 Coordinator는 운행 데이터 동기화와 공통 요청 처리에 사용한다. `TripCoordinator.kt` 433→232줄.
- [x] 검증: `cd android && ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` 통과(화면별 ViewModel을 통한 요청 ID 재사용 테스트 포함). 서버 `/health`의 DB 정상, 에뮬레이터에서 APK 설치 후 현재 B 운행 열기 → 현장 작업 → 주행 복귀 → 운행 선택 → 같은 운행 재진입, 예외 로그 없음. 최종 APK 재설치·실행 후 운행 목록 표시도 확인. `GET /trips/sample-trip-1`에서 B pending·잔량 7 유지 확인. 기준 확정·하차 완료 등 서버 상태 변경 시나리오는 이번 검증에서 실행하지 않음.
- [x] 후속 확인: 현재 작업 트리 기준 `assembleDebug`·`testDebugUnitTest`(JUnit XML 31개)·`lintDebug` 통과, `git diff --check` 이상 없음. Pleos 에뮬레이터 user 10에 APK 재설치 후 앱 실행 → `오늘의 운행` 목록에 `샘플 데이터` 표시 → 운행 열기 → A 완료·B 현재·C 예정, 서버 계산 잔량 박스 7 복원 확인. Docker backend·db 실행 중 확인. 샘플 운행 초기화와 상태 변경을 동반하는 전체 시나리오는 아직 재검증하지 않음.
- [x] S10 F65 확장: TTS의 실제 초기화 결과(`SpeechOutput.available`)와 Android 알림 권한을 표시. `assembleDebug`·`testDebugUnitTest`(31개)·`lintDebug` 통과. 변경 APK를 Pleos 에뮬레이터 user 10에 재설치해 S10에서 서버·DB 정상, 음성 출력 `아직 사용 전`, 헤드업 알림 `권한 허용` 표시 확인. TTS를 새로 재생하는 실측은 하지 않았으며 기존 에뮬레이터 실측 결과를 유지.
- [x] 다음 검증 준비: `app.seed --reset`의 삭제 범위를 코드로 확인. 현재 샘플 운행 관련 관측·이력·회수·인계 및 일부 장소/차량/장비 메모가 삭제되므로 초기화 승인 요청. 현재 데이터는 변경하지 않고 Docker 격리 실행 `pytest -q` 61개 통과(Starlette deprecation 경고 1개). 전체 에뮬레이터 시나리오는 초기화 결정 후 진행.
- [x] 사용자 승인 후 샘플 초기화 및 연속 재검증: `app.seed --reset` → 에뮬레이터 user 10에서 샘플 출처·planned 확인 → 샘플 관측 10·기준 확정 → A 관측 8로 완료 요청 시 화면 `예상 7 / 관측 8`, 서버 A pending·잔량 10(미반영) 확인 → 관측 7 재기록·A 완료 → B 관측 4·완료 → C 관측 0·완료. PostgreSQL에서 status completed, A/B/C delivered 3/3/4·resolution matched, 잔량 0 확인. `docker compose restart backend` 후 동일 상태, 앱 강제 종료·재실행 후 목록 `완료`와 상세 잔량 0 복원. 내비 요청은 이번 실행에서 생략.
- [x] 내비 경로 회귀 확인: 샘플 운행을 다시 초기화하고 관측·기준 10을 API로 기록한 후 앱에서 `A → B → C` 확인창 → Pleos 내비에 경유지 2개 포함 전달 → 내비 전면에서 안내 시작 → 앱 복귀 후 `안내 시작됨 · A · 판교역 납품처 (샘플) · 3.1km · 11분` 표시. 내비가 전면에 있던 동안 운행 범위 리스너가 결과를 수신함. **현재 샘플 운행은 두 번째 초기화 후 기준만 확정한 `in_progress` 상태이며 A/B/C는 미완료.**

## 11. 검증 기록

| 날짜 | 항목 | 결과 | 근거 |
|---|---|---|---|
| 2026-11-10 | 출발 전 방문지 추가·수량 변경이 박스를 만들지 않음 + 샘플 초기화(사용자 승인) | assembleDebug·testDebugUnitTest(120개)·lintDebug, pytest 142개 통과. 에뮬레이터 user 10 임시 운행(확인 후 삭제): 출발 전 D(2개) 추가 시 박스 3 유지, 경고(경로 5 · 실린 짐 3), 출발 팝업 경고, 맞아요 → 기준 3 확정, 출발 후 경고 유지. **미확인:** 경고 음성 청취, 실제 카메라, 실차 | `docs/evidence/2026-11-09/10`, `GET /trips/verify-unload` |
| 2026-11-10 | 분할 화면 조사와 문서 정리(코드 변경 없음) | 빌드·테스트는 실행하지 않음. 에뮬레이터 창 구성은 읽기만 함: 주행 정보 853dp, 앱 영역 1682×1210dp. 실제 분할은 재현하지 못함 | `dumpsys activity activities`, 공식 Window Policy·Display Layout 문서 |
| 2026-11-09 | 하차 자동 확인 · 다음 경로 자동 전달 · 방문지 추가가 짐을 늘리지 않음 | assembleDebug·testDebugUnitTest(119개)·lintDebug, pytest 141개 통과. 에뮬레이터 user 10, 임시 운행 4곳(확인 후 삭제): 확정 후 D 추가 시 잔량 9·단위 0 유지, 경고 표시(경로 10 · 실린 짐 9), 시작 팝업 경고 → A→B→C 전달 → 앱 보며 트렁크 여닫기 → 관측 없음 팝업 → 맞아요 → A 완료 → B→C→D 자동 전달 / 관측 4(예상 3) → 수량 다름 팝업 / 관측 3 → 팝업 없이 B \`matched\` 완료, 재전달 없음 / 수량 줄이기로 경고 해제. **미확인:** 음성 재생 청취, 앱이 뒤에 있을 때 알림, 내비 도착 신호, 실제 카메라, 실차 | \`docs/evidence/2026-11-09/08·09\`, \`LoadmateNavi\` 로그, \`GET /trips/verify-unload\` |
| 2026-11-09 | 뒷문 도착 처리 · 내비 3곳 제한 안내 · 문구 정리 | assembleDebug·testDebugUnitTest(118개)·lintDebug, pytest 141개 통과. 에뮬레이터 user 10 샘플 운행(변경 없음, B·A·C·D pending·잔량 11 유지): `안내 시작` 버튼·성공타워 안내 표시, 시작 팝업 B → A → C와 성공타워 안내, 도착 처리 없이 앱을 보며 트렁크 열기·닫기 → 팝업 → 아직이에요 → `하차 완료` 표시. 성공타워 주소 좌표 조회 정상, 원인은 3곳 제한(`route-points` 로그에 앞 3곳만 요청). **미확인:** 4곳째가 실제 내비에 들어가는 재전송, 바뀐 음성 문구 재생, 실차 | `docs/evidence/2026-11-09/07`, 서버 로그, UI 덤프 |
| 2026-11-09 | 현장 작업 화면 제거 + 하차 확인 팝업 | assembleDebug·testDebugUnitTest(116개)·lintDebug 통과. 에뮬레이터 user 10, 임시 운행(확인 후 삭제, 샘플 운행 변화 없음): 도착 전 버튼 없음 → 음성 "도착했어" → 적재 화면·장소 메모 → 트렁크 열기·닫기 → 팝업 → A `driver_confirmed` 완료 / B에 관측 1 입력 → 수량 다름 팝업 → 다시 확인 → 운행 탭 `하차 완료` → 그대로 넘어가기 → 운행 `completed`·잔량 0. **미확인:** 내비 도착 신호, 앱이 뒤에 있을 때 알림으로 여는 경로(단위 테스트만), 회수 버튼 화면 조작, 실제 카메라, 실차 | 스크린샷 `docs/evidence/2026-11-09/05·06`, `GET /trips/verify-unload` |
| 2026-11-09 | 메모의 해결 기능 제거(사용자 결정: 끝나면 삭제), `이 차` → `차량`. API `resolved`·`resolved_at` 제거, 음성 `resolve_memo` → `delete_memo`(기사 확인 후 삭제), 마이그레이션 `f2c6a8d35e19`(해결 처리된 행 0건 확인 후 컬럼 삭제) | backend pytest 99개, Android `assembleDebug`·`testDebugUnitTest`(84개, 실패 0)·`lintDebug` 통과. PostgreSQL 마이그레이션 적용·샘플 메모 유지 확인. 에뮬레이터 user 10: 운행 시작 카드 `차량 확인 2건`, 메모 드롭다운 `차량`, 해결 버튼 없음. **미확인:** 실제 음성·AI로 삭제(가짜 응답 테스트만) | pytest, Gradle 출력, psql, 에뮬레이터 UI 덤프 |
| 2026-11-09 | 운행 시작 카드 표시 변경: 차량 문제 메모만 최대 2건, 내용·위치를 나눠 한 줄로, 넘치면 `외 N건`. 메모 데이터·API 변경 없음 | `assembleDebug`·`testDebugUnitTest`(84개)·`lintDebug` 통과. 에뮬레이터 user 10에서 `⚠ 고정 끈 1개 끊어짐` / `조수석 뒤 수납함` 한 줄 표시, 기타 메모 미표시 확인. `외 N건`은 샘플에 3건 이상이 없어 미확인 | — |
| 2026-11-09 | 운행 탭 `적재 확인 시작`과 S05 화면 제거, 출발 팝업으로 교체. 수량 입력 없이 `내비 안내 보내기` → "지금 실린 짐이 맞나요?" → `맞아요, 출발` = 최초 기준 확정 후 내비 확인창. 관측이 없으면 계획 수량임을 팝업에 표시 | `assembleDebug`·`testDebugUnitTest`(84개)·`lintDebug` 통과. 에뮬레이터 user 10에서 팝업 표시·`아직이에요`로 닫힘, 서버 `planned` 유지 확인. `맞아요, 출발` 실행은 샘플 운행 상태를 바꾸므로 단위 테스트로만 확인 | — |
| 2026-11-09 | 상단 `새로고침` 버튼 제거 (운행 선택·운행·적재). 운행 선택은 화면 진입·복귀 때 목록 자동 재조회, 실패 때만 `다시 시도` 표시. 운행·적재의 실패 재시도 경로는 유지 | assembleDebug·testDebugUnitTest(실패 0)·lintDebug 통과. 에뮬레이터 user 10: 운행 선택·운행 화면에 버튼 없음, 운행에서 목록 복귀 시 `GET /trips` 1회 추가 확인, 샘플 운행 데이터 변화 없음, 앱 비정상 종료 없음. **미확인:** 목록 실패 시 `다시 시도` 표시(공용 서버를 끄지 않음), 적재 화면 표시(`TripFrame` 공용이라 같은 코드. 메뉴 탭이 다른 메뉴로 열린 현상 있었고 원인 미확인) | Gradle 출력, 에뮬레이터 UI 덤프, backend 로그 |
| 2026-11-09 | 메모 대상 드롭다운에 `전체` 추가·기본값 변경. 서버는 대상 없이 조회하면 다른 차량·운행 메모도 주므로 앱이 이 차·이번 운행 방문지·이번 운행 메모만 모아 최신순 표시(하나라도 실패하면 오류 표시), 해결한 메모 제외, 카드에 대상 표시, 추가 시 저장할 곳 선택 | assembleDebug·testDebugUnitTest(82개, 실패 0)·lintDebug 통과. 에뮬레이터 user 10: 전체 목록·대상 표시, 작성 팝업에서 `이번 운행` 선택 후 저장(서버에 `trip` 메모로 저장 확인, 테스트 메모 삭제), `내역` 목록과 드롭다운 5개, `방문지` 필터 확인. 아래 행의 미확인 항목(합친 화면 저장, 내역 표시)도 이번에 확인 | Gradle 출력, 에뮬레이터 스크린샷·UI 덤프, API 조회 |
| 2026-11-09 | 왼쪽 메뉴를 `메모`·`내역`으로 재구성. `기록·메모`와 `차량 노트`를 `메모` 하나로 합치고(대상 드롭다운: 이 차·장소·이번 운행, `이 차`만 종류 드롭다운·해결), `내역`은 읽기 전용 + 기능별 드롭다운 5개(전체·방문지·적재·회수·이상·운행). 메모 변경은 내역에 넣지 않음(사용자 결정). 서버·API 변경 없음 | assembleDebug·testDebugUnitTest(78개, 실패 0)·lintDebug 통과. 에뮬레이터 user 10: 메모 화면·대상 드롭다운 펼침·`장소 C` 선택·작성 팝업과 저장될 곳 안내 확인. 앱 비정상 종료 없음. **미확인:** 합친 화면에서 실제 저장, `내역` 화면과 드롭다운 표시(검증 중 다른 쪽 에뮬레이터 조작과 겹쳐 중단, 저장·필터 규칙은 단위 테스트로만 확인). 이전 행의 원인 미확인 현상도 같은 조작 겹침으로 추정 | Gradle 출력, 에뮬레이터 스크린샷·UI 덤프·logcat |
| 2026-11-09 | 기록·메모 화면을 좌우 칸에서 `메모`·`변경 내역` 탭으로 분리, 변경 내역을 항목별 카드(내용 + 시각·입력 방식·기사)로 변경 | assembleDebug·testDebugUnitTest(70개, 실패 0)·lintDebug 통과. 에뮬레이터 user 10에서 두 탭 표시와 변경 내역 3건 확인. 탭 확인 중 차량 노트로 넘어가거나 메모 팝업이 열린 현상이 있었으나 원인 미확인, 재시도에서는 재현되지 않음 | Gradle 출력, 에뮬레이터 스크린샷 |
| 2026-11-09 | 차량 노트·기록/메모 작성을 옆 칸에서 `추가` 버튼 + 팝업으로 변경, 수정·삭제 아이콘화(연필은 공식 Figma에 없어 `project_ic_edit`), 문구 정리, 키보드가 뜨면 화면이 줄도록 `adjustResize` | assembleDebug·testDebugUnitTest(68개, 실패 0)·lintDebug 통과. 에뮬레이터 user 10: 노트 팝업 열기·입력·닫아도 내용 유지·저장, 삭제 아이콘·확인, 연필 아이콘으로 수정 팝업 열기, 기록·메모 팝업 열기, 키보드가 뜨면 팝업이 위로 올라오지만 저장·취소 버튼 아래쪽 절반이 잘림(팝업 안 스크롤 가능, 키보드가 뜬 상태의 저장 누름은 미확인). 검증 중 샘플 노트 `seed-note-3`을 잘못 삭제해 같은 키·내용으로 복원(id만 51로 변경), 테스트 노트 삭제. **미확인:** 기록·메모 팝업의 실제 저장, 실차 키보드 크기 | Gradle 출력, 에뮬레이터 스크린샷·UI 덤프 |
| 2026-11-09 | 차량 노트 필터를 줄 세운 탭에서 `PleosDropdown`으로 교체 (항목 전체·도구·차량 문제·기타·해결됨 유지) | assembleDebug·testDebugUnitTest·lintDebug 통과. 에뮬레이터 user 10에서 드롭다운 펼침, 5개 항목 표시, `차량 문제` 선택 시 해당 노트만 표시 확인. `해결됨` 선택 화면은 미확인 | Gradle 출력, 에뮬레이터 스크린샷·UI 덤프 |
| 2026-11-09 | 음성 전체 AI 전환 (한 번의 호출, `ignore`, 인용 근거·숫자 검증, 문장 단위 읽기, AI 대기 5초). `api-contract` 먼저 갱신 | backend pytest 97개, Android assembleDebug·testDebugUnitTest(63개, 실패 0)·lintDebug 통과. 서버 재빌드 후 PostgreSQL에서 키 없는 상태의 잔량·내비·물건 위치 질문이 규칙 기반·`not_configured`로 응답, 운행 상태 변화 없음. 에뮬레이터 user 10에서 새 APK 설치·운행 목록 표시, 충돌 기록 없음. **미검증:** 실제 Gemini 호출·판단 품질·응답 속도(키 없음, AI 경로는 가짜 어댑터와 SDK 가짜 전송으로만 확인), 에뮬레이터 실제 발화에서 `ignore`·문장 단위 읽기(단위 테스트로만 확인) | pytest, Gradle 출력, curl, UI 덤프 |
| 2026-11-09 | 박스별 적재 자리 + 3D·2D 적재 화면 (F12·F13). API `POST /trips/{id}/placements`, `<Trip>.units[]`, 마이그레이션 `d7e2b4a91c30`. 카메라가 없어 시연 도구가 자리 소식을 `sample` 로 보냄. 자리 기록은 잔량·완료 판정에 쓰지 않음 | backend pytest 91개 통과, PostgreSQL 마이그레이션 적용, `alembic check` 차이 없음, `app.seed --reset` 실행(사용자 승인). Android assembleDebug·testDebugUnitTest(57개)·lintDebug 통과. 에뮬레이터 user 10: 상차 시뮬레이션 10개, 이상 포함 시 계획과 다른 자리 경고와 계획 자리로 옮김, 2D 표시, 3D 끌어서 회전, 기준 확정, 주행 중 짐 밀림(이상 의심 기록·빨간 구역), 현장 화면 박스별 위치, 관측 불일치 시 미반영, 기사 확인 완료 후 A 3개 제거·예상 7, backend 재시작 후 복원. **미검증:** 실제 카메라·영상 인식(없음), 다크 모드, 실차. 확인 중 다른 작업의 앱 재설치로 앱이 한 번 종료됨(앱 크래시 아님). 샘플 운행은 A 완료 상태로 남김 | 스크린샷, `GET /trips/sample-trip-1`, logcat |
| 2026-11-09 | AI 어댑터를 Google Gemini로 교체, README·`.env.example`, `AI 키 없음` 표시, 규칙 기반의 차량 주의 사항 조회 | backend pytest 91개, Android `assembleDebug`·`testDebugUnitTest`(57개)·`lintDebug` 통과. Gemini 요청 형식, 한도 초과·서버 오류·시간 초과·형식 오류 시 규칙 기반 전환은 SDK 가짜 전송으로 확인. 재시도 끔. **실제 Gemini 호출·응답 속도는 키가 없어 미검증** | pytest, Gradle 출력 |
| 2026-11-09 | 장비·인계 → 차량 노트 + 메모 AI | backend pytest 74개, Android `assembleDebug`·`testDebugUnitTest`(45개)·`lintDebug` 통과. PostgreSQL 마이그레이션 `a1f3c9d27b10` 적용, 같은 키 재전송 `replayed`, 서버 재시작 후 노트 복원. 에뮬레이터 user 10에서 차량 노트 목록·추가·해결·해결 취소, 운행 시작 카드(`이 차 확인 2건`), 현장 카드(장소 메모·도구 위치) 확인(임시 운행으로 확인 후 삭제, 샘플 운행 상태 유지). **미검증:** 실제 AI API 호출(키 없음, 이후 Gemini로 교체. 요청 형식·응답 처리는 SDK 가짜 전송으로만 확인), AI 응답 속도, 실제 음성 입력으로 메모 저장 | pytest, Gradle 출력, 에뮬레이터 UI 덤프 |
| 2026-11-09 | 운행 화면 한 칸 통합: 내비 안내 + 방문지 목록(현재 방문지 줄에 `현장 작업 시작`, `방문지 편집`에서 순서·취소·기준 전 수량, 팝업으로 추가), 상단 예상 잔량, 왼쪽 메뉴 `적재` 신설(배치·회수품·이상 의심·시연 도구), 다음 방문지 카드·앱 내부 지도·도착 확인 카드 제거. 현장 작업·적재 확인은 화면 전체 사용. `적재` 아이콘은 Figma에 없어 PROJECT | assembleDebug·testDebugUnitTest(41개, 실패 0)·lintDebug 통과. 에뮬레이터 user 10에서 진행 중 운행 목록·편집 표시·적재 메뉴·현장 작업 진입 확인, 서버 데이터 변화 없음. 첫 빌드에서 방문지 추가 팝업이 스크롤 영역 안에 잘려 화면 최상단으로 이동(취소 팝업 포함). 수정본은 운행 완료 화면만 확인, 팝업 표시·출발 전 화면·현장 작업 복귀는 샘플 운행이 `completed`라 미확인 | 스크린샷, `GET /trips/sample-trip-1` |
| 2026-11-09 | 디자인 변경 후 전체 시연 재검증 + 음성 끄기 표시 수정 + 서버 끊김 문구 수정 | `app.seed --reset` 후 에뮬레이터 user 10: 기사 선택 → 샘플 관측 10·기준 확정 → A→B→C 내비 전달·앱 복귀 결과 수신 → A 예상 7/관측 8 미저장 → 회수 기록 후 A 완료 → B→C 재전달 → B 예상 5/관측 8 미저장 → 재관측 5로 완료 → C 완료 → 회수품 반납 → `completed`·잔량 0. backend 재시작·앱 재실행 후 복원. 음성 ON/OFF: SDK `stop` 뒤 마이크는 닫히나(`rec stop`) `onEndedRecognition`이 오지 않아 `듣는 중…`이 남던 문제를 수정, 재확인. 서버 정지 시 `연결 안 됨`·`다시 확인` 표시, 재시작 후 자동 복구. 다크 모드 연결 상태·운행·목록 표시 확인. assembleDebug·testDebugUnitTest(41개)·lintDebug 통과. 시연 빌드에는 진행 중이던 미커밋 적재 탭 작업이 함께 포함됨. 실제 발화 인식은 이번에 미실측 | 스크린샷, UI 덤프, `GET /trips/sample-trip-1`, logcat, `dumpsys audio` |
| 2026-11-09 | 왼쪽 메뉴·운행 목록·연결 상태 변경과 `shared/designsystem` 이행을 합친 커밋 `915a69a` 재검증 (아래 `점검 도구` 제거 행의 작업 폴더 빌드 실패는 해소) | assembleDebug·testDebugUnitTest(40개)·lintDebug 통과. 에뮬레이터 user 10에서 카드 탭으로 운행 열기, 메뉴 전환, 연결 상태 표시, `운행 목록`으로 복귀 확인, 앱 비정상 종료 없음. 음성 스위치 조작·서버 연결 실패 화면·다크 모드는 미확인. 샘플 운행 데이터 변경 없음 | 스크린샷, UI 덤프 |
| 2026-11-09 | 공식 Figma 컴포넌트 전체 추가(미사용 포함)·아이콘 116개, 메모·장비 안내 토스트 전환, 음성 패널을 시스템 팝업으로 전환 | assembleDebug·testDebugUnitTest(40개, 실패 0)·lintDebug 통과. 임시 화면으로 추가 컴포넌트·토스트·음성 패널 표시 확인 후 임시 코드 제거. 드래그·휠 조작, 드롭다운 펼침은 미확인 | 에뮬레이터 스크린샷 |
| 2026-11-09 | 디자인 시스템을 공식 Figma 우선으로 재정렬하고 `shared/designsystem`으로 이동 (세그먼트·스위치·아이콘 버튼·시스템 팝업·토스트·제목·목록 항목, Figma 아이콘 9개). 왼쪽 세로 메뉴·카드·꼬리표·오프라인 안내 바는 Figma에 없어 PROJECT | assembleDebug·testDebugUnitTest(40개, 실패 0)·lintDebug 통과. 에뮬레이터 2560×1440 user 10에서 운행 선택·운행·기록·장비 인계·현장 작업, 경로·회수 팝업, 다크 모드 표시 확인. 토스트와 음성 패널은 화면 미확인 | 스크린샷 확인 |
| 2026-11-09 | 내비 음성과 Loadmate 음성 겹침 방지 | 변경 전 재생 기록: 안내 시작 3회 모두 겹침(예: Loadmate 42.675~46.282초, 내비 42.833~45.178초). 내비는 USAGE_ASSISTANCE_NAVIGATION_GUIDANCE, Loadmate TTS는 USAGE_MEDIA로 구분됨. `안내 시작` 음성 삭제(실패 음성은 유지), 음성은 `NaviAwareSpeechQueue`로 한 문장씩 재생. 에뮬레이터 user 10, 내비 전면: 내비 시작 2ms 뒤 감지, 내비 종료(13.262초) 후 Loadmate 시작(13.620초). 말하는 중 내비가 끼어든 임시 재현: 정지 후 내비 종료(19.652초) 뒤 처음부터 재생(19.968초), 정지 요청이 실제 소리 정지까지 약 0.45초 걸려 내비 음성과 약 0.34초 겹침. 최종 빌드는 경로 전달 시 내비 음성만 재생. assembleDebug·testDebugUnitTest(40개)·lintDebug 통과. 실차·도착 시점 내비 문구 미확인 | `dumpsys audio`, logcat `LoadmateNaviVoice`·`LoadmateTts` |
| 2026-11-09 | 연결 상태 `점검 도구` 제거 (샘플 목적지 전달·경로 취소·듣기 시험 버튼, 수신 기록, 뒷문 원시값). 화면은 서버·차량 기능 상태 목록만 남김. 위 "연결 상태 화면 정리"의 점검 도구 항목을 대체 | 다른 작업의 미완성 `shared/designsystem` 때문에 작업 폴더 빌드는 실패(`PleosPopup.kt` `PleosButtonSize.Large` 없음). 해당 폴더만 뺀 임시 복사본에서 assembleDebug·testDebugUnitTest(31개)·lintDebug 통과, 그 APK를 에뮬레이터 user 10에 설치해 화면 확인 | 스크린샷 확인 |
| 2026-11-09 | 장비·인계 화면 용어·순서 정리 | `지금 조작하는 기사`(시연용, 인증 아님)와 서버의 `운행 담당 기사`를 분리 설명. 차량 비치 공용 도구는 배송 물품과 별개임을 명시하고 상태 버튼을 두 줄로 배치. 인계 요청→받는 기사 인수→담당 변경 순서와 대상 기사 조건을 화면·ViewModel에 반영. 기사 없이 시작한 샘플 운행에서도 서버 계약대로 첫 인계 요청 허용. `assembleDebug`·`testDebugUnitTest`·`lintDebug` 통과, 에뮬레이터 user 10에서 문구·버튼 배치 확인. 인계 생성·인수 서버 상태 변경은 이번에 실행하지 않음. | `FleetScreen.kt`, `FleetViewModel.kt`, 에뮬레이터 스크린샷 `/private/tmp/fleet-final.png` |
| 2026-11-09 | 연결 상태 서버 줄 단순화: 정상이면 `연결됨`만 표시(주소·버전 제거), 화면 진입·복귀 때 자동 재확인, `다시 확인`은 연결 실패 때만 표시 | assembleDebug·testDebugUnitTest(31개)·lintDebug 통과. 에뮬레이터 user 10에서 정상 표시 확인. 실패 표시와 `다시 확인` 버튼은 서버를 끄지 않아 화면 미확인 | 스크린샷 확인 |
| 2026-11-09 | 연결 상태(S10) 화면 정리: 왼쪽에 서버·차량 기능 상태 목록, 시험 버튼(목적지 전달·경로 취소·듣기)과 수신 기록·뒷문 원시값은 오른쪽 `점검 도구`에 접어 둠 | assembleDebug·testDebugUnitTest(31개)·lintDebug 통과. 에뮬레이터 user 10에서 상태 목록 표시와 점검 도구 펼침 확인. 점검 도구 안의 시험 버튼은 내비 전환을 피하려고 이번에 누르지 않음(동작 코드는 변경 없음). 다크 모드 미확인 | 스크린샷 확인 |
| 2026-11-09 | 운행 화면 이동 구조 변경: 상단 탭·"운행 선택" 버튼을 왼쪽 세로 메뉴(맨 위 뒤로가기 `운행 목록`, 맨 아래 음성 ON/OFF)로 이동, 목록은 카드 전체를 누르면 열림. 세로 메뉴와 아이콘은 공식 Figma 규격이 없어 PROJECT | assembleDebug·testDebugUnitTest(31개)·lintDebug 통과. 에뮬레이터 user 10에서 카드 탭으로 운행 열기, 메뉴 4개 전환, 음성 ON→OFF, 뒤로가기로 목록 복귀 확인. 샘플 운행 데이터 변경 없음. 다크 모드 미확인 | 스크린샷 확인 |
| 2026-11-09 | 공식 Figma 2.1.5 값으로 디자인 시스템 정렬 (색 3개 수정·divider 추가, 줄 높이, 버튼 Small/Medium·눌림·비활성, 입력창, 스테퍼, 시스템 팝업, 텍스트 버튼) | assembleDebug·testDebugUnitTest(31개)·lintDebug 통과. 에뮬레이터 2560×1440 user 10에서 운행·장비 인계·연결 상태·현장 작업 화면과 경로·회수 팝업 표시 확인. 음성 패널 표시와 다크 모드는 화면 미확인 | 스크린샷 확인, 값 대조는 Figma 변수 추출 |
| 2026-11-08 | 앱 내부 온라인 도로 지도 1단계 | OSM 타일을 직접 그려 A/B/C 위치를 표시. WebView는 타일 로드에도 Pleos 에뮬레이터에서 빈 영역으로 합성되어 Compose Canvas로 변경. 빌드·단위 테스트·lint 통과, 에뮬레이터 user 10에서 실제 도로 지도와 카드 경계 표시 확인. 아직 도로 경로선·현재 위치·회전 안내 없음. | `RouteOverview.kt`, 에뮬레이터 스크린샷 `/private/tmp/loadmate-map-final.png`, [OSM 타일 정책](https://operations.osmfoundation.org/policies/tiles/) |
| 2026-11-08 | 앱 내부 내비 지원 여부 재확인 | **앱 내부 지도 표시 가능(별도 지도 SDK)**, **Pleos 내비 화면 직접 삽입 불가(공개 NaviHelper 3.0.5 API 기준)**. Pleos route request는 별도 내비 앱으로 전달. 실제 도로 경로선·회전 안내는 자체 경로/위치 연동과 차량 앱 허용 범위 확인이 선행되어야 함. 코드·API 변경 없음. | [Pleos NaviHelper 소개](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/intro), 로컬 NaviHelper 3.0.5 sources.jar, [MapLibre MapView](https://maplibre.org/maplibre-native/android/api/-map-libre%20-native%20-android/org.maplibre.android.maps/-map-view/index.html), [Android 내비 앱](https://developer.android.com/training/cars/apps/navigation) |
| 2026-11-08 | 미커밋 작업 정리 | 기존 변경을 보존하며 앱 구조·음성·2D 개요와 S10 상태 표시를 분리 커밋. 앱 `assembleDebug`·`testDebugUnitTest`·`lintDebug` 통과, 에뮬레이터 표시 확인. 실제 연속 발화 미실측 상태 유지. 문서·작업 지침도 별도 커밋으로 정리. | Git 커밋 이력, Gradle 출력, UI 덤프 |
| 2026-11-08 | 새 세션 음성·앱 내부 2D 지도 1단계 | 기존 미커밋 변경 보존. 방문지 좌표 직선 개요 구현. `assembleDebug`·`testDebugUnitTest`·`lintDebug`, `git diff --check` 통과. 에뮬레이터 user 10에 설치하여 운행 화면 `음성 ON · 끄기`/`음성 OFF · 켜기` 토글과 `방문지 2D 위치 개요`, A→B→C 및 직선 경고 표시 확인. **두 차례 실제 발화의 자동 재청취는 호스트 마이크 입력을 확보하지 못해 미실측**; 코드의 `scheduleNextListen` 경로 확인에 그침. | `RouteOverview.kt`, `DrivingScreen.kt`, UI 덤프, Gradle 출력 |
| 2026-11-08 | 내부 지도·3D 타당성 조사 | Pleos NaviHelper는 경로 시작 상태만 주고 지도 뷰·경로 선 좌표를 주지 않음. 독립 지도 엔진(MapLibre Native Android 등)과 별도 지도 데이터·경로 API가 필요. Android for Cars 내비 템플릿은 내비 카테고리/권한과 차량 앱 품질 요건이 있어 현재 Loadmate Activity에 무조건 적용 가능하다고 단정할 수 없음. 3D 지형은 MapLibre Style Spec에 개념이 있으나 Native Android 지원을 확인하지 못했으므로 보류. 왼쪽 3D 차량은 Pleos 시스템 화면이며 Loadmate 적재 화면 아님. | [Android 내비 앱 문서](https://developer.android.com/training/cars/apps/navigation), [차량 앱 품질](https://developer.android.com/docs/quality-guidelines/car-app-quality), [MapLibre Android](https://maplibre.org/maplibre-native/docs/book/platforms/android/), [Terrain 지원표](https://maplibre.org/maplibre-style-spec/terrain/) |
| 2026-11-08 | 음성 ON/OFF 조작 버튼 명확화 | 색 테두리와 `켜기/끄기` 동작 문구를 추가, Compose 접근성 역할 Button. `assembleDebug`·`testDebugUnitTest`·`lintDebug` 통과. 이 마지막 문구 변경 APK의 에뮬레이터 재설치는 미실시 | `MainActivity.kt`, Gradle 출력 |
| 2026-11-08 | 상단 음성 표시 간소화 | 기사 이름 문구 제거, 초록 ON/빨강 OFF 스위치. `assembleDebug`·`testDebugUnitTest`·`lintDebug`, `git diff --check` 통과. Pleos 에뮬레이터 user 10에 재설치 후 운행 화면에서 `음성 OFF` 문구와 기사 이름 미표시 확인. 색 자체의 육안 QA와 연속 발화 검증은 미실시 | `MainActivity.kt`, `PleosTheme.kt`, Gradle 출력, UI 덤프 |
| 2026-11-08 | 차량 패널·앱 내부 내비·상시 음성 재검토 | 왼쪽 3D 차량은 Pleos 시스템 차량 화면(안전벨트·차량 상태)이며 Loadmate 적재 시각화 아님. NaviHelper 3.0.5 소스에 앱 내 지도 View 없음. 음성 ON/OFF·상태 표시·자동 재청취 구현, assembleDebug·testDebugUnitTest·lintDebug 통과. 에뮬레이터 user 10 재설치 후 `음성 꺼짐/켜기` → `음성 듣는 중/끄기` 표시 확인 후 OFF. 실제 연속 발화 인식/내비 내부 지도는 미실측·미구현 | SDK sources.jar, `MainActivity.kt`, `VoiceViewModel.kt`, Gradle 출력, UI 덤프 |
| 2026-11-08 | 제출 준비 1차: 기존 미커밋 변경 보존, README 점검·release 빌드 가능성 확인 | `assembleDebug`·`testDebugUnitTest`·`lintDebug`·`assembleRelease` 성공, `git diff --check` 이상 없음. release 산출물은 38MB unsigned APK이며 서버 주소 미설정. 샘플 운행 데이터 변경 없음 | 작업 트리 상태·Gradle 출력·`android/app/build/outputs/apk/release/` |
| 2026-11-08 | A0 앱 구조 이행 1단계: `ApiResult`·`ApiError` 공통 네트워크 이동 | assembleDebug·testDebugUnitTest·lintDebug 통과 | `21b864e` |
| 2026-11-08 | A0 앱 DTO 이행 착수: kotlinx.serialization 1.6.3 도입, 연결 상태 응답 DTO·mapper 전환 | assembleDebug·testDebugUnitTest·lintDebug 통과 | `35ed274`; 나머지 파서 이행 전 |
| 2026-11-08 | A0 앱 응답·요청 DTO/mapper 이행, Repository StateFlow, 운행 범위 내비 리스너, 운행 화면별 ViewModel과 나머지 화면 폴더 이동 | 각 단계 assembleDebug·testDebugUnitTest·lintDebug 통과, 단위 테스트 26개 | `476bdd0`, `ca04f78`, `29d04cf`, `a76ae40`, `ecd81da`, `8c6333c`, `156fd58`, `dc2904f`, `6ea90a1` |
| 2026-11-08 | 변경 APK 에뮬레이터 설치·실행, 현재 샘플 운행 조회 | 실행 성공. 현재 A 완료·B 진행 중이므로 3절 처음부터 재현은 미실시 | `adb install -r`, user 10 `MainActivity`, UI 텍스트 덤프; `GET /trips/sample-trip-1` |
| 2026-11-08 | 최종 DTO 요청 전환 APK 에뮬레이터 재설치·실행 | 운행 탭 B 진행·서버 예상 잔량 7 표시. 전체 시연은 샘플 운행 초기화 승인 대기 | user 10 `MainActivity`, UI 텍스트 덤프, `GET /trips/sample-trip-1`; 자동 승인 검토에서 `app.seed --reset` 거절 |
| 2026-11-08 | 화면별 UiState·UiAction 반영 APK 재설치·실행 | B 진행·예상 잔량 7 표시, 앱 시작 정상 | `2f14344`, user 10 UI 텍스트 덤프 |
| 2026-11-08 | 화면별 상태·액션 파일 및 운행 공통 Composable 분리 | 각 변경 후 assembleDebug·testDebugUnitTest·lintDebug 통과. 최종 APK 에뮬레이터 재설치 후 B 진행·잔량 7 표시 | `016a527`, `09b474b`, `bad9f8f`; user 10 UI 텍스트 덤프 |
| 2026-11-08 | 동작 보존 점검: 관측·방문지 완료 뒤 중복 운행 조회 제거 | 화면의 기존 재조회 흐름 유지, assembleDebug·testDebugUnitTest·lintDebug 통과 | `CachingTripRepository`, 로컬 빌드 |
| 2026-11-08 | F02 운행 선택 첫 화면, 샘플 출처 표시, 선택 운행으로 탭 전환 | assembleDebug·testDebugUnitTest(29개)·lintDebug 통과. 에뮬레이터 user 10에서 목록·샘플 표시 → 운행 열기 → 운행 선택 복귀 확인. 전체 시연은 기존 샘플 운행 상태 보존으로 미실시 | `GET /trips` UI 텍스트 덤프, emulator-5554 |
| 2026-11-08 | Android assembleDebug / testDebugUnitTest / lintDebug | 통과 | 로컬 빌드 |
| 2026-11-08 | backend pytest | 2 passed | Python 3.9 venv |
| 2026-11-08 | docker compose + `/health` | `database: ok` | Colima, postgres:16, python:3.12-slim |
| 2026-11-08 | 에뮬레이터 debug APK 설치 → S10 화면 | "정상 · 서버 0.1.0 · DB 연결됨" 표시 | Pleos_Connect_v2.1 AVD, `http://10.0.2.2:8000`, 백엔드 로그 `GET /health 200` |
| 2026-11-08 | core/pleos + S10 Pleos 연동 섹션 빌드 · 단위 테스트 6개 · lint | 통과 (lint 버전 경고만) | `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` |
| 2026-11-08 | NaviHelper `requestRoute` 실측 | 경로 안내 시작, 실패 시 `CAUSE_SEARCH_ROUTE`, 화면 단위 리스너는 결과 수신 못 함 | logcat `LoadmateNavi`, evidence/03 |
| 2026-11-08 | SpeechToText ON_DEVICE 실측 | 자격 증명 없이 한국어 인식 동작, 오인식 있음 | logcat `LoadmateStt`, evidence/05 |
| 2026-11-08 | Door REAR 구독 실측 | Trunk Open/Close → 100/0 수신, 이탈 시 해제 | logcat `LoadmateRearDoor`, evidence/04 |
| 2026-11-08 | 경유지 전달 · 헤드업 알림 · TTS · 백그라운드 STT 실측 | 경유지 2개+목적지 동작, 알림은 카테고리 지정 시 내비 위 표시, TTS·백그라운드 STT 동작, 도착 신호는 에뮬레이터에서 확인 불가 | logcat `LoadmateExp`, evidence/06~08 |
| 2026-11-08 | 실험 코드 제거 후 assembleDebug · testDebugUnitTest | 통과 | 로컬 빌드 |
| 2026-11-08 | backend pytest (trip 규칙·API 포함) | 27 passed | Docker python:3.12 이미지에 backend 마운트 |
| 2026-11-08 | PostgreSQL 실제 실행: 마이그레이션·시드·대표 시나리오(A 예상 7/관측 8 → 확인 필요 → 관측 7 → 완료) | 동작 | curl, `trip_events` 2행(baseline +10, stop-a −3) |
| 2026-11-08 | 같은 완료 요청 5개 동시 전송 | 5개 모두 200, 이벤트 1건, 잔량 7 (중복 차감 없음) | curl 병렬, psql |
| 2026-11-08 | backend 재시작 후 복원 | 잔량 7·A 완료(matched) 유지 | `docker compose restart backend` |
| 2026-11-08 | Android 단위 테스트 (파서·경로 규칙·ViewModel 요청 ID 재사용) · lint | 19개 통과, lint 버전 경고만 | `./gradlew :app:testDebugUnitTest :app:lintDebug` |
| 2026-11-08 | **대표 시나리오 에뮬레이터 연속 실행** (14장 1~8단계 + B·C) | S01 → 샘플 관측 10 → 기준 확정 → 확인창 → A→B→C 경유지 전달, 내비가 화면을 덮은 동안 "안내 시작됨 · 3.1km · 11분" 수신 → A 도착(뒷문 열림 표시) → 샘플 관측 8 → 완료 요청 → S06 "예상 7 / 관측 8" (저장 안 됨) → 다시 관측 7 → A 완료(관측 일치) → B 전달 → B 기사 확인 완료 → C 완료 → 운행 완료, 내비 release | evidence/10~16, `trip_events` 4행(기준 +10, A −3 match, B −3 driver_confirmed·근거 mismatch, C −4 match) |
| 2026-11-08 | 앱 재설치(프로세스 재시작) 후 복원 | 기준 10·현재 A 상태 복원. 이전 내비 요청 결과는 표시 못 함 → "이번 실행에서 보낸 기록 없음"으로 표기 | evidence/17 |
| 2026-11-08 | 기능 확장 서버 테스트 (운행 관리·회수·이상·이력·메모 권한·인계·음성 해석) | 61 passed | Docker python:3.12 |
| 2026-11-08 | 기능 확장 앱 단위 테스트 (회수 요청 ID 재사용, 오프라인 유지, 구역 배치, 음성 확인 전 미저장·voice 출처·재질문) | 25개 통과, lint 버전 경고만 | `./gradlew :app:testDebugUnitTest :app:lintDebug` |
| 2026-11-08 | 에뮬레이터: 기사 선택·장비 상태 | 동작 | evidence/20 |
| 2026-11-08 | 에뮬레이터: B 1개 미하차 → S06 구역 강조(가운데) → "회수품이에요" → 회수 기록 → S04 복귀 → 재요청 → 관측 일치 완료 | 동작. 잔량 7+1−3=5 | evidence/21, 이력 |
| 2026-11-08 | 에뮬레이터: 실제 STT "다음 장소에 뭐 내려야 해" → 서버 해석 → 화면·TTS 응답 | 동작 ("C 서현역…박스 4개…앞쪽") | evidence/22. 이후 호스트 마이크 재연결 후 입력이 들어오지 않아 음성 회수 확인은 단위 테스트로 검증 |
| 2026-11-08 | 에뮬레이터: 듣기 준비 전 말이 끝나면 인식이 끝나지 않음 발견 → 12초 시간 제한 추가 | 시간 제한 후 "다시 말해 주세요" | VoiceViewModel |
| 2026-11-08 | 에뮬레이터: 공유 메모 작성 → 다른 기사에게 보임, 수정 이력 표시 | 동작 | evidence/23 |
| 2026-11-08 | 에뮬레이터: 인계 확정(기사 1) → 기사 2 선택 → 인수 확정 → 운행 기사 변경 | 동작, 요약에 개인 기록 제외 | evidence/24 |
| 2026-11-08 | 에뮬레이터: 남은 업무가 있을 때 운행 종료 → 사유 선택 → ended_incomplete | 동작 | evidence/25 |
| 2026-11-08 | 시연 도구(샘플 이상 관측) 버튼을 운행 화면에서 펼쳐야 보이도록 변경 | 탭 스크립트가 버튼을 잘못 눌러 이상 기록이 생긴 일을 계기로 오조작 방지 | — |
| 2026-11-08 | A0 서버 구조 이행: 시드 이동, UTC 함수 통합, 기능 계층 분리, 메모 방문지 조회 경계, trip 서비스 분리 | 각 이행 커밋 전 Docker pytest 61 passed | `086ed9e`, `7807cac`, `69c29d6`, `39c64db`; `docker run ... loadmate-backend ... pytest -q` |
| 2026-11-08 | A0 PostgreSQL 대표 시나리오: A 예상 7/관측 8 → 확인 필요(미저장) → 관측 7 → 완료, 동일 키 재전송 | API 응답·DB 재조회 모두 일치, 잔량 7·A 완료 | `docker compose up -d --build`; `app.seed --reset`; backend 컨테이너에서 HTTP 요청 |
| 2026-11-08 | A0 backend 재시작 후 PostgreSQL 복원 | 잔량 7, A 완료, 배송 수량 3 유지 | `docker compose restart backend` 후 `GET /trips/sample-trip-1` |
