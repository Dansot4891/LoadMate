# 다음 작업 (인수인계)

작성: 2026-11-08 · 접수 마감 **2026-11-15** · 2차 PT 2026-12-03
배경과 근거: [planning-review.md](planning-review.md) · 구조와 실행: [architecture.md](architecture.md) · 계약: [api-contract.md](api-contract.md)

## 현재 상태 (2026-11-08 확인)

| 항목 | 상태 |
|---|---|
| Android | debug 빌드, 단위 테스트 6개, lint 통과(버전 경고만). 화면은 서버 연결 상태(S10) 하나. 에뮬레이터(Pleos Connect v2.1, user 10)에서 서버·DB 연결 표시 확인 |
| Backend | `docker compose up --build -d` 로 실행, `GET /health` → `{"status":"ok","version":"0.1.0","database":"ok"}` 확인. pytest 2개 통과 |
| Pleos SDK | `core/pleos` 에 NaviHelper(목적지 전달)·Vehicle Door(REAR 구독)·SpeechToText(ON_DEVICE) 연결. 권한 `NAVI_ROUTE_SEARCH`, `CAR_DOORS`, `STT_SERVICE`. S10에 "Pleos 연동" 실측 섹션 |
| Git | `git init` + 스테이징만. **커밋 없음.** 원격 저장소 없음 |

## 개발 환경 메모

- Docker 엔진: **Colima** (Homebrew 설치, Docker Desktop 아님). 재부팅 후 `colima start` 필요. 자동 시작은 `brew services start colima`. `docker`/`docker compose` 명령은 그대로 사용.
- Compose 플러그인 경로: `~/.docker/config.json` 의 `cliPluginsExtraDirs` = `/opt/homebrew/lib/docker/cli-plugins`.
- 시스템 Python은 3.9 → 서버는 Docker(Python 3.12)로 실행. 로컬 venv가 필요하면 `brew install python@3.12`.
- Android 빌드는 VS Code 터미널에서도 가능. `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"` 필요 (Android Studio는 SDK·JBR·에뮬레이터 때문에 삭제 금지).
- 에뮬레이터: `~/Library/Android/sdk/emulator/emulator -avd Pleos_Connect_v2.1 -no-snapshot-save -no-boot-anim -feature -Vulkan,-GLDirectMem` · AAOS 사용자 10 (`am start --user 10 -n com.loadmate.app/.MainActivity`).
- 호스트 마이크(STT 시험): `adb emu avd hostmicon` / 끝나면 `hostmicoff`.
- Virtual Console: `adb forward tcp:8088 tcp:8088` → http://127.0.0.1:8088/ (열린 세션이 없으면 상태 변경 503. CLI는 1초마다 `curl -X POST .../browser/session?client=cli`). 트렁크: `curl -X PUT '.../vehicle/body?trunk=true'`.

## 1순위 — 지금 할 일 (11/8~9)

1. ✅ **앱↔서버 에뮬레이터 연결 확인** (2026-11-08)
   - `docker compose up -d` → 에뮬레이터 실행 → debug APK 설치 → 화면에 "정상 · 서버 0.1.0 · DB 연결됨" 확인.
   - 결과를 아래 "검증 기록"에 남긴다.
2. ✅ **Pleos 연동 모듈 `core/pleos` 생성** (2026-11-08)
   - `app/.../core/pleos/` 패키지 (core/designsystem 과 같은 방식, Gradle 모듈 아님). 인터페이스 `NaviGateway` · `RearDoorGateway` · `SpeechGateway` + Pleos 구현. SDK 타입은 이 패키지 밖으로 나가지 않는다.
   - 조립은 `AppContainer` 에서만. 화면(S10)은 `LifecycleStartEffect` 로 화면이 보일 때만 연결·구독하고 ON_STOP 에서 해제.
3. ✅ **에뮬레이터 SDK 실측 3건** (2026-11-08) — 상세는 아래 "실측 결과". **대표 시나리오 설계에 영향 → 결정 대기(아래 "결정 필요")**
   - [x] NaviHelper `requestRoute` — 내비 앱이 실제로 경로 안내 시작. 실패 콜백 `onError(CAUSE_SEARCH_ROUTE)`.
   - [x] SpeechToText **ON_DEVICE** — Client ID/Secret 없이 initialize·인식 성공(한국어). 오인식 있음.
   - [x] Door `REAR` 구독 — Virtual Console Trunk Open/Close → 100/0 알림 수신, 화면 이탈 시 해제 확인.

### 실측 결과 (2026-11-08, Pleos Connect Emulator v2.1.0 · Navi Service 3.0.4 · CAAS 4.2.10 · user 10)

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

### 추가 실측 (2026-11-08 오후, 임시 debug 리시버로 시험 · 코드는 커밋하지 않음)

| 항목 | 결과 | 시나리오 영향 |
|---|---|---|
| 경유지 한 번에 전달 `requestRouteWithWaypoints` (`NAVI_ROUTE_SEARCH`) | ✅ 경유지 A·B + 목적지 C 경로 생성. 내비 진행 막대에 경유지 2개 표시, 화면은 다음 방문지 A. `onRouteStarted.name` = 첫 경유지 이름. **경유지는 FIRST·SECOND 2개까지** | A·B·C 3곳은 한 번 요청으로 가능. 4곳 이상은 나눠서 요청 |
| `getWaypointInfo` | `NAVI_ROUTE` 권한 필요 (SecurityException) | 쓰려면 권한 추가 |
| 알림이 내비 위에 뜨는지 | 카테고리 미지정 → 헤드업 안 뜸 (`CarHeadsUpNotificationManager: category not set`). `CATEGORY_MESSAGE`·`CATEGORY_NAVIGATION` → **내비 위 헤드업 표시** | 주행 중 짧은 안내는 헤드업 알림 사용 가능. 카테고리 필수 |
| TextToSpeech 3.3.1 ON_DEVICE (`TTS_SERVICE`) | ✅ 자격 증명 없이 initialize·한국어 재생(화자 seoyoon). `speak(text, isFinal)` 두 번째 인자가 false 면 문장 미완료로 처리되어 `onDone` 안 옴 → **true 로 호출** | 내비 화면 위에서도 음성 안내 가능 |
| 앱이 백그라운드(내비 전면)일 때 STT | ✅ 인식 동작. "다음 장소에 뭐 내려야 해" → "다음 장소에 뭘 내려야 해?" | 이동 중 조회(14장 4단계) 가능. 단, 듣기 시작 트리거(버튼·알림 액션)는 별도 설계 필요 |
| 앱이 백그라운드일 때 내비 콜백 | ✅ 리스너를 유지하면 수신 | 결정 1 근거 |
| 도착 신호 `onWayPointArrived` / `onDestinationArrived` | ❌ 확인 불가. `adb emu geo fix` 위치는 내비가 NMEA 체크섬 오류로 버림. Virtual Console 가상 주행(D·60km/h)은 차량 속도만 바뀌고 내비 위치는 그대로 | 도착은 **기사 확인 버튼**을 기본 유지 (기존 방침과 같음) |

## 2순위 — 대표 시나리오 (11/10~12)

기획서 14장 A·B·C 운행, 목적지 A에서 1개 미하차.

1. ✅ `docs/api-contract.md` 에 엔드포인트 먼저 작성 (운행 목록 · 최초 적재 확정 · 하차 완료 요청 · 잔량 비교 · 기사 정정). 상태 변경 요청은 멱등 키. (2026-11-08)
2. ✅ Backend `features/trip` (물품·관측도 같은 Feature에 둠 — 지금은 나눌 만큼 크지 않음) + Alembic 첫 마이그레이션 `c4994a640ec7` + 샘플 시드(방문지 3곳, 박스 10개 = A3·B3·C4). 서버 시작 시 없으면 생성. (2026-11-08)
3. ✅ 예상 잔량 = 확정 직전 잔량 − 이번 하차 (+ 회수·추가 상차는 15장 구현 때) — `rules.py` 순수 함수 + 단위 테스트. (2026-11-08)
4. 관측값: 우선 샘플/수동 입력 또는 녹화 영상 분석 결과. 화면에 출처 표시.
5. **다음:** Android 화면: S01 운행 준비 → S05 적재 확정 → S02/S03 내비 전달(A·B·C 경유지 한 번에, 기사 확인 후, 실패 시 다시 보내기) → S04 현장 작업 → S06 불일치 확인. 내비 리스너는 운행 범위로 유지.
6. 완료 기준: 앱 재실행 후 상태 복원, 완료 요청 중복 차감 없음.

## 3순위 — 제출 준비 (11/13~15)

- [ ] 시연 영상 녹화 (실제 동작 / 에뮬레이터 신호 / 녹화 영상 / 샘플 데이터 구분 자막)
- [ ] release APK → `apk/Loadmate_Loadmate_release.apk`. release 서버 주소 `-Ploadmate.apiBaseUrl=` 결정 (없으면 앱이 "서버 미설정" 표시)
- [ ] 서명 키 생성·보관 (Git 제외)
- [ ] GitHub 원격 저장소 생성 (공개/비공개 결정, 비공개면 심사 계정 Collaborator·쓰기 권한 주의), README 필수 항목 점검
- [ ] 기획서 수정 반영 요청 (planning-review.md §5) — 기획자와 공유

## 결정 필요 (사용자·기획자)

- [x] **(실측 반영) 내비 리스너 수명**: 운행 중(시작~종료)에는 유지, 운행 종료 시 해제 (2026-11-08 결정. 앱이 내비 기반으로 동작하므로 기본값). CLAUDE.md 원칙 8 갱신
- [x] **(실측 반영) 목적지 전달**: 전달 전 기사 확인, 실패 시 실패 표시·다시 보내기·목적지 정보 유지 (2026-11-08 결정, 기획서 16장 "내비 요청 실패"와 같음). 출발 시 A·B·C를 경유지로 한 번에 전달해 재요청 자체를 줄인다
- [x] **(실측 반영) 음성 명령 확인**: 인식 문장을 보여 주고 기사가 맞다고 확인한 뒤 실행 (2026-11-08 사용자 결정). 기획서 5장 "업무를 바꾸는 요청은 요약 후 확인"과 같은 방향

- [ ] 2월 현장 시연 서버: 클라우드 배포 vs 앱 내 데모 모드(명확 표시)
- [ ] 대화 AI 모델·실행 위치(외부 API / 로컬), 비용·개인정보 처리
- [ ] 외부 서버·외부 AI 사용 가능 여부 공모전 문의 (/guide/help)
- [ ] GitHub 공개 여부

## 검증 기록

| 날짜 | 항목 | 결과 | 근거 |
|---|---|---|---|
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
