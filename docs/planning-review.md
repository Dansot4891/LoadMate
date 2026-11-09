# 기획서 대조 검토 — Pleos 물류 동반자 v2.1

- 대상: `Pleos_물류동반자_기획개발안.docx` (버전 2.1, 2026-11-05). 기획서 원본은 저장소에 넣지 않음.
- 검토일: 2026-11-08
- 근거 구분: **공식 문서**(document.pleos.ai, appdevchallenge.auto-hmg.io 당일 조회) / **SDK 바이너리**(Vehicle 2.3.2 바이트코드 추출) / **에뮬레이터 확인**(PleosLab, Pleos Connect Emulator v2.1.0). 에뮬레이터 결과는 실차 지원을 뜻하지 않는다.

## 1. 공모전 일정과 제출물 (공식 문서, 2026-11-08 조회)

| 날짜 (KST) | 내용 |
|---|---|
| 2026-11-15 (일) | 접수 마감 |
| 2026-11-23 (월) | 1차 서류 결과 |
| 2026-12-03 ~ 04 | 2차 PT 심사 (오프라인). **PT 전까지 실제 동작 가능한 앱 개발 완료** |
| 2026-12-18 | 최종 선정 발표 |
| 2027-02 | 현대자동차그룹 행사 전시·시상. 선정 10팀 **최종 구동용 APK 필수** |

- 필수: 접수양식(기획서). 선택(가산점): 시연 영상(목업·화면 녹화 인정), GitHub 저장소(소스+APK).
- APK: 실행 가능한 **release** 버전, 저장소 `apk/` 폴더, 파일명 `팀명_앱이름_release.apk`. 용량 초과 시 GitHub Releases 링크.
- 비공개 저장소는 심사용 계정을 Collaborator로 초대(쓰기 권한 포함).
- README 필수 항목: 앱 이름·한 줄 소개, 실행 방법, 테스트 환경·필요 설정, APK 위치 또는 Release 링크.
- 1차 평가 항목(배점 비공개): 차량특화성, 창의성, 기술 활용성(Pleos SDK·API를 핵심 기능에 활용), 사업성. 2차 기준은 추후 공지.
- 개발 환경(공모전 가이드): Android API 28+, Gradle 8.0+, Java/Kotlin.
- 규정 페이지에 **외부 서버·외부 AI API·카메라·음성에 대한 제한은 명시되어 있지 않음.** 불확실하면 /guide/help 로 문의.

출처: https://appdevchallenge.auto-hmg.io/guide/schedule · /guide/submission · /guide/rules · /guide/judging-awards · /guide/development

## 2. SDK 콘솔 상태 (공식 문서 「프로젝트 생성 및 SDK 사용 신청」, 2026-11-08 조회)

| SDK | 콘솔 상태 |
|---|---|
| Vehicle State, NaviHelper | **사용 가능** (신청 불필요) |
| FusedLocation, TextToSpeech, SpeechToText, Vehicle Control | **준비 중** (신청 버튼 없음) |
| LLM | Emulator v2.1.0 릴리스 노트상 제공 종료 |

출처: https://document.pleos.ai/docs/pleos-only/create-my-project

## 3. 기능별 판정

| 기획서 기능 | 필요 API | 확인 결과 | 판정 |
|---|---|---|---|
| F16~F18 내비 목적지·경유지 전달 | NaviHelper `requestRoute`, `requestRouteWithWaypoints`, `addWaypoint` 등 | 공식 문서: **`NAVI_ROUTE_SEARCH`** 권한 필요(`requestTrip`·`get*Info`는 `NAVI_ROUTE`, `requestNavigationScreen`은 `NAVI_CUSTOM_MAP`). **에뮬레이터(2026-11-08): `requestRoute` → 내비 앱이 메인 패널을 차지하고 안내 시작, `onRouteStarted` 수신. 실패는 `onError(CAUSE_SEARCH_ROUTE)`이며 기존 경로도 취소됨. `onRouteStarted` 직후 `onRouteEnded` 가 같이 옴** | ✅ 목적지 전달 가능(에뮬레이터 확인) · **경유지 2개 + 목적지 한 번에 전달 확인** (`requestRouteWithWaypoints`) · 결과 수신을 위해 운행 중 리스너 유지 필요 |
| F19 도착 판단 · F63 주행/정차 맥락 (내비 `onWayPointArrived`·`onDestinationArrived` 는 에뮬레이터에서 위치 이동 불가로 미확인) | 차량 속도, 기어, 주행 상태 | SDK 바이너리: Vehicle 2.3.2 공개 API에 **차량 속도 없음**, Gear 3개 메서드 **모두 deprecated**. DrivingMode는 에뮬레이터에서 `ERROR_NOT_SUPPORTED_PROPERTY` | ⚠️ 신호 기반 판단 불가 → **기사 확인 버튼(기획서 16장 대체 흐름)을 기본**으로 |
| 하역 이벤트 (기획서에 없음 · **추가 제안**) | Door `REAR` 위치 (`CAR_DOORS`) | 에뮬레이터: `checkDoorPositionCapabilities` REAR **0..100**. Loadmate 구독 실측(2026-11-08): Virtual Console Trunk Open/Close → 100/0 알림, 화면 이탈 시 해제 확인 | ✅ 뒷문 열림/닫힘을 하역 시작·재출발 확인 트리거로 사용 제안 (차량특화성) |
| 주행 중 알림 (기획서 11장) | Android 알림 | 에뮬레이터: `CATEGORY_MESSAGE`/`CATEGORY_NAVIGATION` 지정 시 내비 위 헤드업 표시, 미지정 시 안 뜸 | ✅ 짧은 안내에 사용 (카테고리 필수) |
| 위치 맥락 | FusedLocation | 콘솔 준비 중 | ❌ → NaviHelper `getCurrentLocationInfo` (에뮬레이터 콜백 확인) |
| 보조 신호 | BrakePedal, Odometer 주행 거리·시간 (`CAR_DRIVE`) | 에뮬레이터 GET 동작 (BrakePedal=false 등) | 보조 근거로만 사용 가능 |
| F32, F35~F43 음성 | SpeechToText / TextToSpeech | 콘솔 준비 중. 공식 문서: STT **ON_DEVICE 모드는 앱 등록(Client ID/Secret) 없이 사용 가능**, HYBRID·SERVER는 등록 필요. **에뮬레이터(2026-11-08): 서비스는 `ai.pleos.playground.caas` 4.2.10. ON_DEVICE initialize·한국어 인식 성공(자격 증명 없음). 오인식("에이 하차"→"내일 하체")과 주변 소음 확정 사례 있음.** **TTS ON_DEVICE도 자격 증명 없이 재생. 앱이 백그라운드(내비 전면)여도 STT·TTS 동작** | ✅ 앱에 연결(2026-11-08): 음성 패널 STT → 서버 규칙 기반 해석 → TTS 응답, 변경은 확인 후 저장, 직접 입력 병행. 실차·콘솔 승인 미확인 |
| F08~F15 적재 공간 카메라 | 적재함 카메라 | Pleos SDK에 카메라 API 없음 | ⚠️ 기획서대로 별도 카메라/녹화 영상 → vision 프로세스. 시연에서 "녹화 영상 분석"으로 표시 |
| 대화 AI | — | Pleos LLM 제공 종료 | 서버 `integrations/assistant`의 Gemini 어댑터가 먼저 판단하고(키 필요, 실제 호출 미검증) 키가 없거나 끊기면 규칙 기반 해석기로 동작. 화면에 어느 쪽인지 표시 |

## 4. 서버 · 데이터 저장

- 서버 필요: 운행 계획, 확정 기록, 관측 이력, 회수, 메모, 인계, 공유 범위 → FastAPI + PostgreSQL (기획서 확정 사항).
- 차량 데이터 자체는 최소 저장: 뒷문·정차 같은 신호는 **업무 이벤트의 근거**로만 기록. VIN·주행 궤적은 저장하지 않는다(개인정보·위치정보 리스크).
- 영상: 근거 장면 위주 보관, 보관 기간·삭제 방식·외부 AI 전송 여부를 구현 시 명시(기획서 17장).
- **현장 시연 리스크:** 기획서는 로컬 PC 서버 기준. 2월 현장 시연용 release APK가 서버에 접근할 수 없으면 동작하지 않는다. PT 전까지 (a) 클라우드 배포 또는 (b) 앱 내 데모 모드(화면에 명확히 표시, 실제 결과와 혼동 금지) 중 결정 필요.
- 앱 권한(2026-11-08): `INTERNET`(서버), `NAVI_ROUTE_SEARCH`(requestRoute·cancelRoute), `CAR_DOORS`(Door REAR), `STT_SERVICE`(SpeechToText ON_DEVICE). 에뮬레이터 → 개발 PC는 `http://10.0.2.2:8000`, debug 빌드만 cleartext 허용.

## 5. 기획서 수정 제안

1. 17장 "Vehicle·FusedLocation으로 주행/정차·위치 맥락 활용" → 현재 불가. **NaviHelper 위치 + 뒷문(REAR) 신호 + 기사 확인 버튼**으로 수정.
2. 17장 "STT·TTS는 음성 입출력에 사용" → 콘솔 준비 중임을 명시하고, ON_DEVICE 시험 결과에 따른 대체 방식(화면 입력 등) 병기.
3. 뒷문 신호를 하역 이벤트로 사용하는 내용 추가 → 차량특화성 설명 강화.
4. 66개 기능은 최종 범위로 유지하되, 제출본에는 **실제 구현 / 에뮬레이터 신호 / 녹화 영상 / 샘플 데이터 / 미구현**을 구분 표기(기획서 19장 원칙).

## 6. 일정 제안 (11/15 마감 기준)

| 기간 | 목표 |
|---|---|
| 11/8~9 | 저장소 뼈대 ✅, Docker로 backend+DB 실행 ✅, 에뮬레이터에서 STT ON_DEVICE · NaviHelper `requestRoute` · Door REAR 실측 ✅ (결과: next-steps.md "실측 결과") |
| 11/10~12 | 대표 시나리오(14장) 연결: 샘플 목록 수신 → 최초 적재 확정 → 내비 전달 → A 하차 → 예상 7 / 관측 8 불일치 → 정정 → B 안내. 관측값은 녹화 분석 또는 샘플 값(표시) |
| 11/13~14 | 시연 영상 녹화, README·release APK, 기획서 수정 반영 |
| 11/15 | 제출 |
| ~12/3 | 회수·메모·인계(15장), 음성 확장, 서버 배포/데모 모드 결정 |

## 7. 남은 확인 항목

- [x] 에뮬레이터 STT ON_DEVICE 동작 여부 — 동작(자격 증명 없이), 오인식 있음 (2026-11-08)
- [x] NaviHelper `requestRoute` 에뮬레이터 동작 (`NAVI_ROUTE_SEARCH`) — 동작, 실패 코드 `CAUSE_SEARCH_ROUTE` (2026-11-08)
- [x] Door REAR 구독 — 동작, 범위 0..100 (2026-11-08)
- [x] 경유지 API (`requestRouteWithWaypoints`) — 경유지 2개+목적지 동작 (2026-11-08)
- [ ] NaviHelper `onWayPointArrived`·`onDestinationArrived` — 에뮬레이터에서 내비 위치를 움직일 방법 없음. 실차 또는 다른 방법 필요
- [ ] 운행 중 내비 리스너 유지 방식 결정 (화면 단위 해제 시 결과 수신 불가)
- [x] TextToSpeech 에뮬레이터 동작 — ON_DEVICE 재생 (2026-11-08)
- [x] 내비 실행 중 앱 백그라운드 음성·알림 가능 범위 (기획서 11장) — 헤드업 알림(카테고리 필요)·TTS·STT 동작 (2026-11-08)
- [ ] 외부 서버·외부 AI 사용 가능 여부 공모전 문의
- [ ] 현장 시연 환경(기기·네트워크) — 공지 대기
