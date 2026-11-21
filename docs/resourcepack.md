# 🎨 Ресурспак «Халуми»

[← К общему описанию](../README.md) · [Плагин HalloumiTrader →](plugins/halloumi-trader.md)

Необязательные иконки `16×16` в стиле ванильных предметов для халуми и всех 14 блюд мезе из `HalloumiTrader`.

- **Java Edition:** сервер предлагает пакет при входе (`RESOURCE_PACK` в [docker-compose.yml](../docker-compose.yml)); игрок может отказаться, тогда блюда отображаются ванильными предметами (хлеб, свинина, печенье и т.д.).
- **Bedrock Edition (Geyser):** пакет отправляется принудительно (`force-resource-packs: true`), весит около 8 КБ.

---

## Состав каталога `resourcepack/`

| Файл | Назначение |
|---|---|
| [make-icons.mjs](../resourcepack/tools/make-icons.mjs) | Генератор пиксельных иконок в `src/textures/` (без сторонних зависимостей) |
| `resourcepack/src/textures/<id>.png` | Исходные текстуры 16×16 для `halloumi` и каждого `id` из `tavern.menu` |
| [build-packs.mjs](../resourcepack/tools/build-packs.mjs) | Собирает архивы в `dist/` и обновляет URL, SHA1 и UUID пака в [docker-compose.yml](../docker-compose.yml) |
| [HalloumiPack.zip](../resourcepack/dist/HalloumiPack.zip) | Java-пакет (клиенты качают напрямую с ветки `main`) |
| [HalloumiPack.mcpack](../resourcepack/dist/HalloumiPack.mcpack) | Bedrock-пакет (Jenkins копирует в `plugins/Geyser-Spigot/packs/`) |
| [halloumitrader.json](../resourcepack/dist/halloumitrader.json) | Geyser custom item mappings v2 (копируется в `plugins/Geyser-Spigot/custom_mappings/`) |

---

## Привязка иконок и обновление

Каждое блюдо получает компонент `custom_model_data` со строкой вида `halloumitrader:<id>`:
- Java-пакет подменяет базовые модели через `select` по этой строке;
- Geyser использует предикат `match` и отображает текстуру `halloumitrader_<id>`.

```bash
node resourcepack/tools/make-icons.mjs     # если менялись рисунки
# увеличьте PACK_VERSION в resourcepack/tools/build-packs.mjs
node resourcepack/tools/build-packs.mjs
git add resourcepack docker-compose.yml
```

При любом изменении обязательно поднимайте `PACK_VERSION`: Bedrock-клиенты кэшируют пакеты по версии, а Java-клиенты — по SHA1. Zip записывается без сжатия и с фиксированной датой, чтобы из одних и тех же исходников всегда получался идентичный SHA1.

