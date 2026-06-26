package me.vanillabounties.gui;

import me.vanillabounties.BountyService;
import me.vanillabounties.BukkitTestSupport;
import me.vanillabounties.model.BountyVisibility;
import me.vanillabounties.model.KnownPlayer;
import me.vanillabounties.storage.BountyDatabase;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GuiStyleTest extends BukkitTestSupport {
    @TempDir
    Path tempDir;

    @Test
    void confirmationMenuUsesPositiveAndDestructiveColorsWithoutItalics() {
        PlayerMock placer = MockBukkit.getMock().addPlayer("Placer");
        KnownPlayer target = new KnownPlayer(UUID.randomUUID(), "Target", 1L);
        placer.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND, 1));
        BountyConfirmationGui gui = new BountyConfirmationGui(null);

        gui.open(placer, target, BountyVisibility.NORMAL);

        Inventory inventory = placer.getOpenInventory().getTopInventory();
        assertStyled(inventory.getItem(11), NamedTextColor.GREEN);
        assertStyled(inventory.getItem(15), NamedTextColor.RED);
        assertLoreStyled(inventory.getItem(13));
    }

    @Test
    void bountyDetailMenuUsesNavigationAndTrackingColorsWithoutItalics() throws Exception {
        try (BountyDatabase database = openDatabase()) {
            PlayerMock viewer = MockBukkit.getMock().addPlayer("Viewer");
            UUID targetUuid = UUID.randomUUID();
            BountyService service = new BountyService(MockBukkit.createMockPlugin(), database);
            BountyGui gui = new BountyGui(service);
            database.insertActiveBounty(
                new KnownPlayer(targetUuid, "Target", 1L),
                UUID.randomUUID(),
                "Placer",
                new ItemStack(Material.EMERALD, 1),
                10L
            );

            gui.openDetail(viewer, targetUuid, "Target", 0);

            Inventory inventory = viewer.getOpenInventory().getTopInventory();
            assertStyled(inventory.getItem(45), NamedTextColor.YELLOW);
            assertStyled(inventory.getItem(47), NamedTextColor.AQUA);
        }
    }

    private BountyDatabase openDatabase() throws Exception {
        BountyDatabase database = new BountyDatabase(tempDir.resolve(UUID.randomUUID() + ".db"));
        database.migrate();
        return database;
    }

    private void assertStyled(ItemStack item, NamedTextColor expectedNameColor) {
        assertNotNull(item);
        ItemMeta meta = item.getItemMeta();
        assertNotNull(meta);
        Component displayName = meta.displayName();
        assertNotNull(displayName);
        assertEquals(expectedNameColor, displayName.color());
        assertEquals(TextDecoration.State.FALSE, displayName.decoration(TextDecoration.ITALIC));
        assertLoreStyled(item);
    }

    private void assertLoreStyled(ItemStack item) {
        assertNotNull(item);
        ItemMeta meta = item.getItemMeta();
        assertNotNull(meta);
        List<Component> lore = meta.lore();
        if (lore == null) {
            return;
        }
        for (Component line : lore) {
            assertNotNull(line.color());
            assertEquals(TextDecoration.State.FALSE, line.decoration(TextDecoration.ITALIC));
        }
    }
}
