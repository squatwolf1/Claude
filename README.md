# 꼬빵

두 사람이 함께 쓰는 달력형 가계부 안드로이드 앱 (Kotlin + Jetpack Compose + Firebase).

- 월별 달력에 날짜별 수입(파랑)·지출(빨강), 위쪽에 수입·지출·합계
- 날짜를 누르면 그날 내역 카드, 길게 누르면 삭제
- `+` 로 내역 추가 (지출/수입, 금액, 분류, 메모, 결제수단). **🔁 매달 고정**을 켜면 매월 같은 날 자동으로 들어감
- 고정 내역을 길게 누르면 '이번 달만 삭제' / '이번 달부터 고정 그만'
- 통계 버튼 → 이번 달 분류별 지출 도넛 그래프(범례), 날짜별 지출 막대 그래프(막대를 누르면 그날 금액), 분류별 순위
- 글꼴은 폰에서 설정한 시스템 글꼴을 따라감
- 통계 맨 위 🐷 저금통: 지출에서 '부동저금'·'유동저금' 분류로 넣은 돈이 모이고(부동·유동 따로 표시), 목표에 가까울수록 저금통이 두둑해지고 표정이 밝아짐
- 공유 버튼 → 내 초대코드 보내기 / 상대 코드로 참여 (최대 2명, 실시간 반영, 오프라인 입력 가능)

## APK 받기 · 자동 업데이트

main 에 머지될 때마다 GitHub Actions 가 빌드해서 [최신 릴리스](../../releases/latest)에 `gagyebu.apk` 를 올린다.

앱은 켜질 때 릴리스를 확인해서 새 빌드가 있으면 **업데이트** 창을 띄운다. 누르면 받아서 설치 화면을 연다(처음 한 번은 '이 앱에서 설치 허용'을 켜야 함). 로그인 없이 확인하려면 저장소가 **공개(Public)** 여야 한다.

## 공유 켜기 (Firebase 설정, 처음 한 번)

`app/google-services.json` 이 없으면 **혼자 쓰기 모드**(이 폰에만 저장)로 빌드된다.

1. https://console.firebase.google.com 에 구글 계정으로 접속 → **프로젝트 추가** → 이름 `gagyebu` → (애널리틱스는 꺼도 됨) → 만들기
2. 프로젝트 첫 화면에서 **안드로이드 아이콘** 클릭
   - Android 패키지 이름: `com.squatwolf.gagyebu` (정확히 이대로)
   - 앱 닉네임, SHA-1 은 비워 둠 → **앱 등록**
3. **google-services.json 다운로드** → 나머지 단계는 전부 **다음**/**콘솔로 이동**
4. 왼쪽 메뉴 **빌드 → Authentication → 시작하기 → 로그인 방법 → 익명** → 사용 설정 → 저장
5. 왼쪽 메뉴 **빌드 → Firestore Database → 데이터베이스 만들기**
   - 위치: `asia-northeast3 (서울)` → **프로덕션 모드**로 시작
   - 만들어지면 **규칙** 탭에 이 저장소의 [`firestore.rules`](firestore.rules) 내용을 통째로 붙여넣고 **게시**
6. GitHub 저장소에서 `app` 폴더 → **Add file → Upload files** → `google-services.json` 올리고 커밋
7. 빌드가 끝나면 새 APK 를 두 폰에 설치 → 한 사람이 공유 버튼에서 **코드 보내기**, 다른 사람이 **참여하기**

요금은 무료(Spark) 요금제로 충분하다.

## 사용한 무료 에셋

- 동전·반짝이·돈주머니 그림: [Microsoft Fluent Emoji](https://github.com/microsoft/fluentui-emoji) 3D, MIT License ([licenses/FluentEmoji-MIT.txt](licenses/FluentEmoji-MIT.txt))
- 돼지 저금통은 앱 안에서 직접 그림
