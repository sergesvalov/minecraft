# HalloumiTrader

**English** | [Русский](README_ru.md)

A Paper 26.3 plugin with wandering halloumi traders and Cypriot taverns that generate in the world on their own.

No client mods needed. Works for both Java and Bedrock players (via Geyser).

The default in-game texts are in Russian; change them in `config.yml`.

## Halloumi trader

- From time to time a **Halloumi trader** (a wandering trader with a gold name tag) shows up near one of the players.
- It has a single trade: **4 halloumi for 2 emeralds**, up to 16 times per trader.
- After 30 minutes the trader leaves (despawns), unless someone is trading with it at that moment.
- When it arrives, a chat message announces which player it came to.
- When a player opens its trade window, it hints where the nearest tavern is ("north-west, about 800 blocks away"). At most once a minute per player.

**Halloumi** is bread named "Халуми" with the description "Жареный кипрский сыр" (fried Cypriot cheese). It is more filling than bread: 8 hunger and 12.8 saturation, the same as steak. It doesn't stack with regular bread.

### When the trader comes

Every 20 minutes the plugin rolls:

1. If a halloumi trader (not counting tavern guests) is already in loaded chunks, no new one comes.
2. With a 50% chance the trader appears near a random player.
3. The player must be in the overworld (not the Nether or the End) and not in spectator mode.
4. The spot is on the surface, 10–20 blocks from the player: a solid block below, no water or lava, two free blocks of height. If no spot is found in 15 attempts, the visit is skipped until the next check.

## The "Halloumi" tavern

Taverns generate on their own in **new** overworld chunks, like villages. They don't appear in already explored areas.

- **Hard to find, but possible.** The world is split into 48×48-chunk squares (~770 blocks). Each square has at most one tavern, with a 60% chance. Wandering halloumi traders give hints.
- **Fits the surroundings.** A tavern is built only on flat natural ground (at most 3 blocks of height difference, no water, not on a village or a cliff). Its style depends on the biome:

  | Biome | Style |
  |---|---|
  | plains, meadow, forest, flower forest | oak, cobblestone, spruce roof |
  | taiga, snowy plains, grove | spruce, cobblestone, dark oak roof |
  | birch forest | birch, cobblestone |
  | savanna | acacia, terracotta |
  | desert | sandstone with jungle wood details |
  | cherry grove | cherry, stone bricks |
  | dark forest, pale garden | dark oak, mossy cobblestone, deepslate tile roof |
  | jungle | jungle wood, mossy cobblestone |

  Other biomes (oceans, mountains, swamps, badlands, etc.) get no taverns.
- **Inside**: a bar counter, barrels and a smoker along the back wall, two tables with chairs, lanterns, and a lectern with the "Меню" (menu) book by the entrance. A sign above the door reads «Таверна «Халуми»».
- **The innkeeper** always stands behind the counter. It doesn't move and can't be damaged. If it disappears (say, killed by a creative-mode player), a new one appears within seconds. When a dish sells out, the innkeeper restocks.
- **Guests.** While a player is within 48 blocks, wandering halloumi traders sometimes drop in, at most two at a time. They leave after 30 minutes.

### Innkeeper's menu: Cypriot meze

| Dish | What it is | Item | Per trade | Price |
|---|---|---|---|---|
| Халуми (halloumi) | Fried Cypriot cheese | bread | 4 | 2 emeralds |
| Сувлаки (souvlaki) | Grilled pork skewers | cooked porkchop | 3 | 3 |
| Шефталья (sheftalia) | Minced meat sausages with herbs | cooked rabbit | 4 | 2 |
| Клефтико (kleftiko) | Lamb slow-cooked in a clay oven | cooked mutton | 2 | 4 |
| Мусака (moussaka) | Aubergine, potato and mince bake | baked potato | 3 | 3 |
| Кефтедес (keftedes) | Fried meatballs with mint | steak | 4 | 2 |
| Куппепья (koupepia) | Vine leaves stuffed with rice and meat | dried kelp | 6 | 1 |
| Хориатики (horiatiki) | Village salad: tomato, cucumber, feta, olives | beetroot | 4 | 1 |
| Лукумадес (loukoumades) | Honey-syrup doughnuts | cookie | 6 | 1 |
| Кипрский кофе (Cyprus coffee) | Strong, brewed in a copper pot | potion: Speed, 60 s | 1 | 1 |

Names, descriptions, food values and prices are set in `tavern.menu`, where you can also add dishes.

## Commands

| Command | Who | What it does |
|---|---|---|
| `/halloumi tavern [style]` | **superadmin papa only** | Builds a tavern right in front of you (door 3 blocks to the north) with an innkeeper, sign and menu. Style comes from the biome or is given explicitly: `oak`, `spruce`, `birch`, `acacia`, `sandstone`, `cherry`, `dark_oak`, `jungle` |
| `/halloumi spawn` | ops | Summons a halloumi trader 2 blocks in front of you |
| `/halloumi random` | ops | Runs a random visit now, skipping the chance roll and the trader limit |
| `/halloumi give [amount]` | ops | Gives you halloumi (default 4, max 64) |
| `/halloumi count` | ops | How many traders are in loaded chunks and how many taverns are known |
| `/halloumi taverns` | ops | The five nearest taverns with door coordinates |
| `/halloumi reload` | ops | Reloads `config.yml`: visit timer, tavern generation, menu |

### Permissions

| Permission | Default | Description |
|---|---|---|
| `halloumi.admin` | ops | All commands except `tavern` |
| `halloumi.tavern` | nobody | Building a tavern by command |

In this project **papa is the superadmin** and is allowed more than the other admins. The other admins get the LuckPerms group `admin` with `*`, so on server start `scripts/auto-perms.sh` explicitly denies `halloumi.tavern` to group `admin` and grants it to papa.

## Configuration

File on the server: `/opt/minecraft/data/plugins/HalloumiTrader/config.yml`. It is created on first start; keys added in newer versions are appended automatically. Run `/halloumi reload` after editing.

| Key | Default | Description |
|---|---|---|
| `trader-name` | `Торговец халуми` | Name above the trader |
| `random-spawn.enabled` | `true` | Enable random visits |
| `random-spawn.check-interval-minutes` | `20` | How often to roll for a visit |
| `random-spawn.chance` | `0.5` | Visit chance per roll (0–1) |
| `random-spawn.max-active` | `1` | How many wandering traders can exist at once |
| `random-spawn.min-distance` / `max-distance` | `10` / `20` | Distance from the player, in blocks |
| `despawn-minutes` | `30` | Minutes until a trader (and a tavern guest) leaves |
| `announce-message` | `Торговец халуми пришёл к игроку %player%! …` | Chat message; empty string disables it |
| `trade.price-emeralds` / `amount` / `max-uses` | `2` / `4` / `16` | The halloumi trade |
| `halloumi.name` / `lore` / `nutrition` / `saturation` | `Халуми` / … / `8` / `12.8` | The halloumi item |
| `tavern.enabled` | `true` | Generate taverns in new chunks |
| `tavern.worlds` | `[world]` | Which worlds |
| `tavern.spacing-chunks` | `48` | Grid square size in chunks: bigger means rarer |
| `tavern.chance` | `0.6` | Chance of a tavern per square |
| `tavern.max-slope` | `3` | Max height difference under a tavern |
| `tavern.hints` | `true` | Traders hint at the nearest tavern |
| `tavern.innkeeper-name` | `Трактирщик` | Innkeeper's name |
| `tavern.innkeeper-max-uses` | `64` | Purchases per dish before the innkeeper restocks |
| `tavern.sign` | `Таверна «Халуми»` | Four lines of the sign above the door |
| `tavern.guests.max` / `chance` / `player-radius` | `2` / `0.04` / `48` | Guests: max, chance per check (every 10 s), distance to a player |
| `tavern.menu-title` / `menu-intro` | `Меню` / … | Title and first page of the menu book |
| `tavern.menu` | 9 dishes | Innkeeper's menu: `id`, `name`, `item`, `lore`, `nutrition`, `saturation`, `price`, `amount`; potion drinks also take `effect`, `effect-seconds`, `color` |

Wandering traders already in the world keep their old trades. The innkeeper picks up a new menu when the number of dishes changes or something sells out. The sign and the book are written once, when the tavern is first loaded.

Known taverns are stored in `data/plugins/HalloumiTrader/taverns.txt`.

## Build and deploy

The plugin is built and deployed by the same Jenkins pipeline as `WardenLog`:

```bash
bash scripts/build-plugin.sh HalloumiTrader
```

The script builds the jar with Maven in Docker (`maven:3.9-eclipse-temurin-25`) and puts `HalloumiTrader-<version>.jar` into `data/plugins/`. If a jar of that version already exists, the build is skipped. **To roll out changes, bump `<version>` in `pom.xml`.**

On deploy, Jenkins removes old `HalloumiTrader-*.jar` files on the server and copies the new one.

## How it works

- Traders are vanilla `WanderingTrader`s tagged in their `PersistentDataContainer`: `halloumitrader:halloumi_trader` (all halloumi traders), `halloumitrader:tavern_guest` (tavern guests), `halloumitrader:innkeeper`. The plugin cancels `VillagerAcquireTradeEvent` for them so vanilla trades are not added.
- Leaving is handled by the vanilla `despawnDelay`. For the innkeeper it is 0 (never leaves), and its AI is off.
- Food items are vanilla items with a `FOOD` component (Paper Data Component API), a name and a description. The coffee is a potion with a colour and an effect.
- Generation is a `BlockPopulator` (`TavernPopulator`). It runs on world generation threads and only touches the `LimitedRegion`. The grid square and its candidate chunk are derived from the seed, like vanilla `random_spread` structures. The main thread adds the sign, the book and the innkeeper every 10 seconds once the tavern chunk and its entities are loaded.
