# Feature specs

These files are a starting description of the mod's current behavior, based on the code, README, and tests. They are grouped by system so a change can refer to one or two useful files. They are editable working documents, not a formal approval or validation process.

| Category | System | Covers |
| --- | --- | --- |
| Course | [Terrain system](course/terrain-system/spec.md) | Materials, corners, extensions, placement, persistence, light |
| Course | [Terrain editing](course/terrain-editing/spec.md) | Trowel, mower, grader, preview, undo |
| Course | [Flags and cups](course/flags-and-cups/spec.md) | Planting flags, cup capture, scoring |
| Play | [Clubs and shots](play/clubs-and-shots/spec.md) | Clubs, charge, accuracy, tuning, tees, range bay |
| Play | [Ball targeting and flight](play/ball-targeting-and-flight/spec.md) | Shot targeting, helper, rangefinder, flight guides |
| Play | [Ball physics](play/ball-physics/spec.md) | Slopes, materials, bounce, roll, settling |
| Play | [Ball management](play/ball-management/spec.md) | Colors, ownership, tracker, locate, return |
| Vehicles | [Golf carts](vehicles/golf-carts/spec.md) | Driving, slope movement, multiplayer sync |

For a change, tell Codex what you want in ordinary language. We can turn it into a short file in `changes/` using the [optional template](changes/TEMPLATE.md), discuss any unclear details, implement it, and revise the same note and code as we learn. Once the behavior is stable, update the relevant system file here so the baseline stays useful. There is no required command, special syntax, archive step, or spec-only CI check. Pull requests still run the build, headless GameTests, and client-fixture compilation.
