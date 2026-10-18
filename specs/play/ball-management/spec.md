# Ball management

## Scope

Define ball colors, ownership, persistent tracking, locating, and returning balls to players.

## Current behavior

### Sixteen ball colors
Golf balls must support all sixteen vanilla dye colors, with white as the default for new and legacy balls. Dyeing any ball with one vanilla dye must produce a ball of that dye's color.

#### Example: Recolor a ball
- When a player crafts any colored ball with a vanilla dye
- Then the result is one ball of the dye's color

#### Example: Legacy ball
- When a ball saved without color data loads
- Then it appears as white

### Color continuity
A ball's color must survive placement, strokes, saves, scoring, and tracker deletion/return. Its glowing outline and flight guide must match that color.

#### Example: Return a colored ball
- When a colored ball is deleted through the tracker
- Then the returned item has the same color

### Persistent ownership and index
The server must track balls placed or last hit by a player across dimensions. Loaded balls must update their tracked state; unloaded balls must retain their last known information until loaded again or deleted.

#### Example: Cross-dimension listing
- When an owned ball is in another dimension or an unloaded chunk
- Then it remains listed with its last known dimension, coordinates, strokes, and state

#### Example: Existing ball after upgrade
- When a chunk containing an older unindexed ball loads
- Then that ball enters the tracking index

### Tracker interface
Using the Golf Helper or `/golf_helper_manage` must open a tracker with up to 45 balls per page and previous, refresh, and next controls. The tracker must show useful ball details without allowing the display items to be taken.

#### Example: Many balls
- When a player owns more than 45 tracked balls
- Then they can page through the list and inspect coordinates, dimension, distance, strokes, state, and nearby block information

### Private ball location
Left-clicking a tracker entry must draw a colored particle path for five seconds only for that player, provided the ball is loaded, in the same dimension, and within 128 blocks.

#### Example: Eligible locate
- When the player selects a loaded owned ball within 128 blocks in their dimension
- Then the tracker closes and only that player sees the temporary path

#### Example: Ineligible locate
- When the ball is unloaded, in another dimension, or too far away
- Then no path is drawn and the player receives a reason

### Authorized single return
Tracker actions must recheck ownership on the server. Right-clicking an owned entry must remove that ball and return exactly one item of the same color, even if the entity is unloaded or an old chunk copy later loads.

#### Example: Delete an unloaded ball
- When an owner deletes an unloaded ball
- Then one colored item is returned immediately and the ball is removed when its chunk next loads

#### Example: Full inventory
- When the owner's inventory is full during return
- Then the item drops at the owner's feet

#### Example: Unauthorized or duplicate request
- When another player requests a tracker action or a deleted ID appears again
- Then the request cannot return or control an extra ball
