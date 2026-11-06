# Crouch to Set Respawn

Sleeping in a bed no longer moves your spawn point. **Sneak + use a bed** to set your respawn there, day or night.

Skip the night in a bed at a far-off base without losing your home spawn, and stop accidentally moving your spawn to a bed you only meant to sleep in.

**Server-side only.** Install it on the server (or in single player). Players join with a plain vanilla client and nothing to install.

## How it works

| You do | What happens |
|---|---|
| Use a bed at night | You sleep as normal. Your spawn doesn't move. |
| Use a bed during the day | "You can only sleep at night", as in vanilla. Your spawn doesn't move. |
| **Sneak + use** a bed at night | "Respawn point set", then you sleep. |
| **Sneak + use** a bed during the day | "Respawn point set". |
| Sneak + use a bed while holding a block | The block gets placed, as in vanilla. |

Everything else stays vanilla:
- Beds still explode in the Nether and the End, sneaking or not.
- Too far away, obstructed, occupied, and monsters nearby all give the usual messages. With monsters nearby, a sneak + use still sets your spawn (vanilla sets spawn before that check too), you just can't sleep.
- Respawn anchors, the sleep percentage, phantoms, and what happens when your spawn bed is broken are untouched.

### Holding items
Vanilla only lets you use a bed while sneaking if both hands are empty. By default this mod lets a sneak + use set your spawn while you're holding things like a shield, sword, torch or map. Items that actually do something to a bed keep doing it: blocks are placed, and bone meal, flint and steel and buckets work as normal. You can turn this off in the config.

### Finding out about it
New players won't know about sneaking, so the first time a normal bed use skips setting your spawn, a popup says **"Bed not set as spawn: Sneak + use to set respawn"**. Vanilla labels every popup "Advancement Made!", but this one isn't a real advancement: it never shows up in the advancements screen or chat. After that first time it's a short line above the hotbar instead, at most once a night.

### Undercover Sleeper
Set your spawn with sneak + use for the first time to earn **Undercover Sleeper**: *"Your spawn point has been compromised."* It's a real advancement in the Adventure tab, under Sweet Dreams.

## Config

`config/crouchtosetrespawn.properties` is created on first start. Restart the server after changing it.

| Setting | Default | What it does |
|---|---|---|
| `sneak_with_items_sets_spawn` | `true` | Sneak + use sets spawn while holding items. `false` is the vanilla rule: both hands must be empty. |
| `show_spawn_hint_popup` | `true` | The "Sneak + use to set respawn" reminder on a normal bed use. |

## Requirements

- [Fabric API](https://modrinth.com/mod/fabric-api) on the server (any version for your Minecraft version)
- The jar for your Minecraft version:

| Jar | Minecraft | Fabric Loader | Java |
|---|---|---|---|
| `crouchtosetrespawn-mc26.3` | 26.3 and newer | 0.19.3+ | 25 |
| `crouchtosetrespawn-mc26.2` | 26.2 | 0.18.4+ | 25 |
| `crouchtosetrespawn-mc26.1-26.1.2` | 26.1 – 26.1.2 | 0.18.4+ | 25 |
| `crouchtosetrespawn-mc1.21.11` | 1.21.11 | 0.17.3+ | 21 |
| `crouchtosetrespawn-mc1.21.9-1.21.10` | 1.21.9 – 1.21.10 | 0.17.3+ | 21 |
| `crouchtosetrespawn-mc1.21.5-1.21.8` | 1.21.5 – 1.21.8 | 0.17.3+ | 21 |
| `crouchtosetrespawn-mc1.21.2-1.21.4` | 1.21.2 – 1.21.4 | 0.17.3+ | 21 |
| `crouchtosetrespawn-mc1.21-1.21.1` | 1.21 – 1.21.1 | 0.17.3+ | 21 |
| `crouchtosetrespawn-mc1.20.5-1.20.6` | 1.20.5 – 1.20.6 | 0.17.3+ | 21 |
| `crouchtosetrespawn-mc1.20.2-1.20.4` | 1.20.2 – 1.20.4 | 0.17.3+ | 17 |
| `crouchtosetrespawn-mc1.20-1.20.1` | 1.20 – 1.20.1 | 0.17.3+ | 17 |
| `crouchtosetrespawn-mc1.19-1.19.4` | 1.19 – 1.19.4 | 0.17.3+ | 17 |
| `crouchtosetrespawn-mc1.17-1.18.2` | 1.17 – 1.18.2 | 0.17.3+ | 16 (17 for 1.18) |

Every feature works the same on every version.

## Building

One source tree builds every Minecraft version ([Stonecutter](https://stonecutter.kikugie.dev/)); `release-groups.json` decides which versions share a jar.

```
gradlew build
```
Each version's jar is written to `versions/<minecraft>/build/libs/`. Tests:
- `gradlew :<minecraft>:runScenarioTest` runs the bed scenarios in a real dedicated server on that version (every version).
- `gradlew :26.3:runGameTest` runs the 50 server game tests on the newest version.
- `gradlew vanillaClientTest` starts a real dedicated server with the mod and joins it from a client that doesn't have it.
- `gradlew verifyGroupClasses` checks that every version in a release group compiles to identical classes.

## License

MIT
