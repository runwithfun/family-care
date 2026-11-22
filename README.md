# Family Care

Family Care is a mobile planner for family appointments, records, and follow-ups. The starter app opens a fictional child profile and day plan locally. The app is designed to run on the device with local records, reminders, and Assistant actions. It requires no backend deployment or analytics service. The project site is published at [https://runwithfun.github.io/family-care/](https://runwithfun.github.io/family-care/).

## Run the demo

The shared UI uses Kotlin and Compose Multiplatform. Use JDK 17 and the Gradle wrapper in `app/`.

### iOS

On an Apple-silicon Mac, install Xcode and an iOS simulator runtime. Set `JAVA_HOME` to a JDK 17 installation, then open [FamilyCare.xcodeproj](app/iosApp/FamilyCare.xcodeproj), select the **FamilyCare** scheme and an iOS simulator, and run. The Xcode build phase builds the shared framework through Gradle. A simulator build needs neither signing credentials nor a server.

```sh
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
xcodebuild -project app/iosApp/FamilyCare.xcodeproj -scheme FamilyCare \
  -destination 'generic/platform=iOS Simulator' -derivedDataPath app/iosApp/DerivedData \
  CODE_SIGNING_ALLOWED=NO build
```

### Android and shared checks

Use Android SDK 35 for the Android target. Set `ANDROID_HOME` to its installation or set `sdk.dir` in `app/local.properties`.

```sh
cd app
./gradlew :composeApp:desktopTest :composeApp:assembleDebug --max-workers=1
```

See [AGENTS.md](AGENTS.md) for coordination and verification rules.

## Shared demo model

`DemoFamily.createPlan()` creates a fresh in-memory plan with a fictional mother, child, and fixed demo day. Create it once at the app entry point and share the same plan instance with screens. `eventsOn(date, personId)` provides the agenda, and its optional person filter supports the profile and calendar views.

- A visit has its own booking state, and a task has an independent completion state. Both can link back to an original document for the same person.
- Documents keep original content, extracted proposals and reviewed values. Recorded immunizations, public references and appointments are separate.
- A confirmed assistant proposal writes through the same event operation as the calendar. Repeating the same event ID and details returns the existing result. A different event under that ID is rejected.

## License

Family Care is available under the [MIT License](LICENSE). The Gradle wrapper is distributed under the [Apache License 2.0](app/gradle/wrapper/LICENSE). Bundled fonts retain the SIL Open Font License notices in the app resources.
