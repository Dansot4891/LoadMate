# backend/ — Loadmate API (FastAPI · SQLAlchemy 2 · Alembic · PostgreSQL)

서버 구조·설계·반드시 지킬 규칙만 적는다. **진행 상황·이행 중인 작업은 여기에 쓰지 않는다** → `../docs/next-steps.md`
공통 원칙은 루트 `../CLAUDE.md`. API 모양은 **`../docs/api-contract.md` 를 먼저 고친 뒤** 구현한다.

> 2026-11-08 구조 결정: 모든 기능을 router·schemas·service·repository·models 5파일로 통일, 샘플 데이터는 기능 밖 `app/seed.py`.
> 아직 이 구조로 옮겨지지 않은 기능이 있다(이행 현황은 next-steps). **새로 만들거나 고치는 코드는 이 문서 구조를 따른다.**

## 명령

```bash
# 저장소 루트에서
docker compose up -d --build                     # backend + db. 시작 시 alembic upgrade + 샘플 시드(없을 때만)
curl -s localhost:8000/health                    # /docs 에 OpenAPI
docker compose exec backend python -m app.seed --reset    # 샘플 초기화(시연 반복)

# 테스트 (시스템 Python 3.9 → Docker 이미지에 마운트해서 돌린다)
docker run --rm -v "$PWD/backend:/srv" -w /srv loadmate-backend sh -c "pip install -q -r requirements-dev.txt && pytest -q"

# 마이그레이션
docker compose exec backend alembic revision --autogenerate -m "<무엇>"
docker compose exec backend alembic upgrade head
```

## 구조

```
app/
├── main.py                 # create_app(). 기능 라우터 등록은 여기서만
├── seed.py                 # 샘플 데이터 (모든 기능의 모델을 아는 유일한 곳). data_source="sample"
├── core/
│   ├── config.py           # Settings (.env)
│   ├── db.py               # Base, get_session
│   ├── errors.py           # AppError → {"detail": {"code", "message"}}
│   ├── actor.py            # X-Driver-Id → 현재 기사 (인증 아님, 사용자 선택)
│   └── time.py             # utcnow, as_utc, 응답 시각 직렬화 (기능마다 따로 만들지 않는다)
├── features/<name>/        # health, fleet, trip, memo, handover, assistant
│   ├── router.py           # HTTP만: 경로·Depends·service 호출·응답 모델
│   ├── schemas.py          # Pydantic 요청(XxxIn)·응답(XxxOut)
│   ├── service.py          # 업무 규칙·트랜잭션(commit). AppError 발생
│   ├── repository.py       # SQLAlchemy 조회·추가만. commit 하지 않는다
│   └── models.py           # ORM 테이블
└── integrations/<name>/    # 외부 연동 어댑터 (대화 해석, 영상 결과, 외부 배송 시스템)
alembic/versions/           # 스키마 변경 이력
tests/                      # SQLite 메모리 DB + seed
```

- 큰 기능은 `service.py` 대신 `service/` 패키지로 나눈다. **trip**: `service/{trips,stops,loading,pickups,issues}.py` + 공통 `service/common.py`
  - trips: 목록·조회·운행 종료·이력 / stops: 방문지 추가·순서·취소·수량 / loading: 관측·기준 확정·하차 완료·비교 / pickups·issues: 회수·이상
- 순수 계산 규칙(예상 잔량·비교)은 `rules.py` 처럼 DB 없는 모듈로 두고 단위 테스트한다
- health 처럼 DB 업무가 없는 기능은 router 만 둔다. 그 외에는 5파일을 모두 둔다 (작아도 router에 규칙·쿼리를 넣지 않는다)

## 계층 규칙

- **Router**: `Depends(get_service)` 로 service를 받고 결과를 그대로 반환. SQLAlchemy·업무 판단을 쓰지 않는다
- **Service**: 생성자 `XxxService(session, now=utcnow)`. 트랜잭션 경계(`commit`/`rollback`)는 service 에만. 응답 스키마로 변환해 반환
- **Repository**: `XxxRepository(session)`. `select`·`add`·잠금(`with_for_update`)만. 업무 판단 없음
- **Schemas**: 요청 `XxxIn`, 응답 `XxxOut`. 시각은 UTC `YYYY-MM-DDTHH:MM:SSZ`
- 업무 오류는 `AppError(status, code, message)`. code 는 snake_case 영어, message 는 화면에 그대로 보일 한국어. 새 code 는 api-contract 에 먼저 적는다

## 기능 사이 의존 (한 방향만)

```
fleet  ←  trip  ←  memo  ←  handover  ←  assistant
```

- 오른쪽 기능만 왼쪽 기능을 참조한다. **역방향·순환 import 금지**
- 다른 기능의 데이터가 필요하면 그 기능의 **repository 또는 service 공개 메서드**를 호출한다. 다른 기능의 models 로 직접 쿼리하지 않는다
- 모든 기능의 모델을 함께 다루는 것은 `app/seed.py` 와 `tests/conftest.py` 뿐

## 상태 변경 규칙

- 상태를 바꾸는 POST 는 body 에 `idempotency_key` 를 받는다. 나머지 필드로 `request_hash` 를 만든다
  - 같은 키 + 같은 내용 → 저장된 결과를 `replayed: true` 로 다시 돌려준다
  - 같은 키 + 다른 내용 → 409 `idempotency_key_conflict`
  - DB 에 (범위, 키) 유니크 제약을 둔다. 동시 요청은 `IntegrityError` → rollback 후 재조회
- 같은 운행을 바꾸는 요청은 운행 행을 `with_for_update` 로 잠근 뒤 처리한다
- 값 지정형(장비 상태 = X)처럼 다시 보내도 결과가 같은 요청은 멱등 키를 생략할 수 있다. 그 이유를 docstring 에 적는다
- 관측(카메라·AI·샘플)만으로 배송 완료를 확정하지 않는다. 확정은 기사 확인 또는 관측 일치 규칙을 거친 이벤트로만
- 개인 기록(`visibility="private"`)은 작성자 외에게 조회·요약·인계에 포함하지 않는다

## DB · 마이그레이션

- 모델을 바꾸면 Alembic autogenerate → 생성된 파일을 읽고 고친 뒤 커밋. 기존 마이그레이션 파일은 고치지 않는다
- 새 기능 모델은 `alembic/env.py` 와 `tests/conftest.py` 의 모델 import 에 추가한다
- 테스트 DB는 SQLite 라 tz 가 사라진다 → 읽은 시각은 `core/time.as_utc` 로 다룬다

## Integrations

- 외부·AI 연동은 `integrations/<name>/` 에 어댑터로 두고 service 는 인터페이스만 호출한다 (예: `assistant/note_ai.py` Gemini 어댑터, `assistant/rule_based.py` 규칙 기반)
- 규칙 기반·샘플 결과는 응답에 출처(`source`/`data_source`)를 실어 앱이 그렇게 표시하게 한다. AI 라고 부르지 않는다

## 테스트

- `tests/` 에서 `client` 픽스처(TestClient + SQLite + seed) 사용. 기능별 `test_<feature>.py`
- 규칙 모듈은 DB 없이, API 는 TestClient 로. 멱등(같은 키 재전송·다른 내용 409)과 권한(개인 기록)은 반드시 테스트
- 완료 기준은 pytest + PostgreSQL(docker compose) 실제 실행·재시작 후 복원. 결과는 `docs/next-steps.md` 검증 기록에 남긴다
