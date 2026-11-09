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
- 잔량 비교는 물품 개별 ID 로 하지 않는다. 비교 결과에는 항상 `count_only: true` 를 넣어 **총량 검사임**을 표시한다 (R06). 박스별 자리는 표시용 기록이며(아래 "적재 자리") 비교·완료 판정에 쓰지 않는다.
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

### POST /trips/{trip_id}/stops/{stop_id}/complete — 하차 완료 요청 (R04~R07, 하차 확인 팝업)

```json
요청 { "idempotency_key": "uuid", "delivered": {"box": 3}, "resolution": null | "driver_confirmed" }
응답 200 { "result": "completed | needs_confirmation", "replayed": false,
           "stop": <Stop>, "expected_remaining": {"box": 7}, "comparison": <Comparison> }
```
- 서버는 `이번 하차 반영 후 예상 잔량` 을 최신 관측과 비교한다.
- `match` → 완료 확정 (`resolution: "matched"`).
- 그 외(`mismatch`·`stale`·`unavailable`·`no_observation`)이고 `resolution` 이 없으면 → **`needs_confirmation`, 아무것도 저장하지 않음.** 앱은 뒷문이 닫히면 이 요청을 먼저 보낸다. 수량이 다르면(`mismatch`) 팝업과 음성으로 차이를 알리고 기사가 넘어갈지 고른다. 관측이 없거나 오래됐으면 팝업으로 하차 여부를 묻고, "맞아요"면 `driver_confirmed`로 재전송한다:
  - 남은 물품을 찾아 내림 → 새 관측 기록 후 같은 요청 재전송
  - "이미 다 내렸다" / 확인 불가 상태에서 수동 확인 → `resolution: "driver_confirmed"` 로 재전송 → 완료 확정, 당시 비교 결과를 함께 기록 (R07 근거와 확인 기록 분리)
- 예상 잔량은 0 아래로 내려가지 않는다(실린 짐보다 많은 경로 수량을 완료한 경우).
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
| 샘플 데이터 | `backend/app/seed.py` (A3·B3·C4 박스 10개) |

## 운행 관리·회수·이상·이력 — 확정 (2026-11-08)

공통: 요청 본문에 `idempotency_key`, 선택 `source`(`screen`·`voice`, 기본 screen). 헤더 `X-Driver-Id`(현재 기사, 인증 아님 · F56)는 이력 작성자로 기록.
응답은 모두 `{"trip": <Trip>, "replayed": bool}`. `<Trip>` 에 `driver_id`, `vehicle_id`, `end_reason`, `pickups[]`, `issues[]`, 방문지 `place_key`·`cancel_reason`, 물품 `zone`(rear·middle·front) 이 추가됨.
관측 요청·응답에 선택 `zones: {"rear": {"box": 3}}` (F12).

| 요청 | 규칙 | 오류 코드 |
|---|---|---|
| `GET /places/check?address=` (2~300자) | 기사가 입력한 주소의 위치가 확인되는지 (VWorld 검색 API). 응답 `{"found": bool}`. 좌표·VWorld 주소는 내려 주지 않는다 | `place_search_not_configured`(503), `place_search_failed`(502) |
| `POST /trips/{id}/stops/{sid}/place` `{name, address}` | 남은 방문지의 이름·주소 수정. 짐은 그대로이고 저장된 좌표는 지운다. 내비에는 다시 보내야 반영된다 | `stop_already_completed`, `trip_already_ended` |
| `GET /trips/{id}/route-points?stop_ids=a,b` | 내비 전달 직전 좌표 조회. 저장된 좌표가 있으면 그 값, 없으면 주소로 실시간 조회하고 **DB에 저장하지 않는다** (VWorld 이용약관의 무단 저장 금지). 응답 `{"points":[{"stop_id","latitude","longitude"}], "missing":["stop_id"]}` | `stop_not_found`, `place_search_not_configured`, `place_search_failed` |
| `POST /trips/{id}/stops` `{name, address, latitude?, longitude?, place_key?, cargo:[{item_name, kind, qty, zone?}]}` | 기준 확정 전후 모두 **경로 계획에만 추가**하고 예상 잔량·박스 단위는 바꾸지 않는다(실제 적재는 카메라·기사 확인으로만 바뀐다, 2026-11-10 사용자 결정). 좌표는 둘 다 보내거나 둘 다 생략하고, 생략하면 `address` 필수. `<Trip>` 방문지의 `latitude`·`longitude` 는 없으면 `null` | `trip_already_ended` |
| `POST /trips/{id}/stops/reorder` `{stop_ids}` | 남은(pending) 방문지 전체를 새 순서로. 완료된 곳은 앞에 유지 (F18) | `reorder_must_list_pending_stops` |
| `POST /trips/{id}/stops/{sid}/cancel` `{reason}` | 방문지 취소. 물품은 차에 남음 → 잔량 변화 없음 (F29) | `stop_already_completed` |
| `POST /trips/{id}/stops/{sid}/cargo` `{cargo_line_id, planned_qty}` | 계획 수량 변경. 확정 기준과 박스 단위는 기준 확정 전후 모두 바꾸지 않음 (F29) | `cargo_not_found`, `stop_already_completed` |
| `POST /trips/{id}/end` `{reason?}` | 남은 방문지·미반납 회수품·미해결 이상이 있으면 사유 필수 → `ended_incomplete`, 없으면 `completed` (F07·R13) | `end_reason_required`, `trip_already_ended` |
| `POST /trips/{id}/pickups` `{stop_id?, item_name, kind, qty, return_place}` | 회수·추가 상차. 예상 잔량 + (F44·F47). 기준 확정 후만 | `baseline_not_confirmed` |
| `POST /trips/{id}/pickups/{pid}/return` `{}` | 반납 처리. 예상 잔량 − (F46) | `pickup_already_returned` |
| `POST /trips/{id}/issues` `{kind: shift·fall·leak·other, zone?, unit_id?, note, observation_source, observed_at}` | 적재 이상 **의심** 기록. `unit_id`로 어느 물품인지 지정(없으면 구역까지만, `zone` 생략 시 물품 자리로 채움). 응답 `issues[]`에 `unit_id` 포함. 파손·책임 확정 아님 (F30·F31) | `unit_not_found` |
| `POST /trips/{id}/issues/{iid}/resolve` `{resolution: no_issue·rearranged·needs_action, note?}` | 기사 확인·조치 기록 (F34) | `issue_already_resolved` |
| `GET /trips/{id}/history` | `items[]: {type, stop_id, delta, source, actor_id, created_at, summary}` (F64) | — |

운행 완료(`completed`)는 모든 방문지가 처리되고 **미반납 회수품이 없을 때**만 (F48).

## 적재 자리 — 확정 (2026-11-09, F12·F13·F14)

박스 한 개를 **단위(unit)** 로 기록한다. 3D·2D 화면과 "어디서 꺼낼지" 안내에 쓰며, **잔량·완료 판정에는 쓰지 않는다** (원칙 2). 화면에는 "상차 기록 기준 위치"로 표시하고 실제 위치라고 부르지 않는다.
적재함 격자: `zone` front(앞쪽)·middle(가운데)·rear(뒷문 쪽) × `lane` 0(왼쪽)·1(가운데)·2(오른쪽, 운전석을 바라본 기준) × `layer` 1~3층 = 27칸. 칸이 모자라면 자리 값이 `null` 이고 화면은 "자리 미정"으로 표시한다.

`<Trip>` 에 `units[]` 가 추가된다.
```json
{ "id": 1, "stop_id": "stop-a" | null, "pickup_id": null | 3, "kind": "box", "item_name": "박스", "no": 2,
  "state": "planned | loaded | unloaded", "load_seq": 8,
  "zone": "rear" | null, "lane": 2 | null, "layer": 1 | null,
  "planned_zone": "rear" | null, "planned_lane": 2 | null, "planned_layer": 1 | null,
  "position_source": "sample | manual | recorded_video" | null, "updated_at": "..." }
```
- `planned_*` 는 계획 자리, `zone·lane·layer` 는 기록된 자리. `planned` 상태에서는 기록 자리가 `null`. 둘이 다르면 앱은 "계획과 다른 자리"로 표시한다.
- `state` 변화: 상차 소식 `placed` → `loaded`. 최초 기준 확정 때 아직 `planned` 인 단위는 계획 자리에 `loaded`(출처 `manual`)가 된다. 하차 완료·회수품 반납이 확정되면 해당 단위가 `unloaded` 가 되고, 위층부터 빠지며 남은 단위는 아래로 내려앉는다.
- 방문지 추가·회수는 빈 칸을 자동 배정한다(회수품은 뒷문 쪽 우선). 기준 확정 전의 수량 변경은 단위 수를 맞춘다. 확정 후 수량을 바꿔도 차에 실린 단위는 그대로 둔다.

### POST /trips/{trip_id}/placements — 자리 소식 기록

카메라 분석(vision)·샘플 시뮬레이션·기사 입력이 같은 형식으로 보낸다. 지금은 카메라가 없어 앱의 시연 도구가 `source: "sample"` 로 보낸다.
```json
요청 { "idempotency_key": "uuid", "source": "sample | manual | recorded_video", "observed_at": "...",
       "events": [ { "unit_id": 1, "type": "placed | moved | removed | accepted", "slot": null | { "zone": "rear", "lane": 2, "layer": 1 } } ] }
응답 200 { "trip": <Trip>, "replayed": false }
```
| type | 동작 | 조건 |
|---|---|---|
| `placed` | 놓임. `slot` 생략 시 계획 자리 | `planned` 상태만 |
| `moved` | 옮겨짐. `slot` 필수 | `loaded` 상태만 |
| `removed` | 상차 취소(시뮬레이션 되감기 등). `planned` 로 돌아감 | 기준 확정 전, `loaded` 상태만. **확정 후 물건이 빠지는 것은 하차 완료로만 기록** |
| `accepted` | 기록된 자리를 새 계획 자리로 인정 | `loaded` 상태만 |

여러 건은 순서대로 적용하고 하나라도 실패하면 전부 반영하지 않는다. 요청당 최대 100건.
오류: `404 trip_not_found`, `404 unit_not_found`, `409 trip_already_ended`, `409 idempotency_key_conflict`, `409 unit_state_conflict`, `409 slot_occupied`, `409 placement_locked`, `422 slot_required`.

| 구현 | 위치 |
|---|---|
| 서버 | `backend/app/features/trip/service/placements.py`, 자리 계산 `rules.py` |
| 앱 | `feature/trip/presentation/cargo/` (3D·2D, 시연 도구) |

## 기사·장비 — 앱 미사용 (2026-11-09 차량 노트로 대체. 서버 API 는 보류 상태로 유지)

- `GET /drivers` → `{drivers:[{id, name}]}` (샘플 기사 2명)
- `GET /vehicles/{vid}/equipment` → `{equipment:[{id, name, location, status, note, updated_by, updated_at}]}`
- `POST /equipment/{eid}/status` `{status: ok·missing·broken·lent·not_returned, location?, note?}` → 장비 (상태 지정이라 멱등 키 없음)

## 메모·차량 노트 — 확정 (2026-11-09 개정)

차량 노트는 `scope_type=vehicle` 인 공유 메모다. 누가 타든 같은 내용을 보고 누구나 고친다. 앱이 새로 쓰는 메모는 모두 `shared` 이며 기사 선택이 필요 없다.
개인(`private`) 메모는 작성자만 조회·수정하고 `X-Driver-Id` 필수(`422 driver_required`). 공유 메모는 `X-Driver-Id` 없이 작성·수정·삭제할 수 있고 작성자는 `shared` 로 남는다. 공유 범위는 작성자만 변경 (R12·F53·F54).
`category`: `tool`(도구·물건 위치) · `issue`(차량 문제, 삭제할 때까지 표시) · `place`(장소 정보) · `other`. 생략하면 차량 노트는 AI 가 분류하고, AI 를 쓸 수 없으면 `other`. 장소 메모는 `place`.

- `POST /memos` `{idempotency_key, scope_type: place·vehicle·equipment·trip, scope_id, body, tags[], visibility, persistence: always·this_trip, trip_id?, source, category?, location?}` → `201 {memo, replayed}`
- `PUT /memos/{id}` `{body?, tags?, visibility?, category?, location?}` → 메모 (`404 memo_not_found`, `403 not_author`). 값 지정이라 멱등 키 없음. `location: ""` 은 위치 지움
- `DELETE /memos/{id}` → `{deleted: true}` (`404 memo_not_found`)
- 메모 응답에 `category, location` 포함. 메모에는 해결 상태가 없다 (2026-11-09 제거: 고장·미반납이 끝나면 메모를 삭제한다)
- `GET /memos?scope_type&scope_id&trip_id&q` → 보이는 메모 (q: 본문 검색, F42)
- `GET /trips/{id}/stops/{sid}/memos` → 그 장소의 상시 메모 + 이번 운행 메모 (F50·F55)

## 인계 — 앱 미사용 (2026-11-09 화면 제거. 기획서 F59~F62 범위 재확인 필요. 서버 API 는 보류 상태로 유지)

- `GET /trips/{id}/handover/preview` → `{remaining_stops, pickups_loaded, open_issues, equipment_alerts, shared_memos, expected_remaining, summary_lines[], summary_method: "rule_based"}`. **개인 메모 제외** (R12). 요약은 규칙 기반 자동 요약 (F61, AI 모델 미정)
- `POST /trips/{id}/handover` `{idempotency_key, to_driver_id, note}` → `201 {handover, replayed}`. 현재 운행 기사만 (`403 not_current_driver`), 대기 중 인계가 있으면 `409 handover_pending`
- `GET /trips/{id}/handover` → 최근 인계
- `POST /handovers/{hid}/accept` → 인수 대상 기사만(`403 not_handover_target`). 운행 기사 변경, 이력 `handover_accepted` (R13: 인계 확정과 인수 구분)

## 음성 해석 — 확정 (2026-11-08)

`POST /assistant/interpret` `{trip_id, text, pending?}` (헤더 `X-Driver-Id`)
```json
{ "kind": "answer | confirm | clarify | unknown | ignore", "speech": "TTS 문장", "display": "화면 문장",
  "action": null | { "type": "add_pickup | add_memo | update_memo | delete_memo | navigate_next | arrived", "payload": {...}, "summary": "..." },
  "missing": ["return_place"], "intent": "...", "method": "ai | rule_based", "ai_status": "ok | unavailable | not_configured" }
```
- `arrived` 는 기사가 도착했다고 말한 경우다(예: "도착했어"). `kind: "answer"` 에 action 이 함께 오고 payload 는 `{stop_id}`. **서버는 아무것도 저장하지 않으며 배송 완료와 무관하다.** 앱은 적재 화면을 여는 데만 쓴다. 남은 방문지가 없으면 action 은 `null`.
- 조회(`answer`)는 즉시 답한다. 변경은 `confirm` 으로 요약만 돌려주고 **서버는 저장하지 않는다.** 앱이 기사 확인 후 해당 API 를 `source: "voice"` 로 호출한다 (R08·F41).
- `clarify` 는 부족한 항목만 묻는다(F40). 다음 발화를 `pending: <이전 action>` 과 함께 보낸다.
- 모든 발화를 AI(`integrations/assistant/note_ai.py`, Google Gemini)가 먼저 한 번의 호출로 판단한다(메모 저장·찾기·수정·삭제, 방문지 물품·잔량·회수품 조회, 회수 기록, 내비 요청). 재질문의 답(`pending`)과 AI 가 `other` 로 판단한 말, AI 를 쓸 수 없을 때는 규칙 기반 해석기(`rule_based.py`)가 처리하고 `ai_status` 로 알린다.
- AI 의 답은 서버가 넘긴 사실·메모를 인용할 때만 쓴다. 인용이 없으면 `기록 없음`으로 답하고, 인용에 없는 숫자가 있으면 AI 문장 대신 인용한 사실을 읽는다. 확인 문구는 서버가 만든다.
- `ignore` 는 기사가 앱에 말한 것이 아닌 소음·혼잣말·다른 사람과의 대화다. `speech`·`display` 는 빈 문자열이고 앱은 말하지 않고 다시 듣는다. 애매한 말은 `ignore` 로 보내지 않는다.
- `update_memo` payload `{memo_id, body?, category?, location?}`, `delete_memo` payload `{memo_id}`. 앱이 기사 확인 후 각각 `PUT /memos/{id}`, `DELETE /memos/{id}` 를 호출한다. 문제가 고쳐졌다는 말("끈 새로 샀어")도 `delete_memo` 확인으로 답한다.
- AI 에 보내는 값: 발화 문장, 남은 방문지 라벨·물품·수량·적재 위치, 예상 잔량, 실려 있는 회수품(품목·수량·반납처), 메모 본문·종류·위치. 납품처 이름·주소, 기사 ID 는 보내지 않는다. 메모 본문과 반납처에 직접 적힌 내용은 그대로 전달된다.

## 예정 (미확정)

녹화 영상 분석(vision) 결과 전송. 추가할 때 이 문서부터 고친다.
