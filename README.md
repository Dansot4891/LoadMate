# Loadmate

**다지점 배송 기사가 차량 화면과 음성으로 짐칸 상태를 확인하며 운행을 이어가는 Pleos Connect 물류 동반자 앱.**

- 팀: Team LoadMate · 앱: Loadmate
- **APK: [`apk/TeamLoadMate_Loadmate_release.apk`](apk/TeamLoadMate_Loadmate_release.apk)** (서명된 release, 2026-11-15)

> 샘플 운행 A→B→C의 적재 확인·내비 전달·하차 불일치 확인·회수·메모·내역이 앱과 서버에 연결되어 있습니다. 운행·적재 관측은 샘플이며, 음성 해석은 AI 키가 있으면 AI로, 없거나 연결이 끊기면 규칙 기반으로 처리합니다. Pleos SDK 동작은 에뮬레이터에서만 확인했습니다.

## 구성
| 폴더 | 내용 |
|---|---|
| `android/` | Kotlin · Jetpack Compose 차량 앱 (Pleos Connect, minSdk 28) |
| `backend/` | FastAPI · PostgreSQL · Alembic |
| `vision/` | 적재 영상 분석 (예정, 녹화 영상 미연결) |
| `docs/` | [구조와 실행](docs/architecture.md) · [API 계약](docs/api-contract.md) |
| `apk/` | 제출용 release APK `TeamLoadMate_Loadmate_release.apk` |

## 실행 방법

release APK는 에뮬레이터와 **같은 PC에서 실행한 서버**(`http://10.0.2.2:8000`)에 접속하도록 빌드했습니다. 서버를 먼저 켜고 APK를 설치합니다.

1. Docker 엔진을 켜고 저장소 루트에서 서버를 시작합니다. 처음 실행하면 샘플 운행(A→B→C)과 샘플 차량 노트가 자동으로 만들어집니다.
   ```bash
   docker compose up -d --build        # 서버 + DB → http://localhost:8000/health
   ```
2. Pleos Connect 에뮬레이터를 켜고 APK를 설치·실행합니다(AAOS 사용자 10).
   ```bash
   adb -s emulator-5554 install -r apk/TeamLoadMate_Loadmate_release.apk
   adb -s emulator-5554 shell am start --user 10 -n com.loadmate.app/.MainActivity
   ```
   하단 도크의 상자 모양 Loadmate 아이콘으로도 실행할 수 있습니다. 처음 실행 시 알림 허용을 눌러 주세요(도착·짐칸 알림).
3. `운행 목록` → `분당 납품 운행 · A·B·C`를 열어 시작합니다. `docker compose exec backend python -m app.seed --reset`은 샘플 운행의 기록과 일부 메모를 지우고 처음 상태로 되돌립니다.

소스에서 빌드하려면 ([구조와 실행](docs/architecture.md)):
```bash
cd android
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
```
debug와 release는 서명이 달라 바꿔 설치할 때는 먼저 `adb uninstall com.loadmate.app`을 실행합니다.

## AI 기능 켜기 (선택)

키가 없어도 모든 화면이 동작합니다. 음성 해석만 정해진 표현을 처리하는 규칙 기반으로 제한되며, 음성 창에 `AI 키 없음 · 규칙 기반`으로 표시됩니다.

AI를 켜려면 [Google AI Studio](https://aistudio.google.com/apikey)에서 받은 Gemini API 키를 저장소 맨 위 `.env`에 넣고 서버를 다시 시작합니다. 키는 서버에서만 사용하며 앱과 Git에는 들어가지 않습니다.

```bash
cp .env.example .env      # GEMINI_API_KEY= 뒤에 키를 입력
docker compose up -d --build
```

음성 창에 `해석: AI`가 보이면 AI가 말을 해석한 것입니다. AI를 쓸 수 없으면 `AI 연결 안 됨 · 규칙 기반`으로 표시하고 규칙 기반으로 동작합니다. Gemini 무료 등급은 입력 내용이 Google의 제품 개선과 사람 검토에 쓰일 수 있으므로 샘플 데이터로만 사용하세요.

## 방문지 장소 검색 켜기

방문지 추가는 VWorld 검색 API를 씁니다. [VWorld](https://www.vworld.kr/dev/v4api.do) 인증키를 저장소 맨 위 `.env`의 `VWORLD_API_KEY=` 뒤에 넣고 `docker compose up -d`로 서버를 다시 시작합니다. 키가 없으면 검색 창에 `장소 검색 키가 설정되지 않았습니다`로 표시됩니다. 샘플 방문지는 저장된 좌표로 내비 전달이 동작합니다.

## 테스트 환경 및 설정

| 항목 | 값 |
|---|---|
| 에뮬레이터 | **Pleos Connect Emulator v2.1.0** (AAOS, Android 14 / API 34, AVD `Pleos_Connect_v2.1`) |
| 화면 규격 | **2560 × 1440** 가로. 왼쪽은 시스템 주행 정보, 오른쪽 앱 영역에 Loadmate 표시 |
| 앱 | minSdk 28 · targetSdk 34 · Kotlin · Jetpack Compose |
| Pleos SDK | Vehicle 2.3.2, NaviHelper 3.0.5, SpeechToText·TextToSpeech 3.3.1 |
| 서버 | Docker(Compose) · FastAPI · PostgreSQL. 에뮬레이터에서 `10.0.2.2:8000` 으로 접속 |
| 개발 도구 | Android Studio 2024.2.1 |

- 필요한 설정: 서버만 켜면 모든 화면이 동작합니다. AI 음성 해석과 방문지 장소 검색은 위의 `.env` 키를 넣었을 때만 켜집니다(선택).
- 음성 입력: 에뮬레이터에서는 `adb emu avd hostmicon`으로 PC 마이크를 연결합니다.
- 운행·적재 관측·카메라 수량은 **샘플 데이터**이며, 내비 도착 신호는 에뮬레이터 GPS 문제로 시연 영상에서 debug 빌드 전용 입력을 썼습니다. 에뮬레이터 결과는 실차 지원을 의미하지 않습니다.
- 검증(2026-11-15): 서버 pytest 150개, 앱 단위 테스트 129개·lint 통과, 서명된 release APK를 에뮬레이터에 설치해 운행 목록 표시 확인.

## release APK 다시 만들기

서명 정보는 `android/keystore.properties`(Git 제외: `storeFile`·`storePassword`·`keyAlias`·`keyPassword`)에 두고 빌드합니다.
```bash
cd android
./gradlew :app:assembleRelease -Ploadmate.apiBaseUrl=http://10.0.2.2:8000
cp app/build/outputs/apk/release/app-release.apk ../apk/TeamLoadMate_Loadmate_release.apk
```

## 현재 범위와 다음 작업

적재 카메라·영상 분석, 운영사 배송 시스템 연동은 아직 연결하지 않았습니다(운행·적재 데이터는 샘플). 그 밖에 오프라인 디스크 저장, 메모 사진 첨부 등은 남아 있습니다.
