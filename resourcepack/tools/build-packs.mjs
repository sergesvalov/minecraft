// Builds the optional "Halloumi" resource packs from resourcepack/src/textures and the plugin's config.yml:
//   dist/HalloumiPack.zip        Java pack, offered by the server (RESOURCE_PACK in docker-compose.yml)
//   dist/HalloumiPack.mcpack     Bedrock pack for Geyser (plugins/Geyser-Spigot/packs)
//   dist/halloumitrader.json     Geyser custom item mappings v2 (plugins/Geyser-Spigot/custom_mappings)
// and writes the pack URL and SHA1 into docker-compose.yml.
//
// Usage: node resourcepack/tools/build-packs.mjs
// After changing textures or dishes: bump PACK_VERSION, run this script, commit dist/ and docker-compose.yml.
//
// How the icons are matched: the plugin puts the custom_model_data string "halloumitrader:<id>" on every dish
// (Dish.modelData). The Java pack overrides the vanilla item definitions (assets/minecraft/items/<item>.json)
// with a select on that string, falling back to the vanilla model, so other items of the same kind and
// players who declined the pack see vanilla food. Geyser matches the same string with a "match" predicate.
// Zips are written uncompressed with fixed dates, so the same input always gives the same SHA1.

import { readFileSync, writeFileSync, mkdirSync, existsSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { crc32 } from 'node:zlib';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const PACK_VERSION = 2;
const NS = 'halloumitrader';
const REPO = 'sergesvalov/minecraft';
const JAVA_PACK_ID = 'b44afdae-f7b3-4fdf-8ffe-ba9128351354'; // RESOURCE_PACK_ID: lets clients replace the cached copy
const BEDROCK_HEADER_UUID = 'a1f9cadb-fc53-4df5-aa59-5b9018c781a5';
const BEDROCK_MODULE_UUID = '648a1da0-88b1-4b20-aa88-289a5c968c20';
// Java resource pack format of 26.3 is 97.1 (version.json of the client jar); the range avoids
// "made for a newer/older version" warnings around it.
const MIN_FORMAT = 88;
const MAX_FORMAT = 120;

const here = dirname(fileURLToPath(import.meta.url));
const root = join(here, '..', '..');
const textures = join(root, 'resourcepack', 'src', 'textures');
const dist = join(root, 'resourcepack', 'dist');
const configPath = join(root, 'plugins-src', 'HalloumiTrader', 'src', 'main', 'resources', 'config.yml');
const composePath = join(root, 'docker-compose.yml');

// Vanilla item definitions from the 26.3 client jar (assets/minecraft/items/*.json), used as the fallback.
// Most are a plain model; the potion also tints its contents with the potion colour.
const vanillaModel = (item) => item === 'potion'
  ? { type: 'minecraft:model', model: 'minecraft:item/potion', tints: [{ type: 'minecraft:potion', default: -13083194 }] }
  : { type: 'minecraft:model', model: `minecraft:item/${item}` };

// ---------------------------------------------------------------- dishes from config.yml
// Minimal reader for the parts we need: halloumi.name and tavern.menu[].id/name/item.
function readDishes() {
  const lines = readFileSync(configPath, 'utf8').split(/\r?\n/);
  const unquote = (s) => s.trim().replace(/^"(.*)"$/, '$1');
  const dishes = [];
  let section = null;
  let current = null;
  for (const line of lines) {
    if (/^\S/.test(line)) section = line.split(':')[0];
    if (section === 'halloumi') {
      const m = line.match(/^  name:\s*(.+)$/);
      if (m) dishes.push({ id: 'halloumi', name: unquote(m[1]), item: 'bread' });
    }
    if (section === 'tavern') {
      let m = line.match(/^    - id:\s*(.+)$/);
      if (m) { current = { id: unquote(m[1]) }; dishes.push(current); continue; }
      m = line.match(/^      (name|item):\s*(.+)$/);
      if (m && current) current[m[1]] = unquote(m[2]).replace(/^minecraft:/, '');
    }
  }
  const missing = dishes.filter((d) => !existsSync(join(textures, `${d.id}.png`)));
  for (const d of missing) console.warn(`no texture for "${d.id}", it keeps the vanilla look`);
  return dishes.filter((d) => !missing.includes(d) && d.item && d.name);
}

// ---------------------------------------------------------------- zip (store only, fixed timestamps)
function zip(files) {
  const local = [];
  const central = [];
  let offset = 0;
  const DOS_TIME = 0;
  const DOS_DATE = (0 << 9) | (1 << 5) | 1; // 1980-01-01
  for (const [name, data] of [...files].sort(([a], [b]) => a.localeCompare(b))) {
    const nameBuf = Buffer.from(name, 'utf8');
    const crc = crc32(data) >>> 0;
    const head = Buffer.alloc(30);
    head.writeUInt32LE(0x04034b50, 0);
    head.writeUInt16LE(20, 4);
    head.writeUInt16LE(0x0800, 6); // UTF-8 names
    head.writeUInt16LE(0, 8); // stored
    head.writeUInt16LE(DOS_TIME, 10);
    head.writeUInt16LE(DOS_DATE, 12);
    head.writeUInt32LE(crc, 14);
    head.writeUInt32LE(data.length, 18);
    head.writeUInt32LE(data.length, 22);
    head.writeUInt16LE(nameBuf.length, 26);
    head.writeUInt16LE(0, 28);
    local.push(head, nameBuf, data);

    const cen = Buffer.alloc(46);
    cen.writeUInt32LE(0x02014b50, 0);
    cen.writeUInt16LE(20, 4);
    cen.writeUInt16LE(20, 6);
    cen.writeUInt16LE(0x0800, 8);
    cen.writeUInt16LE(0, 10);
    cen.writeUInt16LE(DOS_TIME, 12);
    cen.writeUInt16LE(DOS_DATE, 14);
    cen.writeUInt32LE(crc, 16);
    cen.writeUInt32LE(data.length, 20);
    cen.writeUInt32LE(data.length, 24);
    cen.writeUInt16LE(nameBuf.length, 28);
    cen.writeUInt32LE(offset, 42);
    central.push(cen, nameBuf);
    offset += head.length + nameBuf.length + data.length;
  }
  const cenBuf = Buffer.concat(central);
  const end = Buffer.alloc(22);
  end.writeUInt32LE(0x06054b50, 0);
  end.writeUInt16LE(files.size, 8);
  end.writeUInt16LE(files.size, 10);
  end.writeUInt32LE(cenBuf.length, 12);
  end.writeUInt32LE(offset, 16);
  return Buffer.concat([...local, cenBuf, end]);
}

const json = (obj) => Buffer.from(JSON.stringify(obj, null, 2) + '\n', 'utf8');
const png = (id) => readFileSync(join(textures, `${id}.png`));

// ---------------------------------------------------------------- Java pack
function javaPack(dishes) {
  const files = new Map();
  files.set('pack.mcmeta', json({
    pack: {
      description: [{ text: 'Таверна «Халуми»', color: 'gold' }, { text: '\nИконки кипрских блюд', color: 'gray' }],
      min_format: MIN_FORMAT,
      max_format: MAX_FORMAT,
    },
  }));
  files.set('pack.png', png('halloumi'));

  const byItem = new Map();
  for (const d of dishes) {
    if (!byItem.has(d.item)) byItem.set(d.item, []);
    byItem.get(d.item).push(d);
    files.set(`assets/${NS}/models/item/${d.id}.json`, json({
      parent: 'minecraft:item/generated',
      textures: { layer0: `${NS}:item/${d.id}` },
    }));
    files.set(`assets/${NS}/textures/item/${d.id}.png`, png(d.id));
  }
  for (const [item, list] of byItem) {
    files.set(`assets/minecraft/items/${item}.json`, json({
      model: {
        type: 'minecraft:select',
        property: 'minecraft:custom_model_data',
        index: 0,
        cases: list.map((d) => ({ when: `${NS}:${d.id}`, model: { type: 'minecraft:model', model: `${NS}:item/${d.id}` } })),
        fallback: vanillaModel(item),
      },
    }));
  }
  return zip(files);
}

// ---------------------------------------------------------------- Bedrock pack + Geyser mappings
function bedrockPack(dishes) {
  const files = new Map();
  const version = [1, PACK_VERSION, 0];
  files.set('manifest.json', json({
    format_version: 2,
    header: {
      name: 'Halloumi Tavern',
      description: 'Иконки кипрских блюд таверны «Халуми»',
      uuid: BEDROCK_HEADER_UUID,
      version,
      min_engine_version: [1, 21, 0],
    },
    modules: [{ type: 'resources', uuid: BEDROCK_MODULE_UUID, version }],
  }));
  files.set('pack_icon.png', png('halloumi'));
  const textureData = {};
  for (const d of dishes) {
    textureData[`${NS}_${d.id}`] = { textures: [`textures/items/${NS}/${d.id}`] };
    files.set(`textures/items/${NS}/${d.id}.png`, png(d.id));
  }
  files.set('textures/item_texture.json', json({
    resource_pack_name: 'halloumi_tavern',
    texture_name: 'atlas.items',
    texture_data: textureData,
  }));
  return zip(files);
}

function geyserMappings(dishes) {
  const items = {};
  for (const d of dishes) {
    (items[`minecraft:${d.item}`] ??= []).push({
      type: 'definition',
      model: `minecraft:${d.item}`,
      bedrock_identifier: `${NS}:${d.id}`,
      display_name: d.name,
      bedrock_options: { icon: `${NS}_${d.id}`, allow_offhand: true },
      predicate: { type: 'match', property: 'custom_model_data', value: `${NS}:${d.id}`, index: 0 },
    });
  }
  return json({ format_version: 2, items });
}

// ---------------------------------------------------------------- main
const dishes = readDishes();
mkdirSync(dist, { recursive: true });
const java = javaPack(dishes);
writeFileSync(join(dist, 'HalloumiPack.zip'), java);
writeFileSync(join(dist, 'HalloumiPack.mcpack'), bedrockPack(dishes));
writeFileSync(join(dist, 'halloumitrader.json'), geyserMappings(dishes));
writeFileSync(join(dist, 'pack-version.txt'), `${PACK_VERSION}\n`);

const sha1 = createHash('sha1').update(java).digest('hex');
// Served straight from the public repo: the committed zip and this SHA1 land on main together.
// No GitHub release, so the Jenkins build needs no API token.
const url = `https://raw.githubusercontent.com/${REPO}/main/resourcepack/dist/HalloumiPack.zip`;
let compose = readFileSync(composePath, 'utf8');
const set = (k, v) => {
  const re = new RegExp(`^(\\s+${k}: ).*$`, 'm');
  if (!re.test(compose)) throw new Error(`${k} not found in docker-compose.yml`);
  compose = compose.replace(re, `$1"${v}"`);
};
set('RESOURCE_PACK', url);
set('RESOURCE_PACK_SHA1', sha1);
set('RESOURCE_PACK_ID', JAVA_PACK_ID);
writeFileSync(composePath, compose);

console.log(`dishes: ${dishes.map((d) => d.id).join(', ')}`);
console.log(`Java pack v${PACK_VERSION}: ${java.length} bytes, sha1 ${sha1}`);
console.log(`URL: ${url}`);
