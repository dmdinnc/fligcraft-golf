# Change: Walking on sculpted terrain

Villagers should walk over gentle course slopes without repeatedly jumping at the terrain beneath their feet.

## Expected behavior

- Flat course terrain and gentle, walkable slopes should not provoke repeated obstacle jumps.
- Address the terrain blocks without changing villagers' goals, navigation, or jump controllers.
- Players, golf carts, ball contacts, and existing saved terrain must retain their behavior.
- Real obstacles and steep changes must still require a valid route or normal jumping.

## Block-side approach

For mobs, a tile whose corners vary by at most 8/16 (and no more than the mob's normal step height) supplies a level collision top at its highest corner. This avoids the vanilla jump controller treating the higher part of the occupied tile as an obstacle. It applies to all four materials and their caps. Steeper terrain retains the existing stepped collision.

This deliberately trades exact foot placement for calmer walking: mobs can stand above the lower side of a gentle tile by up to 8/16. Players and carts retain the existing stepped collision, and balls retain their exact surface contacts. No AI, goals, navigation implementation, tags, or movement attributes are changed.

The block's path type treats nonempty bases and caps as ground, and fully lowered tiles as open space above their foundations. Terrain edits lift mobs onto their new support shape, including a steep-to-gentle edit that lowers the corners but raises the level support.

## Investigation

The original terrain fails a headless reproduction: walking along the lower tread requests a jump at a higher corner elsewhere in the same block. The fix passes that reproduction and actual navigation tests without any jump requests. Ten new regression tests cover ascent and descent, mixed materials, the 8/16 relief boundary, cap transitions, fully lowered tiles, full-height caps, normal obstacles, retained player/steep collision, and live edits.

Validation: Gradle build and all five client fixture compilations pass. All 181 required headless GameTests pass, including the ten terrain walking regressions. No save format or network changes are needed.

Related baseline: [Terrain system](../course/terrain-system/spec.md).
