package me.vanillabounties.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

final class GuiItems {
    private static final NamedTextColor DEFAULT_LORE_COLOR = NamedTextColor.GRAY;

    private GuiItems() {
    }

    static Component text(String value, NamedTextColor color) {
        return plain(Component.text(value, color));
    }

    static Component emptyLine() {
        return plain(Component.empty());
    }

    static ItemStack namedItem(Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        return namedItem(item, name, lore);
    }

    static ItemStack namedItem(ItemStack base, Component name, List<Component> lore) {
        ItemStack item = base.clone();
        item.setAmount(1);
        ItemMeta meta = item.getItemMeta();
        displayName(meta, name);
        lore(meta, lore);
        item.setItemMeta(meta);
        return item;
    }

    static void displayName(ItemMeta meta, Component name) {
        meta.displayName(plain(name));
    }

    static void lore(ItemMeta meta, List<Component> lore) {
        meta.lore(lore.stream()
            .map(line -> plain(line).colorIfAbsent(DEFAULT_LORE_COLOR))
            .toList());
    }

    static Component plain(Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }
}
