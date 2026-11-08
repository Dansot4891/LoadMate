# Loadmate — Pleos 물류 동반자

Pleos Connect(AAOS 차량 IVI) 앱 + FastAPI 서버 + 영상 분석. 현대자동차그룹 차량용 앱 개발 공모전 출품작 (접수 마감 2026-11-15, 2차 PT 2026-12-03).
기획 기준: 기획자가 작성한 `Pleos_물류동반자_기획개발안.docx` v2.1 (저장소 밖). 검토 결과는 `docs/planning-review.md`.
**지금 할 일: `docs/next-steps.md`** (작업 후 검증 기록·체크리스트 갱신).
SDK 실측 기록은 형제 저장소 `../hyundai_app` (PleosLab: docs/sdk-catalog.md, docs/vehicle-api-matrix.md).

## 명령

```bash
# 서버 (Docker 엔진 = Colima. 재부팅 후 `colima start`)
docker compose up --build
cd backend && .venv/bin/pytest

# 앱
cd android && export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
```

## 원칙

1. API 계약은 `docs/api-contract.md` 를 먼저 고친 뒤 앱·서버를 구현한다.
2. 관측(카메라·AI)·계획·기사 확인을 구분한다. 관측만으로 배송 완료를 확정하지 않는다. 잔량·완료 판정의 최종 기준은 서버.
3. 상태 변경 요청은 중복 반영되지 않게 한다(멱등 키). 개인 기록은 자동 공유하지 않는다.
4. 권한은 실제 호출하는 API의 것만 선언. PRIVATE_* 금지. 새 Pleos API를 연결하면 Manifest와 docs/planning-review.md 갱신.
5. 샘플 데이터·녹화 영상·에뮬레이터 신호·미연결 SDK는 화면과 문서에 그렇게 표시한다. 실제 연동이나 실차 지원으로 표현하지 않는다. 실패 시 데모 데이터로 조용히 전환하지 않는다.
6. 공식 값만 공식이라 부른다. 디자인 토큰은 `core/designsystem`(출처·PROJECT 표시 유지). 화면에서 hex 직접 사용 금지.
7. 자격 증명·고객 개인정보·기획서 원본은 Git에 넣지 않는다 (.env, local.properties).
8. Android: Feature 기반 + MVVM(StateFlow UiState / UiAction). 빈 계층·UseCase를 미리 만들지 않는다. Pleos SDK 객체를 화면에 노출하지 않는다. 리스너는 화면 이탈·release 전 해제.
9. 완료 기준은 화면 생성이 아니라 빌드·서버 실행·DB 저장/복원·대표 시나리오 연속 동작. 실행 결과를 docs에 기록.

## 주의

- Vehicle 2.3.2: 차량 속도 API 없음, Gear deprecated, DrivingMode 에뮬레이터 미지원 → 주행/정차는 기사 확인 버튼이 기본.
- STT/TTS/FusedLocation 콘솔 준비 중. STT ON_DEVICE는 에뮬레이터에서 자격 증명 없이 동작(2026-11-08, 오인식 있음). 호스트 마이크: `adb emu avd hostmicon`.
- NaviHelper `requestRoute` 시 내비 앱이 메인 패널을 덮어 Loadmate가 ON_STOP 된다 → 화면 단위 리스너는 경로 결과를 못 받는다 (docs/next-steps.md 실측 결과).
- AGP 8.7.3 고정 (Studio 2024.2.1).
