package com.server.halloumi.trade;

import com.server.halloumi.model.Dish;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.FoodProperties;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Manages the halloumi food item, Cypriot meze menu configuration, innkeeper trades, and lectern menu book.
 */
public final class MenuService {

    private final NamespacedKey itemKey;
    private final NamespacedKey dishKey;
    private List<Dish> menu = List.of();

    public MenuService(Plugin plugin) {
        this.itemKey = new NamespacedKey(plugin, "halloumi");
        this.dishKey = new NamespacedKey(plugin, "dish");
    }

    /**
     * Dishes added to the default menu in a newer version are appended to an existing config.yml once
     * (copyDefaults doesn't merge lists). {@code tavern.menu-version} remembers it, so dishes removed later stay removed.
     */
    public void migrateDefaults(FileConfiguration cfg, Logger logger) {
        var defaults = cfg.getDefaults();
        if (defaults == null) {
            return;
        }
        int current = defaults.getInt("tavern.menu-version", 1);
        int seen = cfg.contains("tavern.menu-version", true) ? cfg.getInt("tavern.menu-version") : 1;
        if (seen >= current || !cfg.contains("tavern.menu", true)) {
            return;
        }
        List<Map<?, ?>> menuList = new ArrayList<>(cfg.getMapList("tavern.menu"));
        Set<String> ids = new HashSet<>();
        menuList.forEach(dish -> ids.add(String.valueOf(dish.get("id"))));
        int added = 0;
        for (Map<?, ?> dish : defaults.getMapList("tavern.menu")) {
            if (ids.add(String.valueOf(dish.get("id")))) {
                menuList.add(dish);
                added++;
            }
        }
        cfg.set("tavern.menu", menuList);
        cfg.set("tavern.menu-version", current);
        logger.info("tavern.menu: added " + added + " new dishes to config.yml");
    }

    public void reload(FileConfiguration cfg, Logger logger) {
        menu = Dish.load(cfg, logger);
    }

    public ItemStack createHalloumi(FileConfiguration cfg, int amount) {
        ItemStack item = new ItemStack(Material.BREAD, amount);
        item.editMeta(meta -> {
            meta.displayName(Component.text(cfg.getString("halloumi.name", "Халуми"), NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(cfg.getStringList("halloumi.lore").stream()
                    .map(line -> (Component) Component.text(line, NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false))
                    .toList());
            meta.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
        });
        item.setData(DataComponentTypes.FOOD, FoodProperties.food()
                .nutrition(cfg.getInt("halloumi.nutrition", 8))
                .saturation((float) cfg.getDouble("halloumi.saturation", 12.8))
                .build());
        item.setData(DataComponentTypes.CUSTOM_MODEL_DATA, Dish.modelData("halloumi"));
        return item;
    }

    public MerchantRecipe createHalloumiRecipe(FileConfiguration cfg, int maxUses) {
        MerchantRecipe recipe = new MerchantRecipe(
                createHalloumi(cfg, Math.max(1, cfg.getInt("trade.amount", 4))), Math.max(1, maxUses));
        recipe.addIngredient(new ItemStack(Material.EMERALD, Math.max(1, cfg.getInt("trade.price-emeralds", 2))));
        recipe.setExperienceReward(false);
        return recipe;
    }

    /** Halloumi first, then the Cypriot menu from {@code tavern.menu}. */
    public List<MerchantRecipe> innkeeperRecipes(FileConfiguration cfg) {
        int maxUses = cfg.getInt("tavern.innkeeper-max-uses", 64);
        List<MerchantRecipe> recipes = new ArrayList<>();
        recipes.add(createHalloumiRecipe(cfg, maxUses));
        for (Dish dish : menu) {
            MerchantRecipe recipe = new MerchantRecipe(dish.create(dishKey), Math.max(1, maxUses));
            recipe.addIngredient(new ItemStack(Material.EMERALD, dish.price()));
            recipe.setExperienceReward(false);
            recipes.add(recipe);
        }
        return recipes;
    }

    public int menuSize() {
        return 1 + menu.size();
    }

    /** Written book for the lectern: the menu with prices. */
    public ItemStack createMenuBook(FileConfiguration cfg) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        book.editMeta(BookMeta.class, meta -> {
            meta.setTitle(cfg.getString("tavern.menu-title", "Меню"));
            meta.setAuthor(cfg.getString("tavern.innkeeper-name", "Трактирщик"));

            List<Component> pages = new ArrayList<>();
            pages.add(Component.text(cfg.getString("tavern.menu-title", "Меню"), NamedTextColor.DARK_RED)
                    .decorate(TextDecoration.BOLD)
                    .appendNewline().appendNewline()
                    .append(Component.text(cfg.getString("tavern.menu-intro",
                            "Кипрское мезе. Цены в изумрудах, заказ — у трактирщика за стойкой."), NamedTextColor.BLACK)));

            List<Component> entries = new ArrayList<>();
            entries.add(menuEntry(cfg.getString("halloumi.name", "Халуми"), cfg.getStringList("halloumi.lore"),
                    cfg.getInt("trade.amount", 4), cfg.getInt("trade.price-emeralds", 2)));
            for (Dish dish : menu) {
                entries.add(menuEntry(dish.name(), dish.lore(),
                        Math.min(dish.amount(), dish.base().getMaxStackSize()), dish.price()));
            }
            // Three dishes per page fit comfortably
            for (int i = 0; i < entries.size(); i += 3) {
                Component page = Component.empty();
                for (int j = i; j < Math.min(i + 3, entries.size()); j++) {
                    page = page.append(entries.get(j)).appendNewline();
                }
                pages.add(page);
            }
            meta.addPages(pages.toArray(new Component[0]));
        });
        return book;
    }

    private static Component menuEntry(String name, List<String> lore, int amount, int price) {
        Component entry = Component.text(name, NamedTextColor.DARK_RED).decorate(TextDecoration.BOLD).appendNewline();
        if (!lore.isEmpty()) {
            entry = entry.append(Component.text(lore.get(0), NamedTextColor.DARK_GRAY)).appendNewline();
        }
        return entry.append(Component.text(amount + " шт. — " + price + " изумр.", NamedTextColor.DARK_GREEN)).appendNewline();
    }
}

