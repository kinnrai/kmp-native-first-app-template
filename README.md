This is a Kotlin Multiplatform project targeting Android, iOS, native macOS, Web, Desktop (JVM for Windows and Linux), and Server.

* [/app/appleApp](./app/appleApp) contains a single Xcode project with separate `iosApp` and `macOSApp` targets.
  Their native SwiftUI implementations live in [iOS](./app/appleApp/iOS) and [macOS](./app/appleApp/macOS), respectively.
  Apple-only Swift adapters shared by both targets live in [Shared](./app/appleApp/Shared). Both targets consume the
  `SharedLogic` Kotlin/Native framework through direct Xcode integration, while AppKit remains available for macOS-specific
  integrations. Shared Xcode settings, including the bundle identifier and development team placeholder, live in
  [Base.xcconfig](./app/appleApp/Configuration/Base.xcconfig), while release versions remain independent in the
  platform-specific configuration files. The native macOS target supports Apple Silicon and macOS 13 or later.

* [/app/sharedLogic](./app/sharedLogic/src) is for the code that will be shared between app targets in the project.
  The most important subfolder is [commonMain](./app/sharedLogic/src/commonMain/kotlin). If preferred, you
  can add code to the platform-specific folders here too.

* [/app/sharedUI](./app/sharedUI/src) contains reusable UI shared by the Compose Multiplatform applications, currently
  Android and Desktop (JVM). Each application owns its app shell, theme, and platform behavior in its entry-point module,
  while [commonMain](./app/sharedUI/src/commonMain/kotlin) contains only the Compose UI they intentionally share.

* [/app/webApp](./app/webApp) contains a React web application with its own app shell and platform UI. It consumes a
  TypeScript adapter over the Kotlin/JS API exported by the [sharedLogic](./app/sharedLogic) module.

* [/core](./core/src) is for the code that will be shared between all targets in the project.
  The most important subfolder is [commonMain](./core/src/commonMain/kotlin). If preferred, you
  can add code to the platform-specific folders here too.

* [/server](./server/src/main/kotlin) is for the Ktor server application.

### Running the apps

Use the run configurations provided by the run widget in your IDE's toolbar. You can also use these commands and options:

- Android app: `./gradlew :app:androidApp:assembleDebug`
- Desktop app (Windows and Linux distribution):
  - Hot reload: `./gradlew :app:desktopApp:hotRun --auto`
  - Standard run: `./gradlew :app:desktopApp:run`
- Server:
  - Run: `./gradlew :server:run`
  - Open: `http://localhost:8080`
  - The server listens on `0.0.0.0:8080` by default; override the bind address with `KTOR_HOST` and `PORT`
- Web app:
  1. Install [Node.js](https://nodejs.org/en/download) (which includes `npm`)
  2. Build and run the web application:
     ```shell
     npm run build:shared
     npm ci
     npm run dev
     ```
     The development server is available at `http://localhost:5173`.
- Apple apps: open [AppleApps.xcodeproj](./app/appleApp/AppleApps.xcodeproj) in Xcode, then run the `iosApp` or `macOSApp` scheme.

### Running tests

Use the run button in your IDE's editor gutter, or run tests using Gradle tasks:

- Android tests: `./gradlew :app:sharedUI:testAndroidHostTest :app:sharedLogic:testAndroidHostTest`
- Desktop tests: `./gradlew :app:sharedUI:jvmTest :app:sharedLogic:jvmTest`
- Server tests: `./gradlew :server:test`
- Web tests: `./gradlew :app:sharedLogic:jsTest`
- iOS tests: `./gradlew :app:sharedLogic:iosSimulatorArm64Test`
- macOS tests: `./gradlew :app:sharedLogic:macosArm64Test`

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…
