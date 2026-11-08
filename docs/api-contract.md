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

## 공통 규칙 — 확정 (2026-11-08)

- JSON, UTF-8. 시각은 ISO 8601 UTC (`2026-11-08T05:30:00Z`).
- 수량은 **종류(kind)별 개수** `{"box": 10}` 로 주고받는다. 서로 다른 종류를 합산해 판정하지 않는다 (기획서 16장).
- 물품 개별 ID 연결은 하지 않는다. 비교 결과에는 항상 `count_only: true` 를 넣어 **총량 검사임**을 표시한다 (R06).
- 출처 표시: 운행은 `data_source`(`sample`), 관측은 `source`(`sample` · `manual` · `recorded_video`). 앱은 이 값을 화면에 그대로 표시한다 (원칙 5).
- 상태를 바꾸는 요청(`POST .../baseline`, `POST .../complete`, `POST .../observations`)은 클라이언트가 만든 `idempotency_key`(UUID 문자열)를 보낸다.
  - 같은 운행에서 같은 키·같은 내용 재전송 → 처음 결과를 다시 돌려준다 (`replayed: true`, 중복 반영 없음).
  - 같은 키·다른 내용 → `409 idempotency_key_conflict`.
  - `needs_confirmation` 처럼 **반영되지 않은 응답은 키를 소비하지 않는다** → 같은 키로 다시 보낼 수 있다.
- 오류 응답: `{"detail": {"code": "<code>", "message": "<사람이 읽는 설명>"}}`. 코드는 각 엔드포인트에 적는다. 입력 검증 실패는 FastAPI 기본 `422`.

## 운행 (대표 시나리오, 기획서 14장) — 확정 (2026-11-08)

### 계산 규칙 (서버가 최종 기준, 원칙 2)

- **예상 잔량** (R05) = 최초 기준 확정 수량 − 지금까지 확정된 하차량 (+ 회수·추가 상차: 15장 구현 때 추가). 운행 이벤트 기록을 합산해 계산한다.
- **비교 상태** `comparison.state`
  | 값 | 조건 |
  |---|---|
  | `no_observation` | 기준 확정 이후 관측이 없음 |
  | `unavailable` | 최신 관측이 `status: "unavailable"` (카메라 가림·단절) |
  | `stale` | 최신 관측이 `stale_after_seconds`(기본 300초) 보다 오래됨 |
  | `match` | 모든 종류에서 예상 = 관측 |
  | `mismatch` | 한 종류라도 다름 |
- `match` 외에는 정상으로 판정하지 않는다 (R10). 마지막 값으로 정상 확정하지 않는다.
- 비교 대상 `expected`: 기준 확정 전에는 **계획 수량 합계(검수 목록)**, 확정 후에는 예상 잔량. 최신 관측은 `observed_at` 기준.

### GET /trips

```json
{ "trips": [ { "id": "sample-trip-1", "name": "샘플 운행 · A·B·C", "status": "planned", "data_source": "sample", "stop_count": 3 } ] }
```

### GET /trips/{trip_id}

```json
{
  "id": "sample-trip-1", "name": "샘플 운행 · A·B·C", "data_source": "sample",
  "status": "planned | in_progress | completed",
  "baseline": null | { "counts": {"box": 10}, "confirmed_at": "...", "observation_id": 1 | null },
  "stops": [
    { "id": "stop-a", "seq": 1, "label": "A", "name": "판교역 납품처", "address": "...", "latitude": 37.3947, "longitude": 127.1112,
      "status": "pending | completed | partial",
      "completed_at": null, "resolution": null | "matched" | "driver_confirmed",
      "cargo": [ { "id": 1, "item_name": "박스", "kind": "box", "planned_qty": 3, "delivered_qty": 0 } ] }
  ],
  "expected_remaining": {"box": 10} | null,
  "comparison": <Comparison>
}
```
`expected_remaining` 는 기준 확정 전 `null`. 오류: `404 trip_not_found`.

### POST /trips/{trip_id}/observations — 관측 기록

카메라 분석(vision)·샘플·기사 수동 입력이 같은 형식으로 보낸다. 관측만으로 업무 상태를 바꾸지 않는다 (원칙 2).
```json
요청 { "idempotency_key": "uuid", "source": "sample | manual | recorded_video", "status": "ok | unavailable",
       "counts": {"box": 8} | null, "observed_at": "2026-11-08T05:30:00Z", "note": "선택" }
응답 201 { "observation": { "id": 3, "source": "sample", "status": "ok", "counts": {"box": 8}, "observed_at": "..." }, "replayed": false,
           "comparison": <Comparison> }
```
`status: "ok"` 이면 `counts` 필수, `unavailable` 이면 `counts` 는 `null`. 오류: `404 trip_not_found`, `409 idempotency_key_conflict`.

### POST /trips/{trip_id}/baseline — 최초 적재 기준 확정 (R02, S05)

기사가 확정한 수량을 기준으로 저장한다. 관측과 다르게 정정해도 된다(오인식 수정). 운행당 1회.
```json
요청 { "idempotency_key": "uuid", "counts": {"box": 10}, "observation_id": 1 | null }
응답 200 { "trip": <Trip>, "replayed": false }
```
오류: `404 trip_not_found`, `409 baseline_already_confirmed`, `409 idempotency_key_conflict`, `422 observation_not_found`.

### POST /trips/{trip_id}/stops/{stop_id}/complete — 하차 완료 요청 (R04~R07, S04·S06)

```json
요청 { "idempotency_key": "uuid", "delivered": {"box": 3}, "resolution": null | "driver_confirmed" }
응답 200 { "result": "completed | needs_confirmation", "replayed": false,
           "stop": <Stop>, "expected_remaining": {"box": 7}, "comparison": <Comparison> }
```
- 서버는 `이번 하차 반영 후 예상 잔량` 을 최신 관측과 비교한다.
- `match` → 완료 확정 (`resolution: "matched"`).
- 그 외(`mismatch`·`stale`·`unavailable`·`no_observation`)이고 `resolution` 이 없으면 → **`needs_confirmation`, 아무것도 저장하지 않음.** 앱은 S06에서 근거를 보여 주고 기사가 고른다:
  - 남은 물품을 찾아 내림 → 새 관측 기록 후 같은 요청 재전송
  - "이미 다 내렸다" / 확인 불가 상태에서 수동 확인 → `resolution: "driver_confirmed"` 로 재전송 → 완료 확정, 당시 비교 결과를 함께 기록 (R07 근거와 확인 기록 분리)
- `delivered` 가 계획보다 적으면 `status: "partial"`. 남은 수량은 예상 잔량에 그대로 남는다 (16장 부분 납품).
- 오류: `404 trip_not_found`, `404 stop_not_found`, `409 baseline_not_confirmed`, `409 stop_already_completed`, `409 idempotency_key_conflict`, `422 delivered_exceeds_planned`, `422 unknown_kind`.

### Comparison 객체

```json
{ "state": "match | mismatch | no_observation | unavailable | stale",
  "expected": {"box": 7} | null, "observed": {"box": 8} | null,
  "differences": [ { "kind": "box", "expected": 7, "observed": 8 } ],
  "observation": null | { "id": 3, "source": "sample", "status": "ok", "observed_at": "..." },
  "count_only": true, "stale_after_seconds": 300 }
```

| 구현 | 위치 |
|---|---|
| 서버 | `backend/app/features/trip/` (router · service · repository · schemas · models) |
| 계산 규칙 | `backend/app/features/trip/rules.py` (DB 없는 순수 함수, 단위 테스트) |
| 샘플 데이터 | `backend/app/features/trip/seed.py` (A3·B3·C4 박스 10개) |

## 운행 관리·회수·이상·이력 — 확정 (2026-11-08)

공통: 요청 본문에 `idempotency_key`, 선택 `source`(`screen`·`voice`, 기본 screen). 헤더 `X-Driver-Id`(현재 기사, 인증 아님 · F56)는 이력 작성자로 기록.
응답은 모두 `{"trip": <Trip>, "replayed": bool}`. `<Trip>` 에 `driver_id`, `vehicle_id`, `end_reason`, `pickups[]`, `issues[]`, 방문지 `place_key`·`cancel_reason`, 물품 `zone`(rear·middle·front) 이 추가됨.
관측 요청·응답에 선택 `zones: {"rear": {"box": 3}}` (F12).

| 요청 | 규칙 | 오류 코드 |
|---|---|---|
| `POST /trips/{id}/stops` `{name, address, latitude, longitude, place_key?, cargo:[{item_name, kind, qty, zone?}]}` | 기준 확정 전: 계획만 추가. 확정 후: **추가 상차**로 예상 잔량 + (R05) | `trip_already_ended` |
| `POST /trips/{id}/stops/reorder` `{stop_ids}` | 남은(pending) 방문지 전체를 새 순서로. 완료된 곳은 앞에 유지 (F18) | `reorder_must_list_pending_stops` |
| `POST /trips/{id}/stops/{sid}/cancel` `{reason}` | 방문지 취소. 물품은 차에 남음 → 잔량 변화 없음 (F29) | `stop_already_completed` |
| `POST /trips/{id}/stops/{sid}/cargo` `{cargo_line_id, planned_qty}` | 계획 수량 변경. 확정 기준은 바꾸지 않음 (F29) | `cargo_not_found`, `stop_already_completed` |
| `POST /trips/{id}/end` `{reason?}` | 남은 방문지·미반납 회수품·미해결 이상이 있으면 사유 필수 → `ended_incomplete`, 없으면 `completed` (F07·R13) | `end_reason_required`, `trip_already_ended` |
| `POST /trips/{id}/pickups` `{stop_id?, item_name, kind, qty, return_place}` | 회수·추가 상차. 예상 잔량 + (F44·F47). 기준 확정 후만 | `baseline_not_confirmed` |
| `POST /trips/{id}/pickups/{pid}/return` `{}` | 반납 처리. 예상 잔량 − (F46) | `pickup_already_returned` |
| `POST /trips/{id}/issues` `{kind: shift·fall·leak·other, zone?, note, observation_source, observed_at}` | 적재 이상 **의심** 기록. 파손·책임 확정 아님 (F30·F31) | — |
| `POST /trips/{id}/issues/{iid}/resolve` `{resolution: no_issue·rearranged·needs_action, note?}` | 기사 확인·조치 기록 (F34) | `issue_already_resolved` |
| `GET /trips/{id}/history` | `items[]: {type, stop_id, delta, source, actor_id, created_at, summary}` (F64) | — |

운행 완료(`completed`)는 모든 방문지가 처리되고 **미반납 회수품이 없을 때**만 (F48).

## 기사·장비 — 확정 (2026-11-08)

- `GET /drivers` → `{drivers:[{id, name}]}` (샘플 기사 2명)
- `GET /vehicles/{vid}/equipment` → `{equipment:[{id, name, location, status, note, updated_by, updated_at}]}`
- `POST /equipment/{eid}/status` `{status: ok·missing·broken·lent·not_returned, location?, note?}` → 장비 (상태 지정이라 멱등 키 없음)

## 메모 — 확정 (2026-11-08)

개인(`private`) 메모는 작성자만 조회·수정. 공유(`shared`) 메모는 누구나 조회·본문 수정, 수정자 기록. 공유 범위는 작성자만 변경 (R12·F53·F54). 작성에는 `X-Driver-Id` 필수(`422 driver_required`).

- `POST /memos` `{idempotency_key, scope_type: place·vehicle·equipment·trip, scope_id, body, tags[], visibility, persistence: always·this_trip, trip_id?, source}` → `201 {memo, replayed}`
- `PUT /memos/{id}` `{body?, tags?, visibility?}` → 메모 (`404 memo_not_found`, `403 not_author`)
- `GET /memos?scope_type&scope_id&trip_id&q` → 보이는 메모 (q: 본문 검색, F42)
- `GET /trips/{id}/stops/{sid}/memos` → 그 장소의 상시 메모 + 이번 운행 메모 (F50·F55)

## 인계 — 확정 (2026-11-08)

- `GET /trips/{id}/handover/preview` → `{remaining_stops, pickups_loaded, open_issues, equipment_alerts, shared_memos, expected_remaining, summary_lines[], summary_method: "rule_based"}`. **개인 메모 제외** (R12). 요약은 규칙 기반 자동 요약 (F61, AI 모델 미정)
- `POST /trips/{id}/handover` `{idempotency_key, to_driver_id, note}` → `201 {handover, replayed}`. 현재 운행 기사만 (`403 not_current_driver`), 대기 중 인계가 있으면 `409 handover_pending`
- `GET /trips/{id}/handover` → 최근 인계
- `POST /handovers/{hid}/accept` → 인수 대상 기사만(`403 not_handover_target`). 운행 기사 변경, 이력 `handover_accepted` (R13: 인계 확정과 인수 구분)

## 음성 해석 — 확정 (2026-11-08)

`POST /assistant/interpret` `{trip_id, text, pending?}` (헤더 `X-Driver-Id`)
```json
{ "kind": "answer | confirm | clarify | unknown", "speech": "TTS 문장", "display": "화면 문장",
  "action": null | { "type": "add_pickup | add_memo | navigate_next", "payload": {...}, "summary": "..." },
  "missing": ["return_place"], "intent": "...", "method": "rule_based" }
```
- 조회(`answer`)는 즉시 답한다. 변경은 `confirm` 으로 요약만 돌려주고 **서버는 저장하지 않는다.** 앱이 기사 확인 후 해당 API 를 `source: "voice"` 로 호출한다 (R08·F41).
- `clarify` 는 부족한 항목만 묻는다(F40). 다음 발화를 `pending: <이전 action>` 과 함께 보낸다.
- 해석기: `backend/app/integrations/assistant/rule_based.py` (키워드 규칙). 대화 AI 로 교체 가능한 어댑터.

## 예정 (미확정)

대화 AI(LLM) 어댑터, 녹화 영상 분석(vision) 결과 전송. 추가할 때 이 문서부터 고친다.
