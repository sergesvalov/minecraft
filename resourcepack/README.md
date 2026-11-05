# Halloumi resource pack

**English** | [Русский](README_ru.md)

Optional icons for the tavern dishes of the [HalloumiTrader](../plugins-src/HalloumiTrader/README.md) plugin: halloumi and the 9 meze dishes, 16×16, drawn in the vanilla item style.

- **Java**: the server offers the pack on join. A player can decline it; the dishes then look like the vanilla items they are based on (bread, cooked porkchop, cookie…).
- **Bedrock** (via Geyser): the pack is sent together with Geyser's own pack. Geyser's `force-resource-packs: true` is on, so Bedrock players can't decline it. It is ~8 KB.

## Layout

| Path | What it is |
|---|---|
| `tools/make-icons.mjs` | Draws the icons into `src/textures/`. No dependencies; edit a shape there and rerun it |
| `src/textures/<id>.png` | Icons, one per dish `id` from `tavern.menu` (plus `halloumi`) |
| `tools/build-packs.mjs` | Builds everything in `dist/` and writes the pack URL, SHA1 and ID into `docker-compose.yml` |
| `dist/HalloumiPack.zip` | Java pack |
| `dist/HalloumiPack.mcpack` | Bedrock pack, goes to `plugins/Geyser-Spigot/packs/` |
| `dist/halloumitrader.json` | Geyser custom item mappings (format v2), go to `plugins/Geyser-Spigot/custom_mappings/` |
| `tools/publish-release.sh` | Uploads the Java zip to the GitHub release `resourcepack-v<N>` |

## How the icons are matched

The plugin puts the `custom_model_data` string `halloumitrader:<id>` on every dish.

- The Java pack replaces the vanilla item definitions of the base items (`assets/minecraft/items/bread.json`, …) with a `select` on that string. Any other value falls back to the vanilla model, so ordinary bread stays ordinary.
- The Geyser mapping matches the same string with a `match` predicate and shows the Bedrock texture `halloumitrader_<id>`.

Items bought before plugin 1.2 have no `custom_model_data` and keep the vanilla look.

## Changing icons or dishes

```bash
node resourcepack/tools/make-icons.mjs     # only if the drawings changed
# bump PACK_VERSION in resourcepack/tools/build-packs.mjs
node resourcepack/tools/build-packs.mjs
git add resourcepack docker-compose.yml
```

Always bump `PACK_VERSION` when the content changes: a published release is never overwritten, and Bedrock clients cache packs by version. A new dish without a texture keeps the vanilla look.

On push, Jenkins:

1. Runs `publish-release.sh` with the `git-access` token. It creates the release `resourcepack-v<N>` and uploads the zip, or does nothing if that exact zip is already there. If it fails, the build is marked unstable and the deploy continues.
2. Copies the `.mcpack` and the mapping into the Geyser folders on the server.
3. Restarts the server through `docker compose up -d` because `docker-compose.yml` changed.

The zips are written uncompressed with fixed dates, so the same sources always give the same SHA1.
