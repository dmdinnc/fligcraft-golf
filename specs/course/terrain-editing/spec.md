# Course terrain editing

## Scope

Define the player tools for shaping and replacing course terrain while preserving adjacent geometry and respecting world protection.

## Current behavior

### Trowel corner editing
The terrain trowel must raise or lower course-terrain corners in selectable steps of 1/16, 2/16, 4/16, or 8/16. Its step and mode must remain on the item.

#### Example: Lower a corner
- When a player right-clicks a corner with the trowel
- Then the selected height is lowered by the current step within the terrain range

#### Example: Raise a corner
- When the player sneak-right-clicks the corner
- Then the selected height is raised by that step within the terrain range

#### Example: Switch edit step
- When the player uses Swap Hands with the trowel in either hand
- Then the edit step cycles without moving the held item

#### Example: Switch edit mode
- When the player uses Sneak and Swap Hands
- Then the edit mode cycles

### Trowel edit modes
The trowel must offer Shared corners, One corner, and Level tile modes. Shared corners must join touching course terrain at the same world corner, including eligible tiles one block above or below whose heights meet within four steps.

#### Example: Shared corner across materials
- When a shared corner is edited among loaded course tiles
- Then each eligible touching tile receives the same world height, regardless of course material

#### Example: One-corner mode
- When One corner mode is used
- Then only the targeted tile's selected corner changes

#### Example: Level-tile mode
- When Level tile mode is used
- Then all four corners of the targeted tile receive the selected corner's next height

#### Example: Protected grouped edit
- When any required tile or cap in a shared edit cannot be changed
- Then the whole edit is rejected without loading neighboring chunks or leaving partial geometry

### Editing preview and entity support
The client must show the selected corner, current and next heights, edit mode and step, affected tiles, and proposed shape. Terrain edits must keep lowered tiles selectable and lift entities standing on raised terrain.

#### Example: Preview a shape
- When the player aims at editable terrain with the trowel
- Then the affected tiles and next surface are outlined before the edit

#### Example: Raise under a standing entity
- When terrain is raised beneath an entity standing on it
- Then the entity is moved onto the edited surface

### Lawnmower conversion
The lawnmower must cycle among fairway, rough, putting green, and bunker sand. It must convert eligible ground in a 3-by-3 area or a single block while preserving existing course shapes, extensions, and flags.

#### Example: Switch mowing material
- When a player right-clicks in the air
- Then the selected material cycles and its translated block name appears in the mode message

#### Example: Area mowing
- When a player right-clicks a ground block
- Then eligible blocks in the centered 3-by-3 area are converted to the selected material

#### Example: Single-block mowing
- When the player sneak-right-clicks
- Then only the targeted block is considered

#### Example: Skip unchanged terrain
- When a block already has the selected material or is ineligible
- Then it is skipped without durability cost

#### Example: Reject protected mowing
- When a required conversion is protected or needs a blocked extension
- Then the operation is cancelled as a whole

### Grader selection and preview
The terrain grader must use four reference corners to preview a Plane or Corner Blend surface over a selection up to 64 by 64 blocks. It must leave terrain unchanged until the player applies the preview.

#### Example: Build a valid selection
- When a player selects four corners that enclose an area
- Then the grader previews the proposed surface and reports invalid or blocked parts

#### Example: Change grading mode
- When the player switches between Plane and Corner Blend
- Then Plane fits the references as closely as possible, while Corner Blend retains their heights

#### Example: Stale reference
- When a selected reference corner's height changes before application
- Then the preview cannot be applied until that reference is recaptured

### Grader application and partial repair
The grader must update only selected vertices and matching corners on immediately touching tiles. A full operation must recheck loading, protection, caps, and source geometry before applying. An explicit partial-repair confirmation may fill missing terrain and skip remaining local problems.

#### Example: Apply a valid preview
- When the player confirms a valid preview
- Then its rounded 1/16 heights are applied without moving existing blocks or changing their materials

#### Example: Confirm partial repair
- When the preview has repairable gaps and the player accepts the current partial-repair offer
- Then reachable terrain is graded, missing columns may be filled with up to eight foundation blocks, and blocked columns are skipped

#### Example: Insufficient survival supplies
- When a repair needs a course block the player does not have
- Then the whole affected column is skipped

#### Example: Changed confirmation state
- When the selection, mode, or preview changes after a partial-repair offer
- Then that offer expires

### Grader undo
The grader must allow the player to undo the last grading operation in the current server session, subject to permission and later-change checks.

#### Example: Undo a safe operation
- When the player sneak-right-clicks after grading and the affected area is unchanged and editable
- Then prior geometry is restored, placed repair blocks are removed, and consumed survival supplies are refunded

#### Example: Refuse an unsafe undo
- When another edit or protection change would be overwritten
- Then undo is refused rather than overwriting that change
