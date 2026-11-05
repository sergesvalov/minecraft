# Ресурспак «Халуми»

[English](README.md) | **Русский**

Необязательные иконки для блюд таверны из плагина [HalloumiTrader](../plugins-src/HalloumiTrader/README_ru.md): халуми и 9 блюд мезе, 16×16, в стиле ванильных предметов.

- **Java**: сервер предлагает пак при входе. От него можно отказаться — тогда блюда выглядят как ванильные предметы, на которых они основаны (хлеб, жареная свинина, печенье…).
- **Bedrock** (через Geyser): пак приходит вместе с собственным паком Geyser. В Geyser включено `force-resource-packs: true`, так что Bedrock-игроки отказаться не могут. Весит около 8 КБ.

## Что где лежит

| Путь | Что это |
|---|---|
| `tools/make-icons.mjs` | Рисует иконки в `src/textures/`. Без зависимостей; поправь фигуру и перезапусти |
| `src/textures/<id>.png` | Иконки, по одной на каждый `id` блюда из `tavern.menu` (и `halloumi`) |
| `tools/build-packs.mjs` | Собирает всё в `dist/` и прописывает URL, SHA1 и ID пака в `docker-compose.yml` |
| `dist/HalloumiPack.zip` | Java-пак |
| `dist/HalloumiPack.mcpack` | Bedrock-пак, кладётся в `plugins/Geyser-Spigot/packs/` |
| `dist/halloumitrader.json` | Сопоставление предметов для Geyser (формат v2), кладётся в `plugins/Geyser-Spigot/custom_mappings/` |
| `tools/publish-release.sh` | Выкладывает Java-zip в GitHub-релиз `resourcepack-v<N>` |

## Как иконка находит блюдо

Плагин ставит каждому блюду строку `custom_model_data` вида `halloumitrader:<id>`.

- Java-пак подменяет ванильные описания базовых предметов (`assets/minecraft/items/bread.json` и т.д.) на `select` по этой строке. Любое другое значение показывает ванильную модель, так что обычный хлеб остаётся обычным.
- Сопоставление Geyser ловит ту же строку предикатом `match` и показывает Bedrock-текстуру `halloumitrader_<id>`.

У еды, купленной до версии плагина 1.2, `custom_model_data` нет, она остаётся ванильной на вид.

## Как поменять иконки или блюда

```bash
node resourcepack/tools/make-icons.mjs     # только если менялись рисунки
# подними PACK_VERSION в resourcepack/tools/build-packs.mjs
node resourcepack/tools/build-packs.mjs
git add resourcepack docker-compose.yml
```

Поднимай `PACK_VERSION` при любом изменении: выложенный релиз не перезаписывается, а Bedrock-клиенты кэшируют паки по версии. Новое блюдо без текстуры просто остаётся ванильным.

При пуше Jenkins:

1. Запускает `publish-release.sh` с токеном `git-access`. Скрипт создаёт релиз `resourcepack-v<N>` и загружает zip, а если такой zip там уже есть — ничего не делает. Если что-то пошло не так, сборка помечается нестабильной, но деплой продолжается.
2. Копирует `.mcpack` и сопоставление в папки Geyser на сервере.
3. Перезапускает сервер через `docker compose up -d`, потому что изменился `docker-compose.yml`.

Zip пишется без сжатия и с фиксированными датами, поэтому из одних и тех же исходников всегда получается один и тот же SHA1.
