# 꼬빵 작업 규칙 (클로드용)

- 답은 한국어로 짧게. 끝에 최신 릴리스 링크 https://github.com/squatwolf1/Claude/releases/latest
- 저장소는 squatwolf1/Claude. 변경은 PR → main 머지. main 에 머지되면 GitHub Actions 가 빌드해서 릴리스(build-N, gagyebu.apk)를 올리고, 앱이 그걸로 자동 업데이트한다.
- 계정이 자동 정지된 적이 있다. 작은 수정은 모아서 PR 하나로 올리고, 불필요한 빌드·릴리스를 만들지 않는다.

## PC 에서 직접 빌드 (Windows)

- Android Studio 가 설치돼 있어야 한다. SDK 경로는 `local.properties` 의 `sdk.dir` (Android Studio 로 이 폴더를 한 번 열면 만들어진다).
- 빌드 번호(versionCode)는 이미 깔린 앱보다 커야 덮어 설치된다. CI 는 실행 번호 + 100 을 쓴다.
  ```
  set BUILD_NUMBER=104
  gradlew.bat assembleRelease
  ```
  PowerShell 이면 `$env:BUILD_NUMBER=104; .\gradlew.bat assembleRelease`
- 결과: `app\build\outputs\apk\release\app-release.apk` (signing.keystore 로 서명됨. 키를 바꾸면 기존 앱에 덮어 설치가 안 된다).

## 참고

- 공유 데이터는 Firebase(프로젝트 gagyebu-4b856, Firestore + 익명 로그인). 규칙은 `firestore.rules`.
