# Change: Automated club carry calibration

Provide a repeatable headless benchmark and optional power calibration for full-charge, perfect-accuracy shots over flat fairway, with wind removed. Preserve the existing gameplay balance; the earlier proposed yardages are not adopted as design goals.

## Expected behavior

- Carry ends at the first descending contact with the flat landing surface, before any bounce or roll. It is horizontal distance, in meters (one block per meter).
- Measure the five lofted clubs from both fairway and an actual tee over the same flat fairway landing area. Tee shots retain their real launch height and power bonus. The putter is outside the carry benchmark.
- Use the registered clubs, real charge/accuracy execution, and real ball tick/contact physics. Wind is disabled only on benchmark balls, not globally in gameplay.
- The range must be open to the sky. Fail calibration if an overhead obstruction clips the initial flight; GameTest's default enclosing roof must be disabled.
- Measure the profile's current multipliers, search within the supported 0.1–5.0 effective power range, and validate suggested settings with fresh shots. Report carry separately from final distance after bounce/roll.
- Export human-readable and machine-readable results plus suggested world config settings. Do not change shipped club defaults or a player's world config.
- An editable test profile supplies user-selected fairway carry targets, trial multipliers, and carry tolerance. The bundled profile is calibration test data, not a requirement for shipped club distances. An optional strict check rejects trial settings that miss the selected goals.

Related baseline: [Clubs and shots](../play/clubs-and-shots/spec.md).

## Tooling limits and validation

The power-only search retains each club's existing loft. Carry alone does not describe a trajectory; exported apex and flight-time measurements help assess proposed changes. Recommendations are for deliberate tuning experiments and are never applied automatically.

Validation covers the Gradle build, all five client fixture compilations, and the headless GameTests. A generated profile can be loaded back in for strict verification. Dedicated regressions check first-impact recording during a bounce tick and detection of a ceiling clipping the initial flight.
