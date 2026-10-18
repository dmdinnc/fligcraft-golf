# Flags and cups

## Scope

Define how flags attach to course terrain and how balls are captured at ordinary and sculpted cups.

## Current behavior

### Flags on sculpted terrain
A Golf Flag must attach to a course tile, including when placed through its upper cap. Its upright pole and cup must follow the live surface at the tile center without changing corner heights.

#### Example: Plant on an edited tile
- When a player uses a Golf Flag on a course tile or its cap
- Then one flag is attached to the original tile and its cup is positioned at the live surface

#### Example: Edit under a flag
- When the tile's corner heights or cap change
- Then the flag stays attached and its cup follows the new center height

#### Example: Light a raised flag
- When a flag is planted on lowered, full-height, or raised course terrain, including a fully filled 32/16 cap
- Then its pole, cloth, and cup use light at their visible positions above the surface
- And nearby shade and light sources affect the flag normally

#### Example: Retrieve a flag
- When a player sneak-right-clicks the tile with an empty main hand and may edit it
- Then the flag is returned without changing the terrain

#### Example: Break a cap beneath a flag
- When only the cap is broken
- Then the flag stays with the original tile

#### Example: Break flagged terrain
- When the original tile is broken
- Then its flag drops with the terrain

### Protected flag changes
Planting and retrieving flags must respect block protection and must preserve ordinary existing flag placement.

#### Example: Protected tile
- When the player lacks permission to modify the target tile
- Then planting or retrieval makes no change

### Moving-ball cup capture
Both ordinary and sculpted cups must capture a moving ball only after its path stays sufficiently close to the cup and live surface for the required time. Fast crossings must pass without scoring.

#### Example: Slow central approach
- When a moving ball remains within about 0.20 blocks of the cup center for about 2.7 ticks near the surface
- Then it scores

#### Example: Fast crossing or departure
- When a ball crosses too quickly, leaves the cup, or moves away from the live surface
- Then capture time resets and the crossing does not score

#### Example: Tick-spanning path
- When an ordinary cup is crossed between two ball positions within a tick
- Then the path is checked rather than relying only on the ball's end position

### Nearly stopped capture
A ball that has nearly stopped near a cup may receive stopped-ball capture assistance. Assistance must use the approach speed before drag or collision so a fast crossing cannot score retroactively.

#### Example: Settled sculpted putt
- When a ball nearly stops within 0.5 blocks of a sculpted cup's center and within the surface-height tolerance
- Then it scores

#### Example: Above the live surface
- When a ball is near the cup horizontally but has not reached the current raised or lowered surface
- Then it does not score

### Flagpole collision
The lower 0.75 blocks of ordinary and sculpted flagpoles must allow rolling balls to pass, while the upper stick must still collide with airborne balls.

#### Example: Roller beneath pole
- When a ball rolls through the lower pole space
- Then the pole does not deflect it

#### Example: Flying ball strikes pole
- When an airborne ball contacts the stick above that gap
- Then it rebounds using flagpole damping
