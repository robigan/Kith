This is a Kotlin Multiplatform project targeting iOS.

* [/iosApp](./iosApp/iosApp) contains an iOS application. Even if you’re sharing your UI with Compose Multiplatform,
  you need this entry point for your iOS app. This is also where you should add SwiftUI code for your project.

* [/sharedLogic](./sharedLogic/src) is for the code that will be shared between app targets in the project.
  The most important subfolder is [commonMain](./sharedLogic/src/commonMain/kotlin). If preferred, you
  can add code to the platform-specific folders here too.

### Architecture

TheRealDex is a personal CRM for iPhone. Its requirements are in [docs/specs](docs/specs/app-base.md).

* **sharedLogic** (Kotlin): SQLDelight database (`src/commonMain/sqldelight`), repositories and source imports (`data`),
  pure rules such as cadence, note analysis and parsers (`domain`), and one view model per screen (`presentation`)
  exposing `StateFlow` state. Koin wires everything together (`di/Modules.kt`). Swift reaches it through `Dex.shared`.
* **SKIE** turns the flows into Swift `AsyncSequence`s, `suspend` functions into `async`, and Kotlin enums into Swift enums.
  `iosApp/iosApp/Support/Screen.swift` mirrors a view model's state into SwiftUI.
* **iosApp** (SwiftUI): the screens, plus device integrations in `Services/` (Contacts, EventKit, speech, notifications)
  that hand plain values to the shared `SourceSync`.

Debug builds accept `-seedSampleData YES` (loads the design's sample people) and `-initialTab people|groups` as launch arguments.

### Running the apps

Use the run configurations provided by the run widget in your IDE's toolbar. You can also use these commands and options:

- iOS app: open the [/iosApp](./iosApp) directory in Xcode and run it from there.

### Running tests

Use the run button in your IDE's editor gutter, or run tests using Gradle tasks:

- iOS tests: `./gradlew :sharedLogic:iosSimulatorArm64Test`

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…