# API 계약

앱(android)과 서버(backend)가 공유하는 HTTP 계약. 엔드포인트를 추가·변경하면 **이 문서를 먼저 고친 뒤** 양쪽을 구현한다.
기획서 22장 원칙: 경로·필드·테이블은 구현 단계에서 확정하며, 여기 적힌 것만 확정이다.

기본 주소: debug 앱 `http://10.0.2.2:8000` (에뮬레이터 → 개발 PC) · 브라우저 `http://localhost:8000/docs` (자동 OpenAPI)

## GET /health — 확정

연결 상태 표시(F65, S10). DB가 응답하지 않아도 **200**으로 응답하고 `database`로 구분한다.

```json
{ "status": "ok" | "degraded", "version": "0.1.0", "database": "ok" | "unavailable" }
```

| 구현 | 위치 |
|---|---|
| 서버 | `backend/app/features/health/router.py` |
| 앱 파서 | `android/.../feature/settings/data/HealthResponseParser.kt` |

## 예정 (미확정)

대표 시나리오(기획서 14장) 순서로 추가: 운행 목록 조회 → 최초 적재 기준 확정 → 하차 완료 요청 → 예상/관측 잔량 비교 → 기사 정정.
중복 차감 방지를 위해 상태를 바꾸는 요청에는 클라이언트 생성 요청 ID(멱등 키)를 둔다.
