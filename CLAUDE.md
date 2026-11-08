# Loadmate — Pleos 물류 동반자

Pleos Connect(AAOS 차량 IVI) 앱 + FastAPI 서버 + 영상 분석. 현대자동차그룹 차량용 앱 개발 공모전 출품작 (접수 마감 2026-11-15, 2차 PT 2026-12-03).
기획 기준: 기획자가 작성한 `docs/Pleos_물류동반자_기획개발안.docx` v2.1 (Git 제외). 검토 결과는 `docs/planning-review.md`.
**새 세션은 `docs/next-steps.md`부터** (인수인계: 현재 상태·재실행 명령·결정·다음 할 일·주의). 작업 후 검증 기록·체크리스트 갱신. 기능별 현황 `docs/feature-status.md`.
SDK 실측 기록은 형제 저장소 `../hyundai_app` (PleosLab: docs/sdk-catalog.md, docs/vehicle-api-matrix.md).

이 파일에는 프로젝트 공통 원칙만 둔다. 영역별 구조·규칙:
- 앱 → **`android/CLAUDE.md`** (화면 폴더·UiState/UiAction·DTO/mapper·AppContainer·Pleos SDK 제약)
- 서버 → **`backend/CLAUDE.md`** (router·schemas·service·repository·models, 기능 의존 방향, 멱등 키)
- 진행 상태·이행 중인 작업은 CLAUDE.md 에 쓰지 않고 `docs/next-steps.md` 에 쓴다

## 명령

```bash
# 서버 (Docker 엔진 = Colima. 재부팅 후 `colima start`)
docker compose up --build

# 앱
cd android && export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

## 원칙

1. API 계약은 `docs/api-contract.md` 를 먼저 고친 뒤 앱·서버를 구현한다.
2. 관측(카메라·AI)·계획·기사 확인을 구분한다. 관측만으로 배송 완료를 확정하지 않는다. 잔량·완료 판정의 최종 기준은 서버.
3. 상태 변경 요청은 중복 반영되지 않게 한다(멱등 키). 개인 기록은 자동 공유하지 않는다.
4. 권한은 실제 호출하는 API의 것만 선언. PRIVATE_* 금지. 새 Pleos API를 연결하면 Manifest와 docs/planning-review.md 갱신.
5. 샘플 데이터·녹화 영상·에뮬레이터 신호·미연결 SDK는 화면과 문서에 그렇게 표시한다. 실제 연동이나 실차 지원으로 표현하지 않는다. 실패 시 데모 데이터로 조용히 전환하지 않는다.
6. 공식 값만 공식이라 부른다. 디자인 토큰은 `core/designsystem`(출처·PROJECT 표시 유지). 화면에서 hex 직접 사용 금지.
7. 자격 증명·고객 개인정보·기획서 원본은 Git에 넣지 않는다 (.env, local.properties).
8. 빈 계층·UseCase를 미리 만들지 않는다. 구조 규칙은 영역별 CLAUDE.md 를 따른다. 내비 리스너는 운행 중(운행 시작~종료) 유지 (2026-11-08 결정).
9. 완료 기준은 화면 생성이 아니라 빌드·서버 실행·DB 저장/복원·대표 시나리오 연속 동작. 실행 결과를 docs에 기록.
