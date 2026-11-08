# Loadmate

다지점 배송 기사가 차량 화면과 음성으로 적재 상태를 확인하며 업무를 이어가는 Pleos Connect 물류 동반자 앱.

> 개발 중. 샘플 운행 A→B→C의 적재 확인·내비 전달·하차 불일치 확인·회수·메모·장비 인계가 앱과 서버에 연결되어 있습니다. 운행·적재 관측은 샘플, 음성 해석·인계 요약은 규칙 기반이며 Pleos SDK 동작은 에뮬레이터에서만 확인했습니다.

## 구성
| 폴더 | 내용 |
|---|---|
| `android/` | Kotlin · Jetpack Compose 차량 앱 (Pleos Connect, minSdk 28) |
| `backend/` | FastAPI · PostgreSQL · Alembic |
| `vision/` | 적재 영상 분석 (예정, 녹화 영상 미연결) |
| `docs/` | [다음 작업](docs/next-steps.md) · [기획서 대조 검토](docs/planning-review.md) · [구조와 실행](docs/architecture.md) · [API 계약](docs/api-contract.md) |
| `apk/` | release APK (`Loadmate_Loadmate_release.apk`, 예정) |

## 실행
자세한 내용: [docs/architecture.md](docs/architecture.md)

실행 전에 Docker 엔진을 켜고, 저장소 루트에서 서버를 시작합니다. 샘플 운행은 현재 **기준 10 확정·A/B/C 미완료** 상태입니다. `app.seed --reset`은 기록과 일부 메모도 삭제하므로 시연 준비 시에만 실행하세요.

```bash
docker compose up -d --build                    # 서버 + DB → http://localhost:8000/health
cd android
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
# Pleos Connect Emulator v2.1.0 (AAOS 사용자 10)
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 shell am start --user 10 -n com.loadmate.app/.MainActivity
```

## 테스트 환경
- Android Studio 2024.2.1, Pleos Connect Emulator v2.1.0 (Android 14)
- Pleos SDK: Vehicle 2.3.2, NaviHelper 3.0.5, SpeechToText·TextToSpeech 3.3.1 (에뮬레이터 연결 확인)
- 에뮬레이터 결과는 실차 지원을 의미하지 않습니다.
- 서버는 Docker에서 pytest 61개, 앱은 JVM 단위 테스트 31개를 통과했습니다(2026-11-08).

## 제출 APK 준비

제출용 서명 APK는 아직 생성하지 않았습니다. 빌드할 때 `-Ploadmate.apiBaseUrl=https://서버주소`를 지정해야 합니다. 이 주소는 차량 또는 에뮬레이터에서 접근 가능해야 하며, 비워 두면 앱에 서버 미설정으로 표시됩니다. Android Studio의 **Generate Signed Bundle / APK**에서 APK를 선택해 서명하고, 결과물을 `apk/Loadmate_Loadmate_release.apk`에 복사합니다. 서명 키와 암호는 저장소에 넣지 않습니다. 팀명과 서버 주소를 확정한 뒤 실제 설치·서버 연결·대표 시나리오를 검증해야 제출 가능한 상태입니다.

## 현재 범위와 다음 작업

기능별 구현·샘플·미구현 구분은 [기능 현황](docs/feature-status.md), 최신 실행 상태와 다음 작업은 [인수인계](docs/next-steps.md)를 확인하세요. 영상 분석, 오프라인 디스크 저장, 메모 사진 첨부 등은 남아 있습니다. release APK는 아직 만들지 않았습니다.
