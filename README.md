# Fligcraft Golf

A golf mod for Minecraft 1.21.1 on NeoForge.

## Features

- Clubs with charged, accuracy-based shots, wind, and configurable power.
- Golf balls in all 16 dye colors, with flight trails, distant markers, and a tracker.
- Tees, flags and cups, a driving range, and golf carts.
- Editable fairway, rough, putting green, and bunker sand with corner heights from 0/16 to 32/16.
- A lawnmower, terrain trowel, and terrain grader for building courses.

## Contributing

Open an issue before starting a large change so its scope can be discussed. Keep pull requests focused, describe the player-visible behavior, and include relevant tests. The grouped [feature specs](specs/README.md) document the current behavior and can be updated alongside a change.

## Reporting issues

Use [GitHub Issues](https://github.com/dmdinnc/fligcraft-golf/issues). Include the Minecraft and NeoForge versions, steps to reproduce, the expected and actual behavior, and relevant logs or screenshots.

## Build and test

Use JDK 21. The project targets Minecraft 1.21.1 and NeoForge 21.1.219. Run the Gradle wrapper as `./gradlew` on macOS/Linux or `.\gradlew.bat` on Windows:

```text
./gradlew build
./gradlew runGameTestServer
./gradlew compileClientTestJava compileFlightClientTestJava compileGraderClientTestJava compileCartClientTestJava compileReloadClientTestJava
./gradlew runClient
```

`build` produces the mod jar in `build/libs/`. `runGameTestServer` runs the headless integration tests. The client fixture compilation checks test code without launching a graphical client. GitHub Actions runs the build, headless tests, and fixture compilation for pull requests to `main`.

Optional graphical tests require a display/GPU: `clientLightingTest`, `clientTerrainReloadTest`, `clientBallFlightTest`, `clientGraderPreviewTest`, and `clientCartSyncTest`. Run each as a separate Gradle command.

Mod ID: `fligcraft_golf`.
