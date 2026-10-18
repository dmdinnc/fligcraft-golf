# Ball targeting and flight

## Scope

Define how players select and locate balls and how the client follows airborne and rolling shots.

## Current behavior

### Club ball targeting
A charged club must select a nearby ball in the player's facing direction, preferring a ball that player last hit when multiple candidates are eligible.

#### Example: Multiple nearby balls
- When a player aims toward multiple eligible balls within club reach
- Then a ball last hit by that player is preferred

#### Example: No ball in reach
- When all balls are outside club reach or aim
- Then the club does not launch an unrelated ball

### Rangefinder reading
The rangefinder must measure to a visible target along the player's aim up to 256 blocks and update its displayed range while used.

#### Example: Target in range
- When a player aims the rangefinder at a target within 256 blocks
- Then a rounded distance is reported

#### Example: No target
- When the ray finds no target within range
- Then the player is told no target was found

### Golf Helper summary
Sneak-right-clicking the Golf Helper must show a summary of the player's balls, wind, and nearest flag within its search range. A flag on sculpted terrain must use its live cup position.

#### Example: Nearby sculpted flag
- When a player requests the summary near a flag planted on edited terrain
- Then the reported flag range uses the current surface height

### Extended ball visibility
The client and server must allow a ball to be rendered or tracked up to 512 blocks away, subject to normal view distance and loaded-chunk limits. A distant outlined marker must fade in beyond 24 blocks.

#### Example: Distant loaded ball
- When a ball is in a loaded, tracked area beyond ordinary small-entity rendering distance
- Then it remains visible up to the extended limit with a distance marker beyond 24 blocks

### Shot flight guides
Moving non-putt shots must leave a roughly one-second trail in the ball's color, following flight, bounces, and roll-out. Guides must fade when the ball stops and obey normal depth testing.

#### Example: Follow a chip
- When a colored ball is chipped and then bounces and rolls
- Then its colored guide follows those phases and fades after stopping

#### Example: Putt
- When a ball is struck with a putter
- Then no flight guide appears

### Client guide preference
The `flightGuides` client setting must hide the trail and distant marker without disabling extended ball rendering.

#### Example: Disable guides
- When a client sets `flightGuides = false`
- Then the trail and marker are hidden while the ball itself remains renderable at extended distance
