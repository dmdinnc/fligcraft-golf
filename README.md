Fligcraft Golf
==============

Basic golf equipment and course blocks for Minecraft 1.21.1 (NeoForge).

Features
--------
- Clubs and balls with a charge/accuracy shot system.
- Tees, flags, fairway, rough, and putting green blocks.
- Driving range bay and ball tracker.
- Golf balls in all sixteen vanilla dye colors, with matching glowing outlines.

Requirements
------------
- JDK 21
- Minecraft 1.21.1
- NeoForge 21.1.219

Colored balls and tracker
-------------------------
Shapeless crafting: combine one golf ball of any color with one vanilla dye in
either crafting grid to produce one ball matching the dye. White dye changes it
back to white. White is the default, including balls in older worlds. Color
survives placing, shooting, world saves, scoring, and deletion/return. All sixteen variants appear
in the golf creative tab. A glowing ball uses its dye's outline color.

Right-click the Golf Helper to open the chest-style tracker, or use
`/golf_helper_manage`. Sneak-right-click keeps the wind/nearest-flag summary.
The tracker lists balls you placed or last hit, across all dimensions, with
45 balls per page and previous/refresh/next controls in the bottom row.
Hover a ball for coordinates, dimension, distance, strokes, state, and the
block at/below it. Loaded information refreshes once a second.

- Left-click a ball to close the menu and draw a colored particle line for five
  seconds. This works for loaded balls in your dimension within 128 blocks;
  otherwise a message explains why a path cannot be drawn. Only you see the line.
- Right-click a ball to delete it and return one ball of the same color. A full
  inventory drops the returned item at your feet. Players can manage their own
  balls without operator permissions; every action rechecks ownership on the server.

Tracking data belongs to the world/server, stored as
`data/fligcraft_golf_tracked_balls.dat` in each dimension's save folder. Unloaded
balls remain listed with their last known information. Deleting one queues entity
removal for its next load and immediately returns its item, without loading the
chunk. Deleted IDs stay saved to prevent an old chunk copy returning a second ball.
Existing balls become indexed when their chunks first load after upgrading.
Both server and clients need the updated mod for the tracker interface.

Club power tuning
-----------------
Club launch power can be tuned live with server settings. Defaults are all `1.0`,
preserving the original club behavior. The effective multiplier is
`globalPowerMultiplier * <club>PowerMultiplier`, including for the putter.
Each setting accepts values from `0.1` to `5.0`.

The mod automatically creates `fligcraft_golf-server.toml` in the world's
`serverconfig` folder when the world first loads (including newly created worlds).
Existing worlds also get the file on their next load if it is missing:

- Dedicated server: `<server>/<level-name>/serverconfig/fligcraft_golf-server.toml`
  (`level-name` defaults to `world`).
- Single player: `.minecraft/saves/<world>/serverconfig/fligcraft_golf-server.toml`
  (use your launcher's instance folder if different).

This world file is the only source of club settings. Commands always save to the
world running on the server that executes them. In multiplayer, that is the host's
world; a player's local config does not affect shots or commands. Single-player
worlds each have their own settings, which travel with the world save.
No instance-wide club config is generated or used. Any older
`config/fligcraft_golf-server.toml` is ignored; existing world files are preserved.
Leave `fligcraft_golf-common.toml` settings as they are; club tuning is separate.

```toml
[clubs]
globalPowerMultiplier = 1.0
driverPowerMultiplier = 1.0
hybridPowerMultiplier = 1.0
ironPowerMultiplier = 1.0
pitchingWedgePowerMultiplier = 1.0
sandWedgePowerMultiplier = 1.0
putterPowerMultiplier = 1.0
```

Commands (club names: `driver`, `hybrid`, `iron`, `pitching_wedge`, `sand_wedge`,
`putter`; `global` changes the factor applied to all clubs):

| Command | Access | Effect |
| --- | --- | --- |
| `/golf clubs show` | Everyone | Show individual and effective power multipliers. |
| `/golf clubs set driver 1.5` | Operator level 2 / single-player cheats | Set a club's power multiplier and save it. |
| `/golf clubs set global 1.25` | Operator level 2 / single-player cheats | Set the global factor and save it. |
| `/golf clubs reset driver` | Operator level 2 / single-player cheats | Reset that club to `1.0`, keeping the global factor. |
| `/golf clubs reset global` | Operator level 2 / single-player cheats | Reset the global factor to `1.0`. |
| `/golf clubs reset all` | Operator level 2 / single-player cheats | Reset and save all factors to `1.0`. |

Changes apply when the next ball is struck, including shots already charged but
not yet struck. Flying balls keep their existing trajectory. Commands work from
the server console too (omit the leading slash). Settings persist across restarts.
Editing the world TOML file also reloads settings while playing when NeoForge's
config watcher is enabled; commands do not depend on the watcher.

These settings multiply launch power, not exact carry or total distance. They also
affect height and airtime; charge, accuracy, surface, tee bonus, wind, and ball
physics still apply. Use driving-range carry measurements to tune each club.
For example, `/golf clubs set driver 1.5` with `global = 1.0` raises driver launch
power by 50%, without promising 50% more distance. A global factor of `1.25`
combined with a driver factor of `1.5` gives `1.875x` driver power.

Development
-----------
- Run client: `./gradlew runClient`
- Build jar: `./gradlew build` (output in `build/libs`)
- Server integration tests: `./gradlew runGameTestServer` (isolated world under
  `runs/gameTestServer`; test classes/resources are excluded from the mod jar)

Mod ID: `fligcraft_golf`
