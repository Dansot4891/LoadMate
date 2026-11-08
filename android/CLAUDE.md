# android/ — Loadmate 차량 앱 (Kotlin · Compose · AAOS)

앱 구조·설계·반드시 지킬 규칙만 적는다. **진행 상황·이행 중인 작업은 여기에 쓰지 않는다** → `../docs/next-steps.md`
공통 원칙(API 계약 먼저, 관측≠확정, 샘플 표시 등)은 루트 `../CLAUDE.md`.

> 2026-11-08 구조 결정: Hangari(Flutter)식 화면 폴더 + DTO/mapper, DI는 `AppContainer` 유지.
> 아직 코드가 이 구조로 옮겨지지 않은 곳이 있다(이행 현황은 next-steps). **새로 만들거나 고치는 코드는 이 문서 구조를 따른다.**

## 명령

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 shell am start --user 10 -n com.loadmate.app/.MainActivity   # AAOS 사용자 10
```

- **AGP 8.7.3 고정** (Android Studio 2024.2.1). Kotlin 2.0.0, compileSdk 35, targetSdk 34, minSdk 28. 올리지 않는다
- release 서버 주소: `./gradlew :app:assembleRelease -Ploadmate.apiBaseUrl=https://...` (미지정 시 앱에 "서버 미설정" 표시)

## 구조

`presentation → domain ← data`. 패키지 루트 `app/src/main/java/com/loadmate/app/`

```
app/
├── LoadmateApplication.kt, MainActivity.kt
├── AppContainer.kt          # 수동 DI. 구현체 연결은 여기서만
├── core/
│   ├── designsystem/        # Pleos 토큰·타이포·PleosButton 등 (출처·PROJECT 표시 유지)
│   ├── network/             # ApiClient, ApiResult, ApiError (모든 기능 공용)
│   ├── pleos/               # Pleos SDK 경계: XxxGateway 인터페이스 + PleosXxxGateway 구현
│   ├── session/             # DriverStore (현재 기사)
│   └── notify/              # DriverNotifier (헤드업 알림)
└── feature/<name>/          # trip, records, fleet, assistant, settings ...
    ├── domain/              # 모델 + XxxRepository 인터페이스. Android·HTTP·Pleos 의존 금지
    ├── data/
    │   ├── dto/             # @Serializable 서버 응답/요청 (필드명 = 서버 그대로, @SerialName)
    │   ├── mapper/          # fun XxxDto.toDomain(): Xxx  (DTO → 도메인 변환은 여기서만)
    │   └── XxxRepositoryImpl.kt
    └── presentation/
        ├── <screen>/        # 화면 하나 = 폴더 하나 (예: trip/presentation/loadcheck/)
        │   ├── XxxScreen.kt       # ViewModel 상태 구독·액션 전달만
        │   ├── XxxUiState.kt
        │   ├── XxxUiAction.kt
        │   ├── XxxUiEffect.kt     # 일회성 이동·알림이 있을 때만
        │   ├── XxxViewModel.kt
        │   └── components/        # 이 화면 전용 조각
        └── shared/          # 이 기능의 여러 화면이 같이 쓰는 조각
test/                        # main 과 같은 패키지 경로
```

- 패키지 이름은 소문자 붙여쓰기 (`loadcheck`, `sitework`). 밑줄 쓰지 않는다
- 빈 폴더·계층을 미리 만들지 않는다. 화면이 하나뿐인 기능도 `presentation/<screen>/` 형태는 지킨다

## Presentation (MVVM + UiState/UiAction)

- ViewModel은 `StateFlow<XxxUiState>` 하나를 노출하고 `fun onAction(a: XxxUiAction)` 하나로 입력을 받는다
- **`XxxScreen` 만 ViewModel을 안다.** `components/` 의 Composable은 상태 값과 콜백(람다)만 받는다. ViewModel·Repository·Gateway를 파라미터로 받지 않는다
- 화면 파일에는 Screen Composable과 그 배치만 둔다. 카드·목록 항목·대화상자 같은 조각은 `components/` 로 뺀다
- 일회성 이벤트(탭 이동, 화면 전환)는 `XxxUiEffect` 를 `SharedFlow` 로 내보내고 Screen 이 `LaunchedEffect` 로 받는다
- 문구·판정 텍스트를 만드는 순수 함수는 ViewModel 밖 `internal fun` 으로 두고 단위 테스트한다
- 색·간격·글꼴은 `Pleos.colors` / `Pleos.type` / `PleosSpacing` 만 쓴다. **화면에서 hex 직접 사용 금지**

### 여러 화면이 같은 운행을 볼 때

- 운행 탭은 화면(개요·적재 확인·주행·현장 작업·불일치)별로 ViewModel을 나눈다. **ViewModel끼리 서로 참조하지 않는다**
- 현재 운행 데이터는 data 계층(`TripRepository` 구현)이 `StateFlow` 로 보관하고, 각 ViewModel은 구독만 한다. 상태 변경 후 서버 응답으로 그 Flow를 갱신한다
- 다른 탭(음성·기록·인계)에서 운행이 바뀌면 같은 Flow 갱신으로 전달한다 (별도 이벤트 버스를 늘리지 않는다)
- **내비 리스너는 화면 ViewModel이 아니라 운행 범위 객체가 소유한다.** 운행 시작~종료 동안 유지하고 운행 종료 시 `release()` (2026-11-08 결정: 내비가 화면을 덮어 Loadmate가 ON_STOP 되어도 경로 결과를 받아야 함)

### 화면 전환

- 첫 화면 = 운행 선택(오늘 운행 목록) → 운행을 고르면 탭 화면(운행 · 기록·메모 · 장비·인계 · 연결 상태)
- 화면 전환 상태는 `MainActivity` 의 최상위 Composable 한 곳에서 관리한다. 내비게이션 라이브러리를 들이면 이 문서를 고친다

## Domain

- 순수 Kotlin. `android.*`, `org.json`, `kotlinx.serialization`, Pleos 타입을 import 하지 않는다
- UseCase는 **여러 Repository를 조합하는 규칙이 생길 때만** 만든다. Repository 호출 한 줄짜리 UseCase 금지
- 잔량·완료 판정은 서버가 최종 기준. 도메인 모델은 서버 확정값과 앱 임시 표시값을 다른 필드로 구분한다

## Data

- 서버 호출은 `core/network/ApiClient` 를 거친다 (`X-Driver-Id` 헤더, 오류 `{"detail":{"code","message"}}` → `ApiError.Http`)
- 반환은 `ApiResult<T>` (`Success` / `Failure(ApiError)`). 예외를 화면까지 던지지 않는다
- DTO는 서버 응답 모양 그대로, 도메인 모델은 화면이 쓰기 좋은 모양으로. 변환은 `mapper/` 에서만
- 실패 시 샘플·데모 데이터로 바꾸지 않는다. 캐시를 보여 줄 때는 "오프라인 · 마지막 수신 시각"으로 구분한다
- **상태 변경 요청에는 멱등 키(요청 ID)를 붙이고, 서버 반영이 확정될 때까지 같은 키로 재시도한다**
- 가짜(Fake) Repository는 `test/` 에만 둔다. 앱 코드에서 mock 구현으로 전환하는 경로를 만들지 않는다

## 기능 사이 의존

- 다른 기능의 `domain` 만 import 할 수 있다. 다른 기능의 `data`·`presentation` import 금지
- 기능을 섞어 보여 주는 조립(탭, 음성 결과로 운행 탭 이동 등)은 `MainActivity` 쪽에서 한다

## DI — AppContainer

- 구현체 생성·연결은 `AppContainer` 에서만 (`by lazy`). 화면·ViewModel은 인터페이스만 받는다
- ViewModel은 `companion object { fun factory(container: AppContainer, ...) }` 로 만든다
- 테스트에서는 생성자에 Fake를 직접 넣는다 (시간 `now`, 요청 ID `newKey` 도 주입 가능하게)

## Pleos SDK (`core/pleos`)

- SDK 객체·타입을 화면·도메인에 노출하지 않는다. `XxxGateway` 인터페이스(앱 용어의 상태·이벤트) + `PleosXxxGateway` 구현
- 권한은 실제 호출하는 API의 것만 Manifest에 선언. `PRIVATE_*` 금지. 새 API를 연결하면 Manifest와 `docs/planning-review.md` 갱신
- 리스너는 화면 이탈·release 전에 해제한다. 예외: 내비 리스너는 운행 범위 (위 참고)
- 에뮬레이터에서만 확인한 동작은 화면·문서에 "에뮬레이터 확인"으로 표시한다. 실차 지원으로 쓰지 않는다

### 실측으로 알게 된 제약 (2026-11-08, 상세 `docs/next-steps.md` 10절)

- Vehicle 2.3.2: 차량 속도 API 없음, Gear deprecated, DrivingMode 에뮬레이터 미지원 → 주행/정차·도착은 **기사 확인 버튼**이 기본
- NaviHelper `requestRoute` 시 내비 앱이 메인 패널을 덮어 Loadmate가 ON_STOP. 목적지 전달은 기사 확인 후, 실패 시 실패 표시·다시 보내기·목적지 정보 유지
- 경유지는 2개까지(`requestRouteWithWaypoints`). 탐색 실패 시 기존 안내도 사라진다
- 헤드업 알림은 카테고리 지정 필수 (`CATEGORY_MESSAGE` / `CATEGORY_NAVIGATION`)
- TTS는 `speak(text, isFinal = true)`. STT ON_DEVICE는 자격 증명 없이 동작하지만 오인식이 있다 → 음성 명령은 인식 문장을 보여 주고 기사 확인 후 실행
- Loadmate 음성은 `NaviAwareSpeechQueue` 를 거친다(내비 음성 중 대기, 끼어들면 멈추고 재생). 길 안내·안내 시작 음성은 내비에 맡기고 Loadmate는 업무·실패·기사 질문 답변만 말한다
- 호스트 마이크: `adb emu avd hostmicon`

## 테스트

- `app/src/test/` 에 main 과 같은 패키지로 둔다. JVM 단위 테스트(`kotlinx-coroutines-test`)
- 우선순위: ViewModel(Fake Repository·Gateway 주입), mapper/파서, 판정·문구 함수
- 완료 기준은 빌드 + 단위 테스트 + 에뮬레이터 연속 동작. 결과는 `docs/next-steps.md` 검증 기록에 남긴다
