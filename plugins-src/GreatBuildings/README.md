# GreatBuildings — Great buildings of the world

**English** | [Русский](README_ru.md)

Paper 26.3 plugin: famous buildings of the world at **1:1** scale (one block = one metre). Only the superadmin `papa` can build them, on command, right in front of himself.

No client mods needed. Works for Java and Bedrock players (through Geyser).

## Buildings

### Svetitskhoveli (`svetitskhoveli`)

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

## Commands

| Command | Who | What it does |
|---|---|---|
| `/gb list` (or just `/gb`) | everyone | Where the built buildings are and how far away |
| `/gb build <building>` | `papa` only | Shows the area the building will take and asks to confirm |
| `/gb confirm` | `papa` only | Builds it (the confirmation is valid for a minute) |
| `/gb undo` | `papa` only | Takes the last building down and restores the terrain |

The building goes **north** of the player with its entrance facing him. For Svetitskhoveli the player stands 4 blocks in front of the south gate. The yard is level with the ground under the player's feet. Everything in the site (134 × 123 blocks, up to 50 high) is replaced: hills are cut, hollows filled with earth, foundations laid under the walls.

The `greatbuildings.build` permission is off by default, even for operators. `RCON_CMDS_STARTUP` in `docker-compose.yml` grants it to `papa` on every server start.

## How it builds

To keep the Raspberry Pi responsive, it builds in steps:

1. Loads all chunks of the site asynchronously and keeps them loaded until done.
2. Reads the ground height and draws the plan (about 750 thousand cells) on another thread.
3. Places at most `blocks-per-tick` (4000) blocks per tick. Cells that already hold the right block, such as air above the ground, are skipped. The action bar shows the progress.
4. Writes the plaques and puts the book on the lectern, then records the building in `plugins/GreatBuildings/buildings.txt`.

`/gb undo` remembers what the last build replaced, but only until the server restarts. Chests that were in the site lose their contents, and undo can't bring them back.

## Settings (`config.yml`)

| Key | Default | Meaning |
|---|---|---|
| `blocks-per-tick` | 4000 | Blocks placed per tick |
| `checks-per-tick` | 30000 | Plan cells checked per tick |

## Adding a building

A building is a class that implements `Building` and draws itself into a `Plan` as block state strings (`minecraft:oak_stairs[facing=north,half=bottom]`). The plan doesn't depend on the server, so it can be rendered to images and checked without running Minecraft. Then register the building in `GreatBuildingsPlugin.onEnable()`.

## Build

```bash
bash scripts/build-plugin.sh GreatBuildings
```

Jenkins builds the plugin and copies `GreatBuildings-<version>.jar` to `/opt/minecraft/data/plugins/`.
