# GreatBuildings — Great buildings of the world

**English** | [Русский](README_ru.md)

Paper 26.3 plugin: famous buildings of the world at **1:1** scale (one block = one metre). Only the superadmin `papa` can build them, on command, right in front of himself.

No client mods needed. Works for Java and Bedrock players (through Geyser).

## Buildings by Country

### Georgia (`georgia`)

#### Svetitskhoveli (`svetitskhoveli`)

The cathedral in Mtskheta (Georgia), near Tbilisi, with its fortress enclosure. Oriented like the real one: the altar to the east, the main gate of the enclosure to the south.

Dimensions from the Orthodox Encyclopedia ("Светицховели", pravenc.ru) and Wikipedia:

| | Real | In game |
|---|---|---|
| Cathedral length with the entrance portico | 55.4 m | 55 blocks |
| Width at the east facade | 25 m | 25 blocks (transept arms stick out 1 more) |
| Outer height | 48.5 m | 49 blocks with the cross |
| Enclosure | 121.6 × 110.5 m | 122 × 111 blocks |
| Enclosure towers | 8: 6 round, 2 square | 8: 6 round, 2 square (gate towers) |

From the sources:

- **Plan.** Cross-domed church elongated west to east. The dome stands on 4 piers. The west arm has one more pair of piers, so each side has two unequal arches. A deep apse with the bishops' bench along its wall and two small side apses. Three entrances: west (main, with a portico), south and north.
- **Facades.** Blind arcading. The east facade has five arches rising to the centre and two niches that are triangular in plan. The apse window has a red stone frame.
- **Stone.** Light and dark yellow-red sandstone with green tuff. The north facade is almost all green tuff, and so are the drum and the cone of the dome.
- **Inside.** The Life-giving Pillar (painted, under the big arch on the south side of the west arm), the small domed Holy Sepulchre chapel at the south wall, the font to the right of the entrance, royal tombs before the altar, a low stone altar screen, frescoed plaster.
- **Enclosure.** Two-tier walls: a solid 5 m wall below and a fighting walkway with loopholes above. Cut stone mixed with river stone, brick on top. The gate is on the south side, and the west side has a gate with a belfry (the bell can be rung).

What the sources don't give was filled in to match, within the known dimensions. This covers eave and ridge heights, a 13 m drum with 12 windows (usual for Georgian domes), tower sizes, and the cypresses and lanterns in the yard. Materials the sources don't name (roofs, floor, portico) follow the walls, so they are sandstone too.

Name plaques are in Georgian (სვეტიცხოველი, at the gates and on the portico above the main entrance) and in Russian (at the gates). A lectern by the west entrance holds a book about the cathedral's history.

The Java client draws Georgian letters with its built-in Unifont. A Bedrock client whose font lacks them may show boxes instead.

#### Bagrati Cathedral (`bagrati`)

Cathedral of the Dormition atop Ukimerioni Hill in Kutaisi (Georgia). Consecrated in 1003 by King Bagrat III; site of the coronation of King David IV the Builder in 1089.

Dimensions from the Orthodox Encyclopedia ("Баграта храм", pravenc.ru) and architectural surveys:

| | Real | In game |
|---|---|---|
| Cathedral length with the west portico | ~44–45 m | 45 blocks |
| Width across transept conchs | ~34 m (~38 m with south portico) | 34 blocks (38 blocks with portico) |
| West arm width (nave and aisles) | ~22 m | 21 blocks |
| Outer height | ~35 m (~38 m with the cross) | 38 blocks with the cross |
| Ukimerioni hill terrace | — | 71 × 67 blocks |

From the sources:

- **Plan.** Developed triconch: three semicircular conchs (east sanctuary, north and south transept arms) and an extended western arm with nave and side aisles. The dome rests on four free-standing cruciform piers linked by high transverse arches.
- **Porticos.** The famous 1030s southern open portico (ichkari): two-tier open arcade with twin carved arches, columns, vaulted ceiling, and ceremonial steps. Vaulted western portico protecting the triple entrance.
- **Facades and drum.** 16-faceted cylindrical drum with 16 narrow arched windows and blind arcade moldings. East facade features three sanctuary windows and two deep triangular niches.
- **Roof.** Distinctive turquoise copper patina (oxidized copper) conical dome and gabled roofs, faithful to the 2012 restoration.
- **Inside.** Semicircular stone synthronon in the apse, chiseled chancel barrier, altar with candles, the royal coronation seat of David the Builder at the southwest pier, western choir gallery on the second tier with stone stairs, hanging chandelier beneath the dome.
- **Surroundings.** Ukimerioni hill plateau: ruins of the ancient fortress wall and round citadel tower, foundations of the 6th-century basilica, stone paths, cypresses, and lanterns.

The builder stands on the southern plaza of the hill and faces north — looking directly at the ceremonial South Portico and facade.

Name plaques in Georgian (`ბაგრატის ტაძარი`) and Russian (`Храм Баграта`). A lectern by the west entrance holds a book about the cathedral's history.

### Cyprus (`cyprus`)

#### Paphos Castle (`paphos`)

Medieval coastal fortress on the western mole of Kato Paphos harbor in Cyprus. Originally built by Byzantines and Lusignans (13th century), dismantled by Venetians in 1570, and reconstructed in 1592 by Ottoman governor Ahmet Pasha. UNESCO World Heritage Site.

Dimensions from historical and archaeological surveys of the Department of Antiquities of Cyprus:

| | Real | In game |
|---|---|---|
| Stone fortress footprint | ~33–40 × 20–23 m | 33 × 23 blocks |
| Lower roof deck height with battlements | ~11–12 m | 11–13 blocks |
| Upper keep height with beacon | ~15–16 m | 16–20 blocks |
| Seawater moat and bridge | moat width ~8 m | 8 blocks, stone arched bridge |
| Site with harbor mole and sea | — | 55 × 59 blocks |

From the sources:

- **Fortress and masonry.** Squat, heavy rectangular keep built of warm Cypriot limestone (`mud_bricks`, `packed_mud`, `smooth_sandstone`) with walls up to 3 metres thick.
- **Moat and bridge.** The landward approach is protected by a seawater moat (`water`), crossed by an arched stone bridge.
- **Entrance and 1592 inscription.** Single arched portal with heavy dark oak double doors. Set above the gate is the authentic white marble slab (`calcite`) commemorating the 1592 Ottoman reconstruction by Ahmet Pasha.
- **Ground floor.** Central barrel-vaulted hall dividing two wings. West wing houses Ottoman prison dungeons with heavy iron bars (`iron_bars`), chains, and bunks. East wing contains the British salt warehouse (smooth quartz blocks and barrels) and armory/guardroom.
- **Roof deck and artillery.** A wide stone staircase leads to the spacious open terrace. Along the parapet are **12 cannon embrasures (ramparts)** mounting coastal defense cannons (polished blackstone and dispensers) pointing out to sea and harbor approaches.
- **Upper keep.** Rising above the roof deck is a secondary tower housing the garrison guardhouse and a small Ottoman prayer chamber (mosque) with carpets and a mihrab niche. Topped by an upper lookout terrace and signal beacon/flagpole.
- **Surroundings.** The western breakwater of Paphos harbor: paved promenade with mooring bollards, Mediterranean palms, lanterns, and rocky sea breakwaters.

The builder stands on the harbor promenade and faces north — looking directly across the stone bridge over the seawater moat into the castle gate.

Name plaques in Greek (`Κάστρο της Πάφου`) and Russian (`Пафосский замок`). A lectern by the bridge holds a book about the castle's history.

#### Kourion Ancient Amphitheatre (`kourion`)

Greco-Roman theatre on the coastal cliff of Kourion (near Limassol, Cyprus), originally built in the 2nd century BC and expanded in the 2nd century AD.

- **Orchestra and cavea.** Semicircular 17 m limestone orchestra with a marble centre mosaic, surrounded by tiered limestone seating (`cavea`) rising to 17 m with wide radial staircases (2+ blocks wide) and upper gallery.
- **Stage and parodoi.** Two-tier stage building (`skene`) wings with colonnade and wide arched open side entrances (`parodoi`, 4×4 blocks).
- **Surroundings.** Paved southern approach plaza, Mediterranean cypresses, bilingual Greek (`Αρχαίο Θέατρο Κουρίου`) and Russian (`Амфитеатр Куриона`) plaques, and a lectern with a history book.

#### Statue of Apollo Hylates (`apollo`)

Monumental statue and sacred courtyard of Apollo Hylates ("Apollo of the Woodland") at the Sanctuary of Kourion, Cyprus.

- **Sanctuary court.** Paved temenos enclosed by a low limestone wall and Doric colonnade with a sacrificial altar.
- **Pedestal and statue.** Stepped limestone and marble pedestal bearing a monumental (~9 m) reconstruction of Apollo in bronze, gilded copper, and stone — holding a golden lyre and bow, with quiver and laurel wreath.
- **Surroundings.** Sacred cypress grove, bilingual Greek (`Απόλλων Υλάτης`) and Russian (`Аполлон Гилат`) plaques, and a lectern with a history book.

### Israel (`israel`)

#### Second Jerusalem Temple (`temple`)

Herod's Temple in Jerusalem at **1:1** scale in its full splendour, built with walkable interiors and 2+ block wide stairs and passages throughout.

- **Court of Women (`62 × 62 m`).** Surrounded by mud-brick limestone (`mud_bricks`, `packed_mud`) walls and white marble colonnades with four corner chambers (Nazarites, Wood, Lepers, Oil) and 4-block wide doorways.
- **Nicanor Gate and Court of Priests.** 12-block wide semicircular-inspired steps leading through the Corinthian bronze Nicanor Gate into the Court of Israel and Court of Priests, featuring the **14 × 14 m** unhewn stone Altar of Burnt Offering with a 4-block ramp and the bronze Laver (`Kiyor`).
- **Sanctuary (`Ulam`, `Hekhal`, `Holy of Holies`).** Steps rise to the **48 × 48 m** monumental Ulam facade. Inside the 20 m Hekhal stand the Golden Menorah, Table of Showbread, and Incense Altar. A 2-block wide passage through the double veil (`Parokhet`) leads into the **10 × 10 × 10 m** Holy of Holies with the Foundation Stone (`Even ha-Shetiyah`), flanked by 3-story side chambers connected by double doors and 2-block-wide interior staircases.
- **Surroundings.** Paved entrance plaza, cypresses, bilingual Hebrew (`בית המקדש`) and Russian (`Иерусалимский Храм`) plaques, and a lectern with a history book.

## Commands

| Command | Who | What it does |
|---|---|---|
| `/gb list [country]` (or just `/gb`) | everyone | Where built buildings are, grouped by country (with clickable `[ТП]` button for `papa`) |
| `/gb build <building>` or `/gb build <country> <building>` | `papa` only | Shows the area in front of you and asks to confirm (with country tab-completion) |
| `/gb confirm` | `papa` only | Builds it (the confirmation is valid for a minute) |
| `/gb undo` | `papa` only | Takes the last building down and restores the terrain |
| `/gb tp <building>` | `papa` only | Teleports to a built building |

The building is rotated in 90° increments based on where you are looking (`Placement.rotationFromYaw`) and placed **2–6 blocks in front of your face** with its entrance facing you. The yard is level with the ground under the player's feet. Everything in the site is replaced: hills are cut, hollows filled with earth, foundations laid under the walls. All stairs and passages are at least 2 blocks wide so characters can walk through every room comfortably.

The `greatbuildings.build` permission is off by default, even for operators. `RCON_CMDS_STARTUP` in `docker-compose.yml` grants it to `papa` on every server start.

## How it builds

To keep the Raspberry Pi responsive, it builds in steps:

1. Loads all chunks of the site asynchronously and keeps them loaded until done.
2. Reads the ground height and draws the plan on another thread.
3. Places at most `blocks-per-tick` (4000) blocks per tick. Cells that already hold the right block are skipped. The action bar shows the progress.
4. Writes the plaques and puts the book on the lectern, then records the building in `plugins/GreatBuildings/buildings.txt`.

`/gb undo` remembers what the last build replaced, but only until the server restarts.

## Settings (`config.yml`)

| Key | Default | Meaning |
|---|---|---|
| `blocks-per-tick` | 4000 | Blocks placed per tick |
| `checks-per-tick` | 30000 | Plan cells checked per tick |

## Architecture & Adding a Building

- `model`: `Building`, `Country`, `Face`, `Plan` (with `doubleDoor`, `wallSign`, `lectern`), `States`, `Terrain`.
- `buildings`: `BuildingCatalog` (central registry of all buildings), `Shapes` (shared `levelGround`, `foundation`, `clearColumn`, `cypress`, `plaquePillar`).
- `engine`: `BuildManager`, `BuildJob`, `Placement`.
- `storage`: `BuildingRegistry` (`buildings.txt` persistence).
- `command`: `GbCommand`.

To add a building, implement `Building` in its country subpackage (`georgia`, `cyprus`, `israel`, etc.) and register it in `BuildingCatalog`. The `Plan` does not depend on Bukkit, so it can be snapshot-tested offline via `BuildingSnapshotTest`.

## Build

```bash
bash scripts/build-plugin.sh GreatBuildings
```

Jenkins builds the plugin and copies `GreatBuildings-<version>.jar` to `/opt/minecraft/data/plugins/`.
