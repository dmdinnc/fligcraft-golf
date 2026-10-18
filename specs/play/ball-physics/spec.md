# Ball physics

## Scope

Define golf-ball contact with sculpted terrain and material-dependent flight, bounce, roll, and rest.

## Current behavior

### Continuous sculpted contact
Ball placement, collision, and rolling must use the live triangular terrain surface, including raised caps, rather than only the coarse player collision steps.

#### Example: Roll across a cap boundary
- When a ball crosses a sloped tile whose surface extends above 16/16
- Then it follows the continuous surface without treating the base/cap boundary as a separate ledge

#### Example: Terrain edit beneath a ball
- When the surface under a ball changes
- Then the ball uses the new height and can resume motion if its prior resting condition no longer holds

### Slope and material rolling
Downhill terrain must accelerate a rolling ball and uphill terrain must slow or reverse it. Putting green, fairway, rough, and bunker sand must have distinct resistance, with sand slowing balls most strongly.

#### Example: Comparable rolls
- When otherwise equal balls roll over equally shaped green, fairway, rough, and bunker sand
- Then their run-out differs according to the material resistance

#### Example: Uphill ball
- When a ball lacks enough motion to climb a slope
- Then it slows and may roll back down

### Static settling
Low-speed balls must settle on moderate slopes according to material grip; sufficiently steep slopes must overcome that grip and cause sliding.

#### Example: Mild slope
- When a ball slows below the material's settling threshold on a mild grade
- Then it comes to rest without endless creeping

#### Example: Steep slope
- When the grade exceeds that material's static grip
- Then the ball can begin sliding

### Material and slope bounce
Impacts must use the contacted surface normal and material rebound behavior. Bunker sand must absorb impacts without rebound; turf must preserve appropriate forward run-out, while steep banks and exposed sides must redirect impacts along their actual normals.

#### Example: Land on bunker sand
- When an airborne ball lands on sculpted bunker sand
- Then it loses speed quickly without a rebound

#### Example: Shallow turf landing
- When a low shot lands on fairway or green
- Then it can skip into a roll without losing forward motion solely to the slope's downward normal component

#### Example: Steep impact
- When a ball hits a grade of at least 8/16 per block or an exposed side
- Then the bounce uses the full contacted normal

### Post-landing flight damping
After the first landing, rebounds must use lighter air damping than initial flight. A new stroke must restore normal flight damping, and the current phase must survive a save and reload.

#### Example: Repeated bounce
- When a ball rebounds after its first landing
- Then subsequent airborne motion uses the lighter post-landing damping

#### Example: New shot after reload
- When a ball is saved during post-landing motion, reloaded, and later struck again
- Then the saved motion continues in the correct phase and the new stroke starts with normal flight damping
