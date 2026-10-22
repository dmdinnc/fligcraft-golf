# Course terrain system

## Scope

Define the editable course surface shared by fairway, rough, putting green, and bunker sand, including its shape, placement, extensions, and saved-world behavior.

## Current behavior

### Editable terrain shape
Each course terrain tile must have four corner heights in sixteenths of a block, inclusive from 0/16 through 32/16. Its visible and ball-contact surface must use two triangles split from northwest to southeast.

#### Example: Unequal corners
- When a tile has unequal corner heights
- Then the surface follows the two triangles, including a possible crease along their diagonal

#### Example: Height boundaries
- When an edit requests a corner below 0/16 or above 32/16
- Then that corner remains within the supported range

### Managed upper extension
The system must represent terrain above 16/16 with a matching upper cap owned by the original tile. The cap must have no separate item or independent terrain shape.

#### Example: Raise into upper cell
- When any corner rises above 16/16 and the upper cell is available and editable
- Then a matching cap appears above the original tile

#### Example: Blocked extension
- When a placement or grouped edit requires an occupied or protected upper cell
- Then the operation fails without partially changing the affected terrain

#### Example: Break the cap
- When only the cap is broken
- Then raised corners are trimmed to 16/16 and the base remains

#### Example: Break the base
- When the base is broken
- Then its cap is removed and only one terrain item drops

### Neighbor-matched placement
Newly placed terrain must match touching course-terrain corner heights where possible without reshaping existing neighbors. Matching must include diagonal tiles and eligible neighbors one block above or below.

#### Example: Restore a missing slope tile
- When a course tile is placed among compatible neighboring corners
- Then its shared corners join their world heights, with same-level neighbors preferred

#### Example: Conflicting neighbors
- When eligible neighboring corners disagree
- Then the most common height wins, with a tie resolved toward 16/16

#### Example: Copied explicit geometry
- When a placed terrain item carries explicit corner heights
- Then its copied shape is retained

### Surface interaction and light
The rendered shape, collision, and lighting must reflect the current corners and cap after placement, editing, and loading.

#### Example: Full cell
- When every part of a base or cap cell is filled, including a 32/16 cap
- Then that cell blocks direct skylight

#### Example: Partial cell
- When corners leave part of a cell unfilled
- Then that cell admits skylight and its lighting updates when the shape changes

#### Example: Reload beneath a tree
- When course terrain beneath leaves is saved and the world or chunk is reloaded
- Then the surface retains normal tree shade without black patches or manual block updates
- And lighting can read whether a cell is full before its corner metadata loads and on the lighting worker thread

### Walking mobs
Course blocks must let mobs walk over gentle terrain without repeatedly jumping at a higher corner of the tile they already occupy. A tile whose corner heights differ by at most 8/16, within the mob's normal step height, must provide mobs a level collision top at its highest corner. This can place their feet above the lower side of the visible surface. Steeper tiles retain stepped collision. Players and carts keep the existing stepped collision, and balls keep their exact terrain contacts.

#### Example: Villager walking on a green
- When a villager follows a route across gentle course slopes, including upper caps and different course materials
- Then it can walk along the route without repeated terrain-induced obstacle jumps, using its normal AI and movement attributes

#### Example: Lowered terrain and full caps
- When a path crosses fully lowered terrain or a full-height cap
- Then pathfinding uses the actual foundation or cap as support instead of treating solid caps as air

#### Example: Change a mob's support
- When a terrain edit increases a mob's collision support height, including by reducing a steep tile to a gentle tile
- Then the mob is lifted onto that support rather than left inside it

### Saved-world compatibility
Terrain edits must be server-owned, saved with the world, and synchronized to clients. Original fairway, rough, and putting-green IDs must remain usable; legacy layer blocks and items must not reappear.

#### Example: Open an older course
- When an existing course block without corner data loads
- Then it becomes a full-height editable tile without replacing its original block or item ID

#### Example: Save and reload an edited course
- When an edited tile and its cap are saved and later reloaded
- Then the same terrain shape and cap relationship are restored

#### Example: Removed layer content
- When an old Fairway Layer or Rough Layer block or item stack loads
- Then it is removed rather than converted into a new independent terrain item
