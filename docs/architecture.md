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

## Android (기획서 21장)

- Feature 기반 패키지: `feature/<trip|delivery|cargo|records|notes|handover|settings>/{domain,data,presentation}`. 필요할 때 만든다(빈 계층·UseCase 금지).
- Presentation: Compose + ViewModel. `StateFlow<UiState>` 노출, `UiAction` 수신. 일회성 이동·알림이 생기면 `UiEffect` 추가.
- Domain은 Android·Pleos·HTTP에 의존하지 않는다. 구현체 연결은 `AppContainer`(수동 DI)에서만.
- Pleos SDK는 `core/pleos` 뒤에 숨기고 화면에 직접 노출하지 않는다. 인터페이스(`NaviGateway`, `RearDoorGateway`, `SpeechGateway`) + Pleos 구현, 조립은 `AppContainer`.
- 디자인 시스템: `core/designsystem` — PleosLab에서 가져온 공식 색상 토큰·타이포그래피(출처·PROJECT 값 표시 유지). 화면에서 hex 직접 사용 금지.
- 예상 잔량·완료 판정의 최종 기준은 서버. 앱은 서버 확정값과 임시 표시를 구분한다.
- 기준 버전: AGP 8.7.3 (Android Studio 2024.2.1 호환), Kotlin 2.0.0, compileSdk 35, targetSdk 34, minSdk 28.

현재 구현: `feature/settings` — 서버 `/health` 확인 + Pleos 연동 상태(내비 목적지 전달·뒷문·음성 인식 실측) 화면 S10. `core/pleos` — NaviHelper · Vehicle Door · SpeechToText.

## Backend (기획서 22장)

- `app/features/<name>/` 에 router · service · repository · schemas · models 를 함께 둔다.
- Router: HTTP·검증만. Service: 업무 규칙·트랜잭션. Repository: SQLAlchemy. 스키마 변경은 Alembic.
- 외부 배송 시스템·대화 AI·영상 결과는 `app/integrations/`(예정) 어댑터로 분리.

현재 구현: `features/health`, `features/trip` (운행·방문지·물품·관측·확정 이벤트, 예상 잔량·비교 규칙 `rules.py`, 샘플 시드 `seed.py`). 업무 오류는 `core/errors.py` 의 `AppError` → `{"detail": {"code", "message"}}`.

샘플 운행 초기화(시연 반복): `docker compose exec backend python -m app.features.trip.seed --reset`
테스트(로컬 venv 없이): `docker run --rm -v "$PWD/backend:/srv" -w /srv loadmate-backend sh -c "pip install -q -r requirements-dev.txt && pytest -q"`

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
