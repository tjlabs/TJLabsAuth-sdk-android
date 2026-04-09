# TJLabsAuth-sdk-android

Android 앱에서 TJLabs Auth 서버로 인증 토큰을 발급받기 위한 SDK입니다.

## Requirements

- Kotlin
- Java 8+
- Android API 26+

## Dependency

```kotlin
dependencies {
    implementation("com.tjlabs:TJLabsAuth-sdk-android:<version>")
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
