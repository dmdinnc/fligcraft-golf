# Golf carts

## Scope

Define golf-cart driving over course terrain and the movement observed by passengers and other players.

## Current behavior

### Cart passengers and authority
A golf cart must carry a driver and a second rider. The driver's client must simulate driven movement while the server accepts vehicle position packets; an empty cart must use server physics.

#### Example: Driver takes control
- When a player drives a cart
- Then their client controls the driven motion and the server receives its position

#### Example: Empty cart
- When no driver controls the cart
- Then the server advances its movement

### Traversal of sculpted slopes
A grounded cart must maintain downward ground contact and divide fast movement into collision moves no longer than a quarter block. It must retain its one-block step height and normal acceleration and speed limits while walls still block it.

#### Example: Climb consecutive steps
- When a cart drives up sculpted terrain with consecutive traversable steps
- Then it maintains ground contact and forward movement through the climb

#### Example: Diagonal climb and wall
- When a cart climbs diagonally beside an obstruction
- Then its axes resolve separately and the obstruction still blocks impassable movement

### Observer synchronization
Other clients must interpolate cart position and rotation across updates, including rotation-only updates, without losing pending movement targets.

#### Example: Rotation while moving
- When observers receive a rotation update during a position interpolation
- Then the cart continues toward the pending position while turning smoothly

### Driver handoff and dismount
Changing or losing the driver must reset movement synchronization to a full current position so later updates and dismount placement use the correct base.

#### Example: Dismount while coasting
- When a driver exits a moving cart
- Then the cart's next updates use its current position and the player does not return to a pre-drive location

#### Example: Change driver
- When driving authority changes between occupants
- Then observers receive a full current position before relative updates continue
