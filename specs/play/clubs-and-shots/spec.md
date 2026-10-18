# Clubs and shots

## Scope

Define striking a ball with the six clubs, shot controls, launch modifiers, and world-specific power tuning.

## Current behavior

### Six club types
The mod must offer driver, hybrid, iron, pitching wedge, sand wedge, and putter as distinct clubs with their own launch behavior. A golf bag must hold clubs for play.

#### Example: Choose a club
- When a player strikes the same ball using different club types under otherwise equal conditions
- Then the launch reflects each club's configured power and intended loft or putting behavior

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
