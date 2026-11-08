# 꼬빵 작업 규칙 (클로드용)

- 답은 한국어로 짧게.
- GitHub 은 쓰지 않는다(계정 정지). 커밋은 이 PC 의 로컬 git 에만 한다. push·PR·릴리스 없음.
- 기능을 고치면 커밋하고, 릴리스 APK 를 빌드해서 경로를 알려 준다.

## 빌드 (Windows)

- Android Studio 가 설치돼 있어야 한다. SDK 경로는 `local.properties` 의 `sdk.dir` (없으면 Android Studio 로 이 폴더를 한 번 열면 만들어진다).
- 빌드 번호(versionCode)는 매번 올린다. 마지막으로 배포한 번호는 103. 이미 깔린 앱보다 커야 덮어 설치된다.
  ```
  set BUILD_NUMBER=104
  gradlew.bat assembleRelease
  ```
  PowerShell 이면 `$env:BUILD_NUMBER=104; .\gradlew.bat assembleRelease`
- 결과: `app\build\outputs\apk\release\app-release.apk` (signing.keystore 로 서명됨. 키를 바꾸면 기존 앱에 덮어 설치가 안 된다).
- 마지막으로 쓴 번호를 이 파일에 고쳐 적는다.

## 참고

- 공유 데이터는 Firebase(프로젝트 gagyebu-4b856, Firestore + 익명 로그인). 규칙은 `firestore.rules`.
- 앱 자동 업데이트(`data/Updater.kt`)는 GitHub 릴리스를 보던 것이라 지금은 동작하지 않는다.
