package com.server.halloumi;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import io.papermc.paper.datacomponent.item.FoodProperties;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * A dish from the tavern menu (tavern.menu in config.yml): a vanilla item renamed, with its own food values.
 * Each dish also carries the custom_model_data string {@code halloumitrader:<id>}: the optional resource pack
 * (resourcepack/) draws its own icon for it, and without the pack the vanilla item model is used.
 */
record Dish(String id, String name, Material base, List<String> lore, int nutrition, float saturation,
            int price, int amount, PotionEffectType effect, int effectSeconds, Color color) {

    ItemStack create(NamespacedKey dishKey) {
        int stack = Math.min(amount, base.getMaxStackSize());
        ItemStack item = new ItemStack(base, Math.max(1, stack));
        item.editMeta(meta -> {
            meta.displayName(Component.text(name, NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            meta.lore(lore.stream()
                    .map(line -> (Component) Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false))
                    .toList());
            meta.getPersistentDataContainer().set(dishKey, PersistentDataType.STRING, id);
            if (meta instanceof PotionMeta potion) {
                if (color != null) {
                    potion.setColor(color);
                }
                if (effect != null) {
                    potion.addCustomEffect(new PotionEffect(effect, effectSeconds * 20, 0), true);
                }
            }
        });
        if (nutrition > 0 && base.isEdible()) {
            item.setData(DataComponentTypes.FOOD, FoodProperties.food()
                    .nutrition(nutrition)
                    .saturation(saturation)
                    .build());
        }
        item.setData(DataComponentTypes.CUSTOM_MODEL_DATA, modelData(id));
        return item;
    }

    /** custom_model_data shared by the plugin, the Java resource pack and the Geyser mapping. */
    static CustomModelData modelData(String id) {
        return CustomModelData.customModelData().addString("halloumitrader:" + id).build();
    }

    static List<Dish> load(FileConfiguration cfg, Logger log) {
        List<Dish> dishes = new ArrayList<>();
        for (Map<?, ?> raw : cfg.getMapList("tavern.menu")) {
            try {
                String id = String.valueOf(raw.get("id"));
                Material base = Material.matchMaterial(String.valueOf(raw.get("item")));
                if (base == null) {
                    log.warning("tavern.menu: unknown item for dish " + id);
                    continue;
                }
                PotionEffectType effect = null;
                if (raw.get("effect") != null) {
                    effect = Registry.EFFECT.get(NamespacedKey.minecraft(String.valueOf(raw.get("effect")).toLowerCase(Locale.ROOT)));
                }
                Color color = null;
                if (raw.get("color") != null) {
                    color = Color.fromRGB(Integer.parseInt(String.valueOf(raw.get("color")).replace("#", ""), 16));
                }
                List<String> lore = new ArrayList<>();
                if (raw.get("lore") instanceof List<?> lines) {
                    lines.forEach(l -> lore.add(String.valueOf(l)));
                }
                dishes.add(new Dish(id, String.valueOf(raw.get("name")), base, lore,
                        number(raw.get("nutrition"), 0).intValue(),
                        number(raw.get("saturation"), 0).floatValue(),
                        Math.max(1, number(raw.get("price"), 1).intValue()),
                        Math.max(1, number(raw.get("amount"), 1).intValue()),
                        effect, number(raw.get("effect-seconds"), 60).intValue(), color));
            } catch (RuntimeException e) {
                log.warning("tavern.menu: bad dish entry " + raw + ": " + e.getMessage());
            }
        }
        return dishes;
    }

    private static Number number(Object value, Number fallback) {
        return value instanceof Number n ? n : fallback;
    }
}
