# HalloumiTrader

**English** | [Русский](README_ru.md)

A Paper 26.3 plugin that adds a wandering trader selling halloumi.

No client mods needed. Works for both Java and Bedrock players (via Geyser).

## In game

- From time to time a **Halloumi trader** (a wandering trader with a gold name tag) shows up near one of the players.
- It has a single trade: **4 halloumi for 2 emeralds**, up to 16 times per trader.
- After 30 minutes the trader leaves (despawns), unless someone is trading with it at that moment.
- When it arrives, a chat message announces which player it came to.

**Halloumi** is bread named "Халуми" with the description "Жареный кипрский сыр" (fried Cypriot cheese). It is more filling than bread: 8 hunger and 12.8 saturation, the same as steak. It doesn't stack with regular bread.

The default in-game texts are in Russian; change them in `config.yml`.

## When the trader comes

Every 20 minutes the plugin rolls:

1. If a halloumi trader is already in loaded chunks, no new one comes.
2. With a 50% chance the trader appears near a random player.
3. The player must be in the overworld (not the Nether or the End) and not in spectator mode.
4. The spot is on the surface, 10–20 blocks from the player: a solid block below, no water or lava, two free blocks of height. If no spot is found in 15 attempts, the visit is skipped until the next check.

All of this is configurable.

## Commands

All commands are for ops only (permission `halloumi.admin`).

| Command | What it does |
|---|---|
| `/halloumi spawn` | Summons the trader 2 blocks in front of you. For testing |
| `/halloumi random` | Runs a random visit now, skipping the chance roll and the trader limit |
| `/halloumi give [amount]` | Gives you halloumi (default 4, max 64) |
| `/halloumi count` | Shows how many halloumi traders are in loaded chunks |
| `/halloumi reload` | Reloads `config.yml` and restarts the visit timer |

## Configuration

File on the server: `/opt/minecraft/data/plugins/HalloumiTrader/config.yml`. It is created on first start. Run `/halloumi reload` after editing.

| Key | Default | Description |
|---|---|---|
| `trader-name` | `Торговец халуми` | Name above the trader |
| `random-spawn.enabled` | `true` | Enable random visits |
| `random-spawn.check-interval-minutes` | `20` | How often to roll for a visit |
| `random-spawn.chance` | `0.5` | Visit chance per roll (0–1) |
| `random-spawn.max-active` | `1` | How many traders can exist at once |
| `random-spawn.min-distance` / `max-distance` | `10` / `20` | Distance from the player, in blocks |
| `despawn-minutes` | `30` | Minutes until the trader leaves |
| `announce-message` | `Торговец халуми пришёл к игроку %player%! …` | Chat message; empty string disables it |
| `trade.price-emeralds` | `2` | Price in emeralds |
| `trade.amount` | `4` | Halloumi per trade |
| `trade.max-uses` | `16` | Trades per trader |
| `halloumi.name` | `Халуми` | Item name |
| `halloumi.lore` | `Жареный кипрский сыр` | Description lines |
| `halloumi.nutrition` / `saturation` | `8` / `12.8` | Hunger and saturation restored |

Price and item changes apply to new traders. A trader already in the world keeps its old trade.

## Build and deploy

The plugin is built and deployed by the same Jenkins pipeline as `WardenLog`:

```bash
bash scripts/build-plugin.sh HalloumiTrader
```

The script builds the jar with Maven in Docker (`maven:3.9-eclipse-temurin-25`) and puts `HalloumiTrader-<version>.jar` into `data/plugins/`. If a jar of that version already exists, the build is skipped. **To roll out changes, bump `<version>` in `pom.xml`.**

On deploy, Jenkins removes old `HalloumiTrader-*.jar` files on the server and copies the new one.

## How it works

- The trader is a vanilla `WanderingTrader`, tagged in its `PersistentDataContainer` (`halloumitrader:halloumi_trader`). The plugin cancels `VillagerAcquireTradeEvent` for tagged traders so vanilla trades are not added.
- Leaving is handled by the vanilla `despawnDelay`.
- Halloumi is `BREAD` with a `FOOD` component (Paper Data Component API), a name, a description and the `halloumitrader:halloumi` tag.
