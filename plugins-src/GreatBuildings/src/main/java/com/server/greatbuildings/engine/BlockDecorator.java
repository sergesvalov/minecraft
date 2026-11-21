package com.server.greatbuildings.engine;

import com.server.greatbuildings.model.Building;
import com.server.greatbuildings.model.Plan;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Lectern;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.LecternInventory;
import org.bukkit.inventory.meta.BookMeta;

/**
 * Populates block entities (waxed sign texts and lectern history books) once all blocks are in the world.
 */
public final class BlockDecorator {

    private BlockDecorator() {
    }

    public static void decorate(Plan plan, World world, Building building,
                                int anchorLocalX, int anchorLocalZ,
                                int anchorWorldX, int oy, int anchorWorldZ,
                                StructureRotation rotation) {
        for (Plan.SignText text : plan.signs) {
            int wx = Placement.toWorldX(text.x(), text.z(), anchorLocalX, anchorLocalZ, anchorWorldX, rotation);
            int wz = Placement.toWorldZ(text.x(), text.z(), anchorLocalX, anchorLocalZ, anchorWorldZ, rotation);
            if (world.getBlockAt(wx, oy + text.y(), wz).getState() instanceof Sign sign) {
                SignSide front = sign.getSide(Side.FRONT);
                for (int i = 0; i < Math.min(4, text.lines().size()); i++) {
                    front.line(i, Component.text(text.lines().get(i), NamedTextColor.DARK_BLUE));
                }
                sign.setWaxed(true);
                sign.update(true, false);
            }
        }
        ItemStack book = book(building.book());
        for (int[] pos : plan.lecterns) {
            int wx = Placement.toWorldX(pos[0], pos[2], anchorLocalX, anchorLocalZ, anchorWorldX, rotation);
            int wz = Placement.toWorldZ(pos[0], pos[2], anchorLocalX, anchorLocalZ, anchorWorldZ, rotation);
            if (world.getBlockAt(wx, oy + pos[1], wz).getState() instanceof Lectern lectern
                    && lectern.getInventory() instanceof LecternInventory inventory) {
                inventory.setBook(book.clone());
            }
        }
    }

    static ItemStack book(Building.Book content) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        book.editMeta(BookMeta.class, meta -> {
            meta.setTitle(content.title());
            meta.setAuthor(content.author());
            meta.addPages(content.pages().stream().map(page -> (Component) Component.text(page)).toArray(Component[]::new));
        });
        return book;
    }
}

