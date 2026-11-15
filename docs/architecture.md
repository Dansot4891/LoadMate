# 구조와 실행

## 저장소

```
loadmate/
  android/            Kotlin · Compose 차량 앱 (Android Studio로 이 폴더만 연다)
  backend/            FastAPI · SQLAlchemy · Alembic
  vision/             적재 영상 분석 (별도 Python 프로세스, 미구현)
  docs/               기획 대조, API 계약, 구조, 검증 기록
  apk/                제출용 release APK (팀명_앱이름_release.apk)
  docker-compose.yml  backend + PostgreSQL
```

## Android (기획서 21장) → 상세 `android/CLAUDE.md`

- `presentation → domain ← data`. 기능별 `feature/<name>/{domain,data,presentation}`, 화면 하나 = `presentation/<screen>/` 폴더 (Screen · UiState · UiAction · ViewModel · components)
- data 는 `dto/`(kotlinx.serialization) + `mapper/` + `XxxRepositoryImpl`. 공용 HTTP·결과 타입은 `core/network`
- DI 는 `AppContainer`(수동). Pleos SDK 는 `core/pleos` Gateway 뒤에 숨긴다
- 다른 기능은 `domain` 만 참조. 여러 화면이 보는 운행 데이터는 Repository 의 StateFlow, 내비 리스너는 운행 범위 객체가 소유
- 기준 버전: AGP 8.7.3 (Android Studio 2024.2.1 호환), Kotlin 2.0.0, compileSdk 35, targetSdk 34, minSdk 28

## Backend (기획서 22장) → 상세 `backend/CLAUDE.md`

- 기능마다 `router`(HTTP) · `schemas` · `service`(규칙·트랜잭션) · `repository`(SQLAlchemy) · `models`. 큰 기능(trip)은 `service/` 패키지로 분리
- 기능 의존 방향 `fleet ← trip ← memo ← handover ← assistant` (역방향·순환 금지). 샘플 데이터는 `app/seed.py`
- 외부 배송 시스템·대화 AI·영상 결과는 `app/integrations/` 어댑터. 업무 오류는 `AppError` → `{"detail": {"code", "message"}}`

2026-11-08 위 구조로 결정.

## 실행

### 서버 (Docker 엔진: Colima — 재부팅 후 `colima start`)
```bash
docker compose up --build        # http://localhost:8000/health , /docs
```

### 서버 (Docker 없이, Python 3.10+ 권장)
```bash
cd backend
python3 -m venv .venv && .venv/bin/pip install -r requirements-dev.txt
cp .env.example .env              # 로컬 PostgreSQL 주소 설정
.venv/bin/alembic upgrade head
.venv/bin/uvicorn app.main:app --reload --port 8000
.venv/bin/pytest
```

### 앱
```bash
cd android
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
~/Library/Android/sdk/emulator/emulator -avd Pleos_Connect_v2.1 -no-snapshot-save -no-boot-anim -feature -Vulkan,-GLDirectMem
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
```

release 빌드 서버 주소: `./gradlew :app:assembleRelease -Ploadmate.apiBaseUrl=https://...` (미지정 시 앱에 "서버 미설정" 표시).
