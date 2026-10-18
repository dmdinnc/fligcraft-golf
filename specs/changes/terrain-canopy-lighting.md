# Change: Preserve terrain lighting beneath leaves after loading

Course surfaces beneath tree canopies can turn black when a world loads. Placing vanilla blocks can temporarily repair the lighting.

## Expected behavior

- Lowered and raised course terrain retains normal tree shade across world saves and reloads, without manual block updates.
- Full base and cap cells still block light; partial cells admit it.
- Lighting must work before corner metadata is available to the renderer and on Minecraft's lighting worker thread.
- Existing course geometry and flags remain intact. Older saved terrain repairs its lighting automatically.

## Validation

The regression reproduced mismatched opacity between the server and lighting threads: lowered cells became opaque on the worker, while filled caps became transparent. Both now read the same saved `full_cell` block state without block entity access. Older corner data repairs that state on load. The client uses server lighting packets instead of recalculating light while chunk metadata is arriving.

All 165 headless GameTests and the build pass, including opacity before metadata loads, older-save repair, and grading undo/fill checks. The graphical canopy fixture checks all four materials, compares client/server light, and verifies identical uploaded meshes across two actual save/reopen cycles.

The existing graphical daylight, break/replace, cap, and planted-flag lighting checks also pass.

Grading's planned fills and undo records include the derived lighting state, preserving their existing conflict protection when a tile crosses the full-cell boundary.

Related baseline: [Terrain system](../course/terrain-system/spec.md).
