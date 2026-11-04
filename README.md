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

- Minecraft 26.3 or newer
- Fabric Loader 0.19.5 or newer
- [Fabric API](https://modrinth.com/mod/fabric-api) (server side, any version for your Minecraft version)
- Java 25

## Building

```
gradlew build
```
The jar is written to `build/libs/`. `gradlew runGameTest` runs the server game tests. `gradlew vanillaClientTest` starts a real dedicated server with the mod and joins it from a client that doesn't have it.

## License

MIT
