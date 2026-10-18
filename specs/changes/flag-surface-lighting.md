# Change: Light planted flags above the course surface

Flags planted on raised greens currently sample light inside their solid supporting tile and can appear almost black in daylight.

## Expected behavior

- The pole, cloth, and cup use light at their visible positions, including tiles raised through the upper cap to 32/16.
- Lowering, raising, planting, and retrieving a flag update its appearance immediately.
- Flags still respond to shade and nearby light sources, using their existing textures and shape.
- Terrain retains its cached chunk mesh. Flag ownership, saves, collision, and scoring stay with the original course tile.

## Validation

The graphical lighting fixture reproduces zero skylight on the original flag's side faces in full daylight. With the fix, it checks lowered (8/16), full-height (16/16), raised (20/16), fully extended (32/16), and sloped greens. It also checks the cup, shade, nearby block light, render bounds, and retrieval. Screenshots confirm the existing red cloth and light pole render correctly.

The flag uses cached geometry in a dedicated renderer with per-vertex world lighting. Course surfaces keep their cached chunk geometry. No saved data or server physics changes are needed.

Verified with `clientLightingTest` and `runGameTestServer build`: the graphical fixture and all 162 headless GameTests pass.

Related baseline: [Flags and cups](../course/flags-and-cups/spec.md).
