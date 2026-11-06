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
| `dist/HalloumiPack.zip` | Java-пак. Java-игроки скачивают его прямо из этого репозитория (`raw.githubusercontent.com`, ветка `main`) |
| `dist/HalloumiPack.mcpack` | Bedrock-пак, кладётся в `plugins/Geyser-Spigot/packs/` |
| `dist/halloumitrader.json` | Сопоставление предметов для Geyser (формат v2), кладётся в `plugins/Geyser-Spigot/custom_mappings/` |

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

Поднимай `PACK_VERSION` при любом изменении: Bedrock-клиенты кэшируют паки по версии. Java-клиенты узнают новый zip по SHA1. Новое блюдо без текстуры просто остаётся ванильным.

После пуша новый zip сразу доступен в `main`, а Jenkins:

1. Копирует `.mcpack` и сопоставление в папки Geyser на сервере.
2. Перезапускает сервер через `docker compose up -d`, потому что изменился `docker-compose.yml`.

До этого перезапуска сервер ещё сообщает старый SHA1, поэтому Java-игрок, зашедший в эти несколько минут, увидит ошибку загрузки пака и будет играть без иконок.

Zip пишется без сжатия и с фиксированными датами, поэтому из одних и тех же исходников всегда получается один и тот же SHA1.
