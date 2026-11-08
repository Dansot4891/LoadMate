# Agent instructions — Backend

`backend/` 작업 전에 루트 `../AGENTS.md`, `../CLAUDE.md`, 이 디렉터리의 `CLAUDE.md`를 읽고 따른다. 서버 구조, 기능 간 의존, 멱등 처리, DB 마이그레이션 및 검증 명령의 상세 기준은 `CLAUDE.md`가 관리한다.

새로 만들거나 수정하는 코드는 `CLAUDE.md`의 목표 구조를 따른다. 이행 현황은 `../docs/next-steps.md`에서 확인한다. API 계약을 바꿀 때는 `../docs/api-contract.md`를 먼저 갱신하고, 작업 후 검증 결과를 `../docs/next-steps.md`에 기록한다.
