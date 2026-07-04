package me.vanillabounties.listener;

import io.papermc.paper.event.player.PlayerTradeEvent;
import me.vanillabounties.BountyService;
import me.vanillabounties.BukkitTestSupport;
import me.vanillabounties.model.KnownPlayer;
import me.vanillabounties.storage.BountyDatabase;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.ItemEntityMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.entity.VillagerMock;
import org.mockbukkit.mockbukkit.inventory.PlayerInventoryViewMock;
import org.mockbukkit.mockbukkit.inventory.SimpleInventoryViewMock;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BountyListenerTest extends BukkitTestSupport {
    @TempDir
    Path tempDir;

    @Test
    void droppingHuntCompassStopsHuntAndRemovesCompass() throws Exception {
        try (BountyDatabase database = openDatabase()) {
            Harness harness = startHunt(database);
            ItemEntityMock droppedItem = new ItemEntityMock(MockBukkit.getMock(), UUID.randomUUID(), harness.compass().clone());
            PlayerDropItemEvent event = new PlayerDropItemEvent(harness.hunter(), droppedItem);

            harness.listener().onPlayerDropItem(event);

            assertTrue(event.isCancelled());
            harness.hunter().getInventory().addItem(harness.compass().clone());
            MockBukkit.getMock().getScheduler().performOneTick();

            assertFalse(harness.service().isHunting(harness.hunter()));
            assertFalse(harness.service().hasHuntCompass(harness.hunter()));
        }
    }

    @Test
    void shiftClickingHuntCompassIntoChestIsCancelled() throws Exception {
        try (BountyDatabase database = openDatabase()) {
            Harness harness = startHunt(database);
            Inventory chest = Bukkit.createInventory(null, 27);
            InventoryView view = new SimpleInventoryViewMock(harness.hunter(), chest, harness.hunter().getInventory(), InventoryType.CHEST);
            int rawBottomSlot = chest.getSize();
            InventoryClickEvent event = new InventoryClickEvent(
                view,
                InventoryType.SlotType.CONTAINER,
                rawBottomSlot,
                ClickType.SHIFT_LEFT,
                InventoryAction.MOVE_TO_OTHER_INVENTORY
            );
            event.setCurrentItem(harness.compass().clone());

            assertTrue(harness.listener().protectHuntCompassClick(event, harness.hunter()));
            assertTrue(event.isCancelled());
            assertTrue(harness.service().isHunting(harness.hunter()));
        }
    }

    @Test
    void rearrangingHuntCompassInsidePlayerInventoryIsAllowed() throws Exception {
        try (BountyDatabase database = openDatabase()) {
            Harness harness = startHunt(database);
            InventoryView view = new PlayerInventoryViewMock(harness.hunter(), harness.hunter().getInventory());
            InventoryClickEvent event = new InventoryClickEvent(
                view,
                InventoryType.SlotType.CONTAINER,
                0,
                ClickType.LEFT,
                InventoryAction.PICKUP_ALL
            );
            event.setCurrentItem(harness.compass().clone());

            assertFalse(harness.listener().protectHuntCompassClick(event, harness.hunter()));
            assertFalse(event.isCancelled());
            assertTrue(harness.service().isHunting(harness.hunter()));
        }
    }

    @Test
    void villagerTradeWithHuntCompassInputIsCancelled() throws Exception {
        try (BountyDatabase database = openDatabase()) {
            Harness harness = startHunt(database);
            VillagerMock villager = new VillagerMock(MockBukkit.getMock(), UUID.randomUUID());
            MerchantRecipe recipe = new MerchantRecipe(new ItemStack(Material.EMERALD, 1), 999);
            recipe.addIngredient(new ItemStack(Material.COMPASS, 1));
            villager.setRecipes(List.of(recipe));
            harness.hunter().openMerchant(villager, true);
            harness.hunter().getOpenInventory().getTopInventory().setItem(0, harness.compass().clone());
            PlayerTradeEvent event = new PlayerTradeEvent(harness.hunter(), villager, recipe, true, true);

            harness.listener().onPlayerTrade(event);

            assertTrue(event.isCancelled());
            assertTrue(harness.service().isHunting(harness.hunter()));
        }
    }

    @Test
    void deathDropsDoNotContainHuntCompassAndHuntIsCleared() throws Exception {
        try (BountyDatabase database = openDatabase()) {
            Harness harness = startHunt(database);
            List<ItemStack> drops = new ArrayList<>(List.of(harness.compass().clone(), new ItemStack(Material.DIAMOND, 1)));
            PlayerDeathEvent event = new PlayerDeathEvent(
                harness.hunter(),
                (DamageSource) null,
                drops,
                0,
                Component.empty(),
                false
            );

            harness.listener().onPlayerDeath(event);

            assertEquals(1, event.getDrops().size());
            assertEquals(Material.DIAMOND, event.getDrops().getFirst().getType());
            assertFalse(harness.service().isHunting(harness.hunter()));
            assertFalse(harness.service().hasHuntCompass(harness.hunter()));
        }
    }

    private Harness startHunt(BountyDatabase database) throws Exception {
        PlayerMock placer = MockBukkit.getMock().addPlayer("Placer");
        PlayerMock target = MockBukkit.getMock().addPlayer("Target");
        PlayerMock hunter = MockBukkit.getMock().addPlayer("Hunter");
        BountyService service = new BountyService(MockBukkit.createMockPlugin(), database);
        BountyListener listener = new BountyListener(service, null, null, null, null);
        KnownPlayer knownTarget = new KnownPlayer(target.getUniqueId(), target.getName(), 1L);
        database.insertActiveBounty(knownTarget, placer.getUniqueId(), placer.getName(), new ItemStack(Material.DIAMOND, 1), 10L);
        hunter.getInventory().addItem(new ItemStack(Material.RECOVERY_COMPASS, 1));

        assertTrue(service.enablePublicTracking(hunter, target.getUniqueId(), target.getName()).success());
        assertTrue(service.toggleHunt(hunter, target.getUniqueId(), target.getName()).success());

        return new Harness(service, listener, hunter, firstHuntCompass(service, hunter));
    }

    private ItemStack firstHuntCompass(BountyService service, Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (service.isHuntCompass(item)) {
                return item.clone();
            }
        }
        throw new AssertionError("hunter does not have a hunt compass");
    }

    private BountyDatabase openDatabase() throws Exception {
        BountyDatabase database = new BountyDatabase(tempDir.resolve(UUID.randomUUID() + ".db"));
        database.migrate();
        return database;
    }

    private record Harness(BountyService service, BountyListener listener, PlayerMock hunter, ItemStack compass) {
    }
}
