# Loadmate

다지점 배송 기사가 차량 화면과 음성으로 적재 상태를 확인하며 업무를 이어가는 Pleos Connect 물류 동반자 앱.

> 개발 중 (2026-11-08 뼈대). 현재 동작: 앱 ↔ 서버 연결 상태 확인. 업무 기능은 미구현.

## 구성
| 폴더 | 내용 |
|---|---|
| `android/` | Kotlin · Jetpack Compose 차량 앱 (Pleos Connect, minSdk 28) |
| `backend/` | FastAPI · PostgreSQL · Alembic |
| `vision/` | 적재 영상 분석 (예정) |
| `docs/` | [다음 작업](docs/next-steps.md) · [기획서 대조 검토](docs/planning-review.md) · [구조와 실행](docs/architecture.md) · [API 계약](docs/api-contract.md) |
| `apk/` | release APK (`Loadmate_Loadmate_release.apk`, 예정) |

## 실행
자세한 내용: [docs/architecture.md](docs/architecture.md)

```bash
docker compose up --build                       # 서버 + DB → http://localhost:8000/health
cd android && ./gradlew :app:assembleDebug      # 앱 → Pleos Connect Emulator v2.1.0에 설치
```

## 테스트 환경
- Android Studio 2024.2.1, Pleos Connect Emulator v2.1.0 (Android 14)
- Pleos SDK: Vehicle 2.3.2, NaviHelper 3.0.5 (연결 예정)
- 에뮬레이터 결과는 실차 지원을 의미하지 않습니다.
