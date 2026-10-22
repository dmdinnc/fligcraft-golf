# Change: Temporary club sounds from vanilla audio

Give each of the six clubs a recognizable hit using existing Minecraft sounds. No third-party recordings or bundled copies of vanilla audio are needed.

- Play a short swing plus a low metallic impact for full-shot clubs; the putter gets a quiet metallic tap. All six clubs are metal.
- Use the pitching wedge's chain impact as the common metallic sound, with slight pitch variations: driver 0.60, hybrid 0.67, iron 0.74, pitching wedge 0.81 (raised from 0.70), sand wedge 0.88, and quiet putter 0.70. Longer full-shot clubs sound lower; sand wedge sounds highest. Lower charges are quieter.
- Layer a sand crunch onto any club striking a ball resting on bunker sand or vanilla sand/red sand, including sculpted slopes and extensions. A tee or water takes precedence over the sand underneath.
- Play only when the server actually launches the ball, at the ball's position, for the hitter and nearby players. Charging, arming, missing a target, or repeating an already consumed shot makes no hit sound.
- Use the Players sound category. No changes to shot balance or saved data.

Related baseline: [Clubs and shots](../play/clubs-and-shots/spec.md).

## Validation

`build runGameTestServer` passed with all 181 required tests. Three sound regressions exercise actual strikes, the shared metallic impact with distinct pitches increasing toward the shorter full-shot clubs, volume scaling, sand on sculpted extensions and ordinary sand, tee/water exclusions, silent arming, removed targets, and repeated actions. Server broadcasts include the hitter with no excluded player. Headless tests check emitted sound events; the final mix still needs in-game listening.
