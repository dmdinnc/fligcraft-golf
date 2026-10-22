# Clubs and shots

## Scope

Define striking a ball with the six clubs, shot controls, launch modifiers, and world-specific power tuning.

## Current behavior

### Six club types
The mod must offer driver, hybrid, iron, pitching wedge, sand wedge, and putter as distinct clubs with their own launch behavior. A golf bag must hold clubs for play.

#### Example: Choose a club
- When a player strikes the same ball using different club types under otherwise equal conditions
- Then the launch reflects each club's configured power and intended loft or putting behavior

### Hit sounds
All six clubs share the vanilla chain impact, with slight pitch variations: driver 0.60, hybrid 0.67, iron 0.74, pitching wedge 0.81, sand wedge 0.88, and quiet putter 0.70. Longer full-shot clubs sound lower, while sand wedge sounds highest. Non-putters add a short attack sound at pitch 0.9 for the swing. Lower charges are quieter.

Any club striking from bunker sand or vanilla sand/red sand adds a sand crunch and a softer impact. Sculpted slopes and extensions use the actual supporting terrain material. Teed balls and balls in water do not add the sand layer.

Sounds play at the ball when the server launches it, including the six-second automatic shot. The hitter and nearby players hear the same broadcast through the Players sound category. Charging, arming, and attempts without a valid pending ball remain silent. The mod references existing vanilla events and bundles no audio recordings.

### Charged accuracy shot
Holding a club must charge shot power. Releasing it near an eligible ball must arm an accuracy shot, which is struck by a left-click or automatically after six seconds. The launch must reflect charge, accuracy timing, aim, and the selected club.

#### Example: Valid charged release
- When the player charges for at least the minimum hold and releases while targeting a nearby ball
- Then an accuracy shot is armed for that ball

#### Example: Strike an armed shot
- When the player left-clicks during the accuracy window or lets six seconds elapse
- Then the server launches the armed ball and counts a stroke

#### Example: No eligible ball
- When the player releases without an eligible nearby ball
- Then no shot is armed, launched, or counted

#### Example: Putt from a slope
- When a putter strikes a ball resting on sloped terrain
- Then its initial direction follows the local surface tangent

### Launch conditions
Shots must incorporate the current surface, tee state, and wind. A tee must increase launch power by 10 percent; water and sand must reduce shot power.

#### Example: Shot from a tee
- When a ball on a golf tee is struck
- Then the tee bonus is applied to launch power

#### Example: Shot from a hazard
- When a ball is struck from water or bunker sand
- Then the appropriate surface power penalty is applied

### World-specific power tuning
The server must use a separate global and per-club power multiplier from the active world's `serverconfig/fligcraft_golf-server.toml`. Each factor must accept 0.1 through 5.0, default to 1.0, and affect the next strike without altering balls already in flight.

#### Example: Combine factors
- When the global multiplier is 1.25 and the driver's is 1.5
- Then the driver's launch power uses an effective 1.875 multiplier

#### Example: Change during charge
- When an authorized player changes a factor before a charged shot is released
- Then the next strike uses the new factor

#### Example: Preserve separate worlds
- When two worlds have different club settings
- Then each uses its own saved settings without using an instance-wide club config

### Tuning commands
Players must be able to inspect club factors with `/golf clubs show`; changing or resetting factors must require operator level 2 or equivalent single-player permission and must save to the active world.

#### Example: Unprivileged inspection
- When a player without operator permission runs `/golf clubs show`
- Then individual and effective factors are shown

#### Example: Authorized update
- When an authorized player runs a valid set or reset command
- Then the selected factor changes and persists across a server restart

### Tee and driving-range setup
Golf tees must support placed balls. A powered driving-range bay must provide balls in front of it when the spawn spaces are empty.

#### Example: Power an empty bay
- When redstone powers a bay with free ball positions in front
- Then it spawns two playable balls

#### Example: Occupied bay
- When the target positions already contain balls
- Then the bay avoids duplicating them

### Automated carry calibration
The headless suite must measure the five lofted clubs from both fairway and a tee over flat fairway under open sky. Shots use full charge, perfect accuracy, zero spin, and no wind. Carry must end at the first descending surface contact, before bounce or roll. A separate total distance must not affect calibration. Any ceiling clipping the initial flight must fail the benchmark. The putter has no carry target.

Existing club balance remains unchanged. The mod does not prescribe target carry distances. The editable [test profile](../../../src/gameTest/resources/club-carry-profile.json) contains calibration test data; users can supply their own fairway carry targets, trial power factors, and tolerance when experimenting. Targets are entered in yards and converted using exactly 0.9144 meters per yard. Tee shots retain their normal height and launch-power bonus; their carry is measured rather than assigned another target.

Run `./gradlew clubCarryTest` (Windows: `.\gradlew.bat clubCarryTest`). It runs the headless suite, measures the profile's trial factors, searches the supported effective power range, and validates the rounded recommendations with fresh shots against the profile's tolerance. Target misses at the current trial factors are reported; unreachable targets or failed verification of the recommendations fail the test.

Outputs in `build/reports/club-carry/` are `carry.md`, `carry.csv`, `carry.json`, `suggested-clubs.toml`, `suggested-profile.json`, and `status.txt`. The reports identify the conditions, trial and recommended factors, and fairway/tee carry; JSON also includes flight time and apex. `status.txt` must say `PASS` for a successful current run. GitHub Actions uploads these as the `club-carry-calibration` artifact.

To try different targets or factors, edit the test profile or pass `-PclubCarryProfile=path/to/profile.json`. To enforce that the trial factors meet the goals, add `-PclubCarryVerifyTargets=true`. For example, verify the generated recommendations with:

```text
./gradlew clubCarryTest "-PclubCarryProfile=build/reports/club-carry/suggested-profile.json" "-PclubCarryVerifyTargets=true"
```

The fixture executes real club strikes, then scales their launch vector to the requested effective factor; it does not copy the flight model or write shared server settings. Wind is disabled only on benchmark balls. Exported world settings assume a global factor of 1.0, with the calibrated factors assigned per club. They are suggestions for the active world's `serverconfig/fligcraft_golf-server.toml`, not automatically applied changes to gameplay defaults. Choose any putter setting separately.
