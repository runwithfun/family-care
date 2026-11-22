# Family Care

Family Care is a mobile day plan for a mother's care and her child's appointments, records, and follow-ups. The hackathon demo uses a fictional family and sample clinic documents. The app runs on the device and requires no backend or analytics service. Visit the [project site](https://runwithfun.github.io/family-care/).

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

## Walk through the demo

1. Complete the illustrated welcome with the example child name **Maya**. The profile photo is optional. Today opens on the fixed demo day, November 23, 2026.
2. Open the **Well-baby visit** or the **Recovery follow-up** from Today. Use **Calendar** to select a date, edit a visit, or add a dated plan item for a family member.
3. In **Records**, tap **Import synthetic clinic PDF**. Open the immunization record to retrieve the original PDF and review a field. The missing dose date can stay unresolved. Confirmed appointments and published schedule references are shown separately.
4. In **Assistant**, tap **Open original note**, then **Add to Today** on the sample follow-up. The task appears under **For you**. Restart the app to see the same saved item.
5. In **Growing up**, switch between the two age stages. Save a note for the next open visit, then open an activity and use **Export PDF** with A4 or US Letter.

## Shared demo model

`DemoFamily.createPlan()` creates a fresh in-memory plan with a fictional mother, child, and fixed demo day. Create it once at the app entry point and share the same plan instance with screens. `eventsOn(date, personId)` provides the agenda, and its optional person filter supports the profile and calendar views.

- A visit has its own booking state, and a task has an independent completion state. Both can link back to an original document for the same person.
- Documents keep original content, extracted proposals and reviewed values. Recorded immunizations, public references and appointments are separate.
- A confirmed assistant proposal writes through the same event operation as the calendar. Repeating the same event ID and details returns the existing result. A different event under that ID is rejected.

## License

Family Care is available under the [MIT License](LICENSE). The Gradle wrapper is distributed under the [Apache License 2.0](app/gradle/wrapper/LICENSE). Bundled fonts retain the SIL Open Font License notices in the app resources.
