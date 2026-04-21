# TJLabsAuth-sdk-android

Android 앱에서 TJLabs Auth 서버로 인증 토큰을 발급받기 위한 SDK입니다.

## Requirements

- JDK 17 (AGP 8+)
- Android Gradle Plugin 8+
- Kotlin
- Java 8+
- Android API 26+

## Dependency

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven(url = "https://jitpack.io")
    }
}
```

```kotlin
dependencies {
    implementation("com.github.tjlabs:TJLabsAuth-sdk-android:<version>")
}
```

## Quick Start

```kotlin
// 1) client secret 등록
// 기본값 persist = false (단말 영구 저장 안 함)
TJLabsAuthManager.setClientSecret(
    context = applicationContext,
    secret = clientSecret
)


// 2) auth 호출
TJLabsAuthManager.auth(accessKey, secretAccessKey) { statusCode, success ->
    // success == true 이면 access token 발급 성공
}
```

## Request Payload

Auth 요청에는 아래 데이터가 전달됩니다.

- `client_secret` (앱에서 setClientSecret으로 전달)
- `access_key`
- `secret_access_key`
- `client_meta`
  - `app_version`
  - `app_package`
  - `device_model`
  - `os_version`
  - `sdks` (setSdkInfos로 등록한 SDK 목록)

## Security Notes

- Release 빌드에서는 SDK 내부 debug 로그를 출력하지 않습니다.
- `setClientSecret()` 기본값은 `persist = false`입니다.
- 가능하면 `access_key`, `secret_access_key`, `client_secret`는 앱에 하드코딩하지 말고 서버/안전 저장소에서 주입하세요.
- 샘플 앱에서는 `local.properties` 또는 Gradle property로 주입하세요.

예시 (`local.properties`):

```properties
AUTH_CLIENT_SECRET=...
AUTH_ACCESS_KEY=...
AUTH_SECRET_ACCESS_KEY=...
```

## PR Validation Workflow

- Workflow file: `.github/workflows/pr-validate.yml`
- Trigger:
  - `pull_request` to `main`, `release/*`
  - `workflow_dispatch`
- Checks:
  - `:<LIB_MODULE>:testDebugUnitTest`
  - `:<LIB_MODULE>:testReleaseUnitTest`
  - `:<LIB_MODULE>:publishToMavenLocal -x test`
- One failing step makes the workflow fail (branch protection에서 required check로 설정 시 merge 차단 가능).

## Release Automation Workflow

- Workflow file: `.github/workflows/release-jitpack.yml`
- Trigger:
  - `push` to `release/x.y.z`
  - `workflow_dispatch` with optional `release_version`
- Flow:
  1. release 버전 파싱 및 `x.y.z` 형식 검증
  2. `sdk/build.gradle.kts` 버전과 릴리즈 버전 일치 검증
  3. Unit test 2종 실행
  4. `publishToMavenLocal -x test` 검증
  5. 태그(`x.y.z`) 자동 생성/푸시 (없을 때만)
  6. JitPack build log URL warm-up 호출
  7. build log artifact 업로드
  8. (선택) Resource 레포로 `repository_dispatch` 이벤트 전달
     - event: `auth_release_published`
     - payload: `auth_version`, `target_branch=main`
     - `RESOURCE_REPO_DISPATCH_TOKEN`가 설정된 경우에만 실행

### Cross-repo automation (Auth -> Resource)

- Auth 릴리즈 후 Resource 레포에 자동 이벤트를 보낼 수 있습니다.
- 목적: Resource에서 Auth 버전 bump PR을 자동 생성(작업 브랜치 직접 수정 금지)
- 필수 Secret (Auth 레포):
  - `RESOURCE_REPO_DISPATCH_TOKEN` (Resource repo dispatch 권한이 있는 PAT)
- 기본 대상:
  - Repo: `tjlabs/TJLabsResource-sdk-android`
  - Target branch: `main`

## JitPack Build Config

- File: `jitpack.yml`
- JDK: `openjdk17`
- Install: `./gradlew :sdk:publishToMavenLocal -x test --stacktrace --no-daemon`

## Test Scope

- Live API smoke test는 자동화 워크플로우에서 제외합니다.
