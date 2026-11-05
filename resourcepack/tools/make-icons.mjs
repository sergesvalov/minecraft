// Generates 16x16 Minecraft-style item icons for the tavern dishes.
// Usage: node resourcepack/tools/make-icons.mjs [outDir]   (default: resourcepack/src/textures)
// No dependencies: shapes are rasterised, shaded like vanilla items (dark outline, light top-left,
// darker bottom-right, slight per-pixel noise) and written as PNG.

import { writeFileSync, mkdirSync } from 'node:fs';
import { deflateSync } from 'node:zlib';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const N = 16;
const here = dirname(fileURLToPath(import.meta.url));
const outDir = process.argv[2] ?? join(here, '..', 'src', 'textures');

// ---------------------------------------------------------------- colour helpers
const hex = (h) => [parseInt(h.slice(1, 3), 16), parseInt(h.slice(3, 5), 16), parseInt(h.slice(5, 7), 16)];
const clamp = (v) => Math.max(0, Math.min(255, Math.round(v)));
const mul = (c, k) => c.map((v) => clamp(v * k));
const mix = (a, b, t) => a.map((v, i) => clamp(v + (b[i] - v) * t));
const WHITE = [255, 255, 255];

function noise(x, y, seed) {
  let h = (x * 374761393 + y * 668265263 + seed * 2147483647) | 0;
  h = Math.imul(h ^ (h >>> 13), 1274126177);
  return ((h ^ (h >>> 16)) & 0xffff) / 0xffff; // 0..1
}

// ---------------------------------------------------------------- shapes (sets of pixels)
const key = (x, y) => y * N + x;
const inside = (x, y) => x >= 0 && y >= 0 && x < N && y < N;

function pixels(test) {
  const set = new Set();
  for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) if (test(x + 0.5, y + 0.5, x, y)) set.add(key(x, y));
  return set;
}
const rect = (x0, y0, x1, y1) => pixels((_, __, x, y) => x >= x0 && x <= x1 && y >= y0 && y <= y1);
const disc = (cx, cy, r) => pixels((px, py) => (px - cx) ** 2 + (py - cy) ** 2 <= r * r);
const ellipse = (cx, cy, rx, ry, part = 'all') =>
  pixels((px, py) => ((px - cx) / rx) ** 2 + ((py - cy) / ry) ** 2 <= 1 &&
    (part === 'all' || (part === 'top' ? py <= cy : py >= cy)));
function capsule(x0, y0, x1, y1, r) {
  return pixels((px, py) => {
    const dx = x1 - x0, dy = y1 - y0;
    const t = Math.max(0, Math.min(1, ((px - x0) * dx + (py - y0) * dy) / (dx * dx + dy * dy)));
    return (px - x0 - t * dx) ** 2 + (py - y0 - t * dy) ** 2 <= r * r;
  });
}
function line(x0, y0, x1, y1) {
  const set = new Set();
  const steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
  for (let i = 0; i <= steps; i++) {
    const x = Math.round(x0 + ((x1 - x0) * i) / steps), y = Math.round(y0 + ((y1 - y0) * i) / steps);
    if (inside(x, y)) set.add(key(x, y));
  }
  return set;
}
const pts = (...xy) => new Set(xy.map(([x, y]) => key(x, y)));
const union = (...sets) => new Set(sets.flatMap((s) => [...s]));
const minus = (a, b) => new Set([...a].filter((k) => !b.has(k)));

// ---------------------------------------------------------------- icon canvas
class Icon {
  constructor(seed) {
    this.seed = seed;
    this.rgba = new Array(N * N).fill(null);
  }

  /**
   * Paints a shape with vanilla-like shading.
   * outline: 'full' = dark rim all round; 'soft' = light top-left / dark bottom-right rim (small pieces);
   *          'flat' = no rim (details painted on top of another shape).
   */
  shape(set, color, { outline = 'full', light = 0.28, dark = 0.8, rim = 0.55, grain = 0.07, alpha = 255 } = {}) {
    const base = hex(color);
    const has = (x, y) => inside(x, y) && set.has(key(x, y));
    for (const k of set) {
      const x = k % N, y = Math.floor(k / N);
      const up = has(x, y - 1), down = has(x, y + 1), left = has(x - 1, y), right = has(x + 1, y);
      let c = base;
      if (outline === 'full' && !(up && down && left && right)) {
        c = mul(base, rim);
      } else if (outline === 'soft' && !(up && left)) {
        c = mix(base, WHITE, light);
      } else if (outline === 'soft' && !(down && right)) {
        c = mul(base, rim + 0.1);
      } else if (outline !== 'flat') {
        const upEdge = !has(x, y - 2) || !has(x - 1, y - 1);
        const leftEdge = !has(x - 2, y);
        const downEdge = !has(x, y + 2) || !has(x + 1, y + 1);
        const rightEdge = !has(x + 2, y);
        if ((upEdge || leftEdge) && !(downEdge || rightEdge)) c = mix(base, WHITE, light);
        else if ((downEdge || rightEdge) && !(upEdge || leftEdge)) c = mul(base, dark);
      }
      c = mul(c, 1 + (noise(x, y, this.seed) - 0.5) * 2 * grain);
      this.rgba[k] = [...c, alpha];
    }
    return this;
  }

  /** Single-colour pixels (details: grill marks, specks), optionally only where something is painted. */
  paint(set, color, { onlyOver = true, alpha = 255 } = {}) {
    const c = hex(color);
    for (const k of set) if (!onlyOver || this.rgba[k]) this.rgba[k] = [...c, alpha];
    return this;
  }

  png() {
    const raw = Buffer.alloc(N * (N * 4 + 1));
    for (let y = 0; y < N; y++) {
      raw[y * (N * 4 + 1)] = 0;
      for (let x = 0; x < N; x++) {
        const p = this.rgba[key(x, y)] ?? [0, 0, 0, 0];
        raw.set(p, y * (N * 4 + 1) + 1 + x * 4);
      }
    }
    const chunk = (type, data) => {
      const len = Buffer.alloc(4);
      len.writeUInt32BE(data.length);
      const td = Buffer.concat([Buffer.from(type), data]);
      const crc = Buffer.alloc(4);
      crc.writeUInt32BE(crc32(td));
      return Buffer.concat([len, td, crc]);
    };
    const ihdr = Buffer.alloc(13);
    ihdr.writeUInt32BE(N, 0);
    ihdr.writeUInt32BE(N, 4);
    ihdr.set([8, 6, 0, 0, 0], 8); // 8-bit RGBA
    return Buffer.concat([
      Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
      chunk('IHDR', ihdr), chunk('IDAT', deflateSync(raw)), chunk('IEND', Buffer.alloc(0)),
    ]);
  }
}

const CRC_TABLE = Array.from({ length: 256 }, (_, n) => {
  let c = n;
  for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
  return c >>> 0;
});
function crc32(buf) {
  let c = 0xffffffff;
  for (const b of buf) c = CRC_TABLE[(c ^ b) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}

// ---------------------------------------------------------------- the dishes
const icons = {
  // Grilled halloumi: a thick cream slab with diagonal grill marks
  halloumi: () => {
    const i = new Icon(1);
    i.shape(rect(2, 10, 13, 12), '#d4bc86');
    i.shape(minus(rect(2, 3, 13, 10), pts([2, 3], [13, 3])), '#f1e2b8');
    for (const k of [-2, 2, 6]) {
      const marks = pixels((_, __, x, y) => y >= 5 && y <= 8 && x >= 3 && x <= 12 && x - y === k);
      const glow = pixels((_, __, x, y) => y >= 5 && y <= 8 && x >= 3 && x <= 12 && x - y === k + 1);
      i.paint(glow, '#b98244').paint(marks, '#6e4119');
    }
    return i;
  },

  // Souvlaki: three grilled pork chunks on a wooden skewer
  souvlaki: () => {
    const i = new Icon(2);
    i.shape(line(1, 14, 14, 1), '#d2ad6e', { outline: 'flat' });
    i.paint(line(0, 15, 2, 13), '#8d6a35', { onlyOver: false });
    i.paint(pts([14, 1]), '#e9d3a2');
    for (const [cx, cy] of [[4.5, 11.5], [7.5, 8.5], [10.5, 5.5]]) {
      i.shape(disc(cx, cy, 2.3), '#a4542a', { outline: 'soft', rim: 0.5 });
      i.paint(pts([Math.floor(cx) - 1, Math.floor(cy)], [Math.floor(cx), Math.floor(cy) + 1]), '#3d1e0c');
    }
    i.paint(pts([6, 10], [9, 7]), '#4f9a3c', { onlyOver: false }); // green pepper between chunks
    return i;
  },

  // Sheftalia: two plump sausages wrapped in lacy caul fat
  sheftalia: () => {
    const i = new Icon(3);
    for (const [x0, y0, x1, y1] of [[3, 9, 9, 3], [6.5, 13, 13, 6.5]]) {
      const s = capsule(x0, y0, x1, y1, 2.2);
      i.shape(s, '#8f4c27');
      i.paint(pixels((_, __, x, y) => s.has(key(x, y)) && (x * 3 + y * 5) % 7 === 0), '#c99062');
    }
    return i;
  },

  // Kleftiko: slow-cooked lamb with the bone sticking out of a clay pot
  kleftiko: () => {
    const i = new Icon(4);
    i.shape(ellipse(7, 8.5, 5.2, 5, 'top'), '#8a4321');
    i.paint(pts([5, 5], [8, 4], [6, 7], [9, 6]), '#5e2a12');
    i.shape(capsule(10, 6, 13, 2.5, 0.9), '#efe5cf', { outline: 'soft' });
    i.shape(disc(13.6, 2.2, 1.3), '#f6efdf', { outline: 'soft' });
    i.shape(rect(1, 8, 14, 9), '#b8643a');
    i.shape(minus(ellipse(7.5, 9, 6.5, 6, 'bottom'), rect(0, 0, 15, 9)), '#9c4a26');
    i.paint(pts([3, 11], [6, 11], [9, 11], [12, 11]), '#d98a54');
    return i;
  },

  // Moussaka: a slice showing its layers — golden bechamel, aubergine, meat, potato
  moussaka: () => {
    const i = new Icon(5);
    const body = rect(2, 4, 13, 13);
    i.shape(body, '#e8c779');
    i.paint(minus(rect(3, 5, 12, 6), new Set()), '#f2d483');
    i.paint(rect(3, 7, 12, 8), '#5a2b5c');
    i.paint(rect(3, 9, 12, 10), '#8d3a22');
    i.paint(rect(3, 11, 12, 12), '#e1c27e');
    i.paint(pts([4, 5], [8, 6], [11, 5], [6, 6]), '#b5762e');
    i.paint(pts([5, 8], [10, 7]), '#7d4a80');
    i.paint(pts([4, 10], [9, 9], [12, 10]), '#5e2414');
    i.paint(pts([2, 4], [13, 4]), '#000000', { alpha: 0 });
    return i;
  },

  // Keftedes: three fried meatballs with a mint leaf
  keftedes: () => {
    const i = new Icon(6);
    for (const [cx, cy] of [[4.8, 11], [11.2, 11], [8, 6.2]]) {
      const ball = disc(cx, cy, 3.3);
      i.shape(ball, '#7c3f1e');
      i.paint(pixels((_, __, x, y) => ball.has(key(x, y)) && (x * 7 + y * 3) % 9 === 0), '#a8693b');
      i.paint(pixels((_, __, x, y) => ball.has(key(x, y)) && (x * 5 + y * 11) % 13 === 0), '#4a220e');
    }
    i.shape(ellipse(9.5, 2.8, 2.3, 1.2), '#4ea545', { outline: 'soft' });
    i.paint(pts([8, 2], [9, 2]), '#2f6e2a');
    return i;
  },

  // Koupepia: stuffed vine leaves rolled into little cigars
  koupepia: () => {
    const i = new Icon(7);
    for (const [cx, cy] of [[4.5, 4.5], [8, 8], [11.5, 11.5]]) {
      const roll = capsule(cx - 3, cy + 3, cx + 3, cy - 3, 1.6);
      i.shape(roll, '#6b8f35', { rim: 0.5 });
      i.paint(pixels((_, __, x, y) => roll.has(key(x, y)) && (x + y) % 4 === 0 && (x - y) % 2 === 0), '#8fae52');
    }
    return i;
  },

  // Horiatiki: Greek village salad in a white bowl with a blue stripe
  horiatiki: () => {
    const i = new Icon(8);
    i.shape(ellipse(8, 8.5, 6, 3.5, 'top'), '#5faa3a');
    i.shape(disc(5, 6.5, 1.5), '#d8392b', { outline: 'soft' });
    i.shape(disc(10.5, 5.5, 1.5), '#d8392b', { outline: 'soft' });
    i.shape(rect(7, 4, 8, 5), '#fbfbf2', { outline: 'soft' });
    i.shape(disc(12.5, 7.2, 1.2), '#cfe59a', { outline: 'soft' });
    i.paint(pts([4, 8], [9, 7], [11, 3]), '#262626', { onlyOver: false });
    i.shape(minus(ellipse(8, 8, 7, 6.5, 'bottom'), rect(0, 0, 15, 7)), '#ececec');
    i.paint(pixels((_, __, x, y) => y === 11 && x >= 2 && x <= 13), '#2f5fa8');
    return i;
  },

  // Loukoumades: honey-soaked doughnut balls with cinnamon
  loukoumades: () => {
    const i = new Icon(9);
    const centers = [[4.3, 11.5], [8.3, 11.5], [12.3, 11.5], [6.3, 7.7], [10.3, 7.7], [8.3, 3.9]];
    for (const [cx, cy] of centers) {
      i.shape(disc(cx, cy, 2.2), '#c47a1c', { outline: 'soft', light: 0.22, rim: 0.5 });
      i.paint(pts([Math.floor(cx) - 1, Math.floor(cy) - 1]), '#ffd25a');
    }
    i.paint(pts([5, 12], [9, 11], [12, 12], [7, 8], [10, 7], [8, 4]), '#5a2c0e');
    i.paint(pts([6, 9], [7, 10], [8, 9], [9, 10], [10, 9], [11, 10], [8, 6], [9, 5]), '#f2b028');
    return i;
  },

  // Cyprus coffee: a small white cup with crema on a saucer, steaming
  coffee: () => {
    const i = new Icon(10);
    i.shape(ellipse(8, 13.5, 7, 1.8), '#e4e4e4');
    i.shape(minus(rect(12, 7, 14, 11), rect(13, 8, 13, 10)), '#f0f0f0', { outline: 'soft' });
    i.shape(minus(rect(4, 6, 12, 13), pts([4, 13], [12, 13])), '#f5f5f5');
    i.paint(rect(5, 6, 11, 6), '#5b3219').paint(pts([6, 6], [9, 6]), '#a26f3c');
    i.paint(pts([6, 4], [7, 3], [7, 2], [6, 1], [9, 4], [9, 3], [10, 2], [10, 1]), '#e6e6e6', { onlyOver: false, alpha: 150 });
    return i;
  },
};

mkdirSync(outDir, { recursive: true });
const made = Object.entries(icons).map(([id, make]) => [id, make()]);
for (const [id, icon] of made) {
  writeFileSync(join(outDir, `${id}.png`), icon.png());
}
console.log(`wrote ${made.length} icons to ${outDir}`);

// Optional: node make-icons.mjs <outDir> <sheet.png> — an upscaled preview sheet on a grey background
if (process.argv[3]) {
  const S = 12, cols = 5, pad = 8, cell = N * S + pad;
  const rows = Math.ceil(made.length / cols);
  const W = cols * cell + pad, H = rows * cell + pad;
  const sheet = new Icon(0);
  const raw = Buffer.alloc(H * (W * 4 + 1));
  for (let y = 0; y < H; y++) {
    raw[y * (W * 4 + 1)] = 0;
    for (let x = 0; x < W; x++) raw.set([139, 139, 139, 255], y * (W * 4 + 1) + 1 + x * 4);
  }
  made.forEach(([, icon], n) => {
    const ox = pad + (n % cols) * cell, oy = pad + Math.floor(n / cols) * cell;
    for (let y = 0; y < N * S; y++) for (let x = 0; x < N * S; x++) {
      const p = icon.rgba[key(Math.floor(x / S), Math.floor(y / S))];
      if (!p || p[3] === 0) continue;
      const a = p[3] / 255, o = (oy + y) * (W * 4 + 1) + 1 + (ox + x) * 4;
      for (let c = 0; c < 3; c++) raw[o + c] = clamp(raw[o + c] * (1 - a) + p[c] * a);
    }
  });
  const chunk = (type, data) => {
    const len = Buffer.alloc(4); len.writeUInt32BE(data.length);
    const td = Buffer.concat([Buffer.from(type), data]);
    const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(td));
    return Buffer.concat([len, td, crc]);
  };
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(W, 0); ihdr.writeUInt32BE(H, 4); ihdr.set([8, 6, 0, 0, 0], 8);
  writeFileSync(process.argv[3], Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', ihdr), chunk('IDAT', deflateSync(raw)), chunk('IEND', Buffer.alloc(0)),
  ]));
  void sheet;
}
