package me.vanillabounties;

import me.vanillabounties.model.BountyReward;
import me.vanillabounties.model.BountyVisibility;
import me.vanillabounties.model.HuntHudMode;
import me.vanillabounties.model.KnownPlayer;
import me.vanillabounties.model.RewardState;
import me.vanillabounties.storage.BountyDatabase;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BountyServiceTest extends BukkitTestSupport {
    @TempDir
    Path tempDir;

    @Test
    void playerKillUpdatesStreaksAndPlacesServerBounty() throws Exception {
        try (BountyDatabase database = openDatabase()) {
            PlayerMock victim = MockBukkit.getMock().addPlayer("Victim");
            PlayerMock killer = MockBukkit.getMock().addPlayer("Killer");
            BountyService service = new BountyService(MockBukkit.createMockPlugin(), database);

            database.applyAutomaticBountiesForKill(UUID.randomUUID(), "Other", victim.getUniqueId(), victim.getName(), 1L);
            assertEquals(1, database.getKillStreak(victim.getUniqueId()));

            service.handlePlayerKill(victim, killer);

            assertEquals(0, database.getKillStreak(victim.getUniqueId()));
            assertEquals(1, database.getKillStreak(killer.getUniqueId()));

            List<BountyReward> rewards = database.listVisibleRewards(killer.getUniqueId(), killer.getUniqueId());
            assertEquals(1, rewards.size());
            assertEquals(BountyDatabase.SERVER_PLACER_UUID, rewards.getFirst().placerUuid());
            assertEquals(BountyDatabase.SERVER_PLACER_NAME, rewards.getFirst().placerName());
        }
    }

    @Test
    void publicBountyPersistsVisibilityAndBroadcastsAfterPlacement() throws Exception {
        try (BountyDatabase database = openDatabase()) {
            PlayerMock placer = MockBukkit.getMock().addPlayer("Placer");
            PlayerMock target = MockBukkit.getMock().addPlayer("Target");
            PlayerMock listener = MockBukkit.getMock().addPlayer("Listener");
            BountyService service = new BountyService(MockBukkit.createMockPlugin(), database);
            placer.getInventory().setItemInMainHand(new ItemStack(Material.GOLDEN_APPLE, 2));

            BountyService.PlaceResult result = service.placeBounty(
                placer,
                new KnownPlayer(target.getUniqueId(), target.getName(), 1L),
                BountyVisibility.PUBLIC
            );

            assertTrue(result.success());
            assertEquals(Material.AIR, placer.getInventory().getItemInMainHand().getType());

            BountyReward reward = database.listVisibleRewards(target.getUniqueId(), placer.getUniqueId()).getFirst();
            assertEquals(BountyVisibility.PUBLIC, reward.visibility());

            String broadcast = PlainTextComponentSerializer.plainText().serialize(listener.nextComponentMessage());
            assertTrue(broadcast.contains("Placer"));
            assertTrue(broadcast.contains("Target"));
            assertTrue(broadcast.contains("Golden Apple"));
        }
    }

    @Test
    void confirmedBountyPlacesOnlyMatchingPreviewedStack() throws Exception {
        try (BountyDatabase database = openDatabase()) {
            PlayerMock placer = MockBukkit.getMock().addPlayer("Placer");
            PlayerMock target = MockBukkit.getMock().addPlayer("Target");
            BountyService service = new BountyService(MockBukkit.createMockPlugin(), database);
            ItemStack preview = new ItemStack(Material.DIAMOND, 3);
            placer.getInventory().setItemInMainHand(preview.clone());

            BountyService.PlaceResult result = service.placeConfirmedBounty(
                placer,
                new KnownPlayer(target.getUniqueId(), target.getName(), 1L),
                BountyVisibility.NORMAL,
                preview
            );

            assertTrue(result.success());
            assertEquals(Material.AIR, placer.getInventory().getItemInMainHand().getType());
            List<BountyReward> rewards = database.listVisibleRewards(target.getUniqueId(), placer.getUniqueId());
            assertEquals(1, rewards.size());
            assertEquals(3, rewards.getFirst().item().getAmount());
        }
    }

    @Test
    void confirmedBountyRejectsChangedHeldStack() throws Exception {
        try (BountyDatabase database = openDatabase()) {
            PlayerMock placer = MockBukkit.getMock().addPlayer("Placer");
            PlayerMock target = MockBukkit.getMock().addPlayer("Target");
            BountyService service = new BountyService(MockBukkit.createMockPlugin(), database);
            ItemStack preview = new ItemStack(Material.DIAMOND, 3);
            placer.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND, 2));

            BountyService.PlaceResult result = service.placeConfirmedBounty(
                placer,
                new KnownPlayer(target.getUniqueId(), target.getName(), 1L),
                BountyVisibility.NORMAL,
                preview
            );

            assertFalse(result.success());
            assertEquals(2, placer.getInventory().getItemInMainHand().getAmount());
            assertTrue(database.listVisibleRewards(target.getUniqueId(), placer.getUniqueId()).isEmpty());
        }
    }

    @Test
    void confirmedBountyRejectsEmptyHand() throws Exception {
        try (BountyDatabase database = openDatabase()) {
            PlayerMock placer = MockBukkit.getMock().addPlayer("Placer");
            PlayerMock target = MockBukkit.getMock().addPlayer("Target");
            BountyService service = new BountyService(MockBukkit.createMockPlugin(), database);
            ItemStack preview = new ItemStack(Material.EMERALD, 1);

            BountyService.PlaceResult result = service.placeConfirmedBounty(
                placer,
                new KnownPlayer(target.getUniqueId(), target.getName(), 1L),
                BountyVisibility.NORMAL,
                preview
            );

            assertFalse(result.success());
            assertTrue(database.listVisibleRewards(target.getUniqueId(), placer.getUniqueId()).isEmpty());
        }
    }

    @Test
    void refundClearReturnsActiveRewardsToOnlinePlacers() throws Exception {
        try (BountyDatabase database = openDatabase()) {
            PlayerMock placer = MockBukkit.getMock().addPlayer("Placer");
            PlayerMock target = MockBukkit.getMock().addPlayer("Target");
            BountyService service = new BountyService(MockBukkit.createMockPlugin(), database);
            KnownPlayer knownTarget = new KnownPlayer(target.getUniqueId(), target.getName(), 1L);
            database.insertActiveBounty(knownTarget, placer.getUniqueId(), placer.getName(), new ItemStack(Material.DIAMOND, 2), 10L);

            BountyService.ClearResult result = service.clearActiveBounties(target.getUniqueId(), BountyService.ClearMode.REFUND);

            assertEquals(1, result.cleared());
            assertEquals(1, result.refunded());
            assertEquals(2, placer.getInventory().all(Material.DIAMOND).values().stream().mapToInt(ItemStack::getAmount).sum());
            assertTrue(database.listVisibleRewards(target.getUniqueId(), placer.getUniqueId()).isEmpty());
        }
    }

    @Test
    void refundClearDoesNotClearWhenAPlacerIsOffline() throws Exception {
        try (BountyDatabase database = openDatabase()) {
            PlayerMock target = MockBukkit.getMock().addPlayer("Target");
            BountyService service = new BountyService(MockBukkit.createMockPlugin(), database);
            KnownPlayer knownTarget = new KnownPlayer(target.getUniqueId(), target.getName(), 1L);
            UUID offlinePlacer = UUID.randomUUID();
            database.insertActiveBounty(knownTarget, offlinePlacer, "OfflinePlacer", new ItemStack(Material.EMERALD, 1), 10L);

            BountyService.ClearResult result = service.clearActiveBounties(target.getUniqueId(), BountyService.ClearMode.REFUND);

            assertEquals(0, result.cleared());
            assertEquals(List.of("OfflinePlacer"), result.unavailablePlacers());
            assertEquals(1, database.listVisibleRewards(target.getUniqueId(), target.getUniqueId()).size());
        }
    }

    @Test
    void selfBountySettingControlsPlacement() throws Exception {
        try (BountyDatabase database = openDatabase()) {
            PlayerMock placer = MockBukkit.getMock().addPlayer("Placer");
            BountyService service = new BountyService(MockBukkit.createMockPlugin(), database);
            KnownPlayer self = new KnownPlayer(placer.getUniqueId(), placer.getName(), 1L);

            placer.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND, 1));
            assertTrue(!service.placeBounty(placer, self).success());
            assertEquals(Material.DIAMOND, placer.getInventory().getItemInMainHand().getType());

            database.setAllowSelfBounties(true);
            BountyService.PlaceResult result = service.placeBounty(placer, self);

            assertTrue(result.success());
            assertEquals(Material.AIR, placer.getInventory().getItemInMainHand().getType());
            assertEquals(1, database.listVisibleRewards(placer.getUniqueId(), placer.getUniqueId()).size());
        }
    }

    @Test
    void nakedKillsCanBeExcludedFromStreaksAndAutobounties() throws Exception {
        try (BountyDatabase database = openDatabase()) {
            PlayerMock victim = MockBukkit.getMock().addPlayer("Victim");
            PlayerMock killer = MockBukkit.getMock().addPlayer("Killer");
            BountyService service = new BountyService(MockBukkit.createMockPlugin(), database);
            database.setCountNakedKills(false);

            service.handlePlayerKill(victim, killer);

            assertEquals(0, database.getKillStreak(killer.getUniqueId()));
            assertTrue(database.listVisibleRewards(killer.getUniqueId(), killer.getUniqueId()).isEmpty());
        }
    }

    @Test
    void spawnKillsCanBeExcludedFromStreaksAndAutobounties() throws Exception {
        try (BountyDatabase database = openDatabase()) {
            PlayerMock victim = MockBukkit.getMock().addPlayer("Victim");
            PlayerMock killer = MockBukkit.getMock().addPlayer("Killer");
            BountyService service = new BountyService(MockBukkit.createMockPlugin(), database);
            database.setSpawnKillPeriodMillis(60_000L);
            service.recordRespawn(victim);

            service.handlePlayerKill(victim, killer);

            assertEquals(0, database.getKillStreak(killer.getUniqueId()));
            assertTrue(database.listVisibleRewards(killer.getUniqueId(), killer.getUniqueId()).isEmpty());
        }
    }

    @Test
    void repeatedVictimsCanBeExcludedUntilKillerStreakResets() throws Exception {
        try (BountyDatabase database = openDatabase()) {
            PlayerMock victim = MockBukkit.getMock().addPlayer("Victim");
            PlayerMock killer = MockBukkit.getMock().addPlayer("Killer");
            BountyService service = new BountyService(MockBukkit.createMockPlugin(), database);
            database.setCountRepeatKills(false);

            service.handlePlayerKill(victim, killer);
            service.handlePlayerKill(victim, killer);

            assertEquals(1, database.getKillStreak(killer.getUniqueId()));
            assertEquals(1, database.listVisibleRewards(killer.getUniqueId(), killer.getUniqueId()).size());

            service.handlePlayerKill(killer, victim);
            service.handlePlayerKill(victim, killer);

            assertEquals(1, database.getKillStreak(killer.getUniqueId()));
            assertEquals(1, database.listVisibleRewards(killer.getUniqueId(), killer.getUniqueId()).size());
        }
    }

    @Test
    void excludedKillStillMovesActiveBountyToClaimable() throws Exception {
        try (BountyDatabase database = openDatabase()) {
            PlayerMock placer = MockBukkit.getMock().addPlayer("Placer");
            PlayerMock target = MockBukkit.getMock().addPlayer("Target");
            PlayerMock killer = MockBukkit.getMock().addPlayer("Killer");
            BountyService service = new BountyService(MockBukkit.createMockPlugin(), database);
            KnownPlayer knownTarget = new KnownPlayer(target.getUniqueId(), target.getName(), 1L);
            database.setCountNakedKills(false);
            database.insertActiveBounty(knownTarget, placer.getUniqueId(), placer.getName(), new ItemStack(Material.DIAMOND, 1), 10L);

            service.handlePlayerKill(target, killer);

            assertEquals(0, database.getKillStreak(killer.getUniqueId()));
            assertEquals(1, database.listClaimableRewards(target.getUniqueId(), killer.getUniqueId()).size());
        }
    }

    @Test
    void trackingConsumesConfiguredItemAndPersistsState() throws Exception {
        try (BountyDatabase database = openDatabase()) {
            PlayerMock placer = MockBukkit.getMock().addPlayer("Placer");
            PlayerMock target = MockBukkit.getMock().addPlayer("Target");
            PlayerMock tracker = MockBukkit.getMock().addPlayer("Tracker");
            BountyService service = new BountyService(MockBukkit.createMockPlugin(), database);
            KnownPlayer knownTarget = new KnownPlayer(target.getUniqueId(), target.getName(), 1L);
            database.insertActiveBounty(knownTarget, placer.getUniqueId(), placer.getName(), new ItemStack(Material.DIAMOND, 1), 10L);
            tracker.getInventory().addItem(new ItemStack(Material.RECOVERY_COMPASS, 1));

            BountyService.TrackingResult result = service.enablePublicTracking(tracker, target.getUniqueId(), target.getName());

            assertTrue(result.success());
            assertEquals(0, tracker.getInventory().all(Material.RECOVERY_COMPASS).values().stream().mapToInt(ItemStack::getAmount).sum());
            assertTrue(database.getTrackingState(target.getUniqueId()).isPresent());
        }
    }

    @Test
    void trackingRequiresConfiguredItemMeta() throws Exception {
        try (BountyDatabase database = openDatabase()) {
            PlayerMock placer = MockBukkit.getMock().addPlayer("Placer");
            PlayerMock target = MockBukkit.getMock().addPlayer("Target");
            PlayerMock tracker = MockBukkit.getMock().addPlayer("Tracker");
            BountyService service = new BountyService(MockBukkit.createMockPlugin(), database);
            KnownPlayer knownTarget = new KnownPlayer(target.getUniqueId(), target.getName(), 1L);
            ItemStack trackingItem = namedItem(Material.RECOVERY_COMPASS, "Tracker Token");
            database.setTrackingItem(trackingItem);
            database.insertActiveBounty(knownTarget, placer.getUniqueId(), placer.getName(), new ItemStack(Material.DIAMOND, 1), 10L);
            tracker.getInventory().addItem(new ItemStack(Material.RECOVERY_COMPASS, 1));
            tracker.getInventory().addItem(trackingItem.clone());

            BountyService.TrackingResult result = service.enablePublicTracking(tracker, target.getUniqueId(), target.getName());

            assertTrue(result.success());
            assertEquals(1, tracker.getInventory().all(Material.RECOVERY_COMPASS).values().stream().mapToInt(ItemStack::getAmount).sum());
            assertTrue(hasSimilarItem(tracker, new ItemStack(Material.RECOVERY_COMPASS, 1)));
        }
    }

    @Test
    void disablingTrackingItemClearsLiveTrackingWithoutClearingBounty() throws Exception {
        try (BountyDatabase database = openDatabase()) {
            PlayerMock placer = MockBukkit.getMock().addPlayer("Placer");
            PlayerMock target = MockBukkit.getMock().addPlayer("Target");
            PlayerMock hunter = MockBukkit.getMock().addPlayer("Hunter");
            BountyService service = new BountyService(MockBukkit.createMockPlugin(), database);
            KnownPlayer knownTarget = new KnownPlayer(target.getUniqueId(), target.getName(), 1L);
            database.setHuntHud(HuntHudMode.BOSSBAR);
            database.insertActiveBounty(knownTarget, placer.getUniqueId(), placer.getName(), new ItemStack(Material.DIAMOND, 1), 10L);
            hunter.getInventory().addItem(new ItemStack(Material.RECOVERY_COMPASS, 1));
            hunter.getInventory().addItem(new ItemStack(Material.COMPASS, 1));

            assertTrue(service.enablePublicTracking(hunter, target.getUniqueId(), target.getName()).success());
            assertTrue(service.toggleHunt(hunter, target.getUniqueId(), target.getName()).success());
            assertTrue(service.isHunting(hunter, target.getUniqueId()));
            assertEquals(2, countMaterial(hunter, Material.COMPASS));
            assertEquals(1, hunter.getBossBars().size());

            service.setTrackingItem(Material.AIR);

            assertTrue(database.getTrackingState(target.getUniqueId()).isEmpty());
            assertTrue(database.hasActiveBounty(target.getUniqueId()));
            assertFalse(service.isHunting(hunter, target.getUniqueId()));
            assertEquals(1, countMaterial(hunter, Material.COMPASS));
            assertTrue(hunter.getBossBars().isEmpty());
        }
    }

    @Test
    void disablingTrackingCompassesClearsLiveTrackingWithoutClearingBounty() throws Exception {
        try (BountyDatabase database = openDatabase()) {
            PlayerMock placer = MockBukkit.getMock().addPlayer("Placer");
            PlayerMock target = MockBukkit.getMock().addPlayer("Target");
            PlayerMock hunter = MockBukkit.getMock().addPlayer("Hunter");
            BountyService service = new BountyService(MockBukkit.createMockPlugin(), database);
            KnownPlayer knownTarget = new KnownPlayer(target.getUniqueId(), target.getName(), 1L);
            database.setHuntHud(HuntHudMode.BOSSBAR);
            database.insertActiveBounty(knownTarget, placer.getUniqueId(), placer.getName(), new ItemStack(Material.EMERALD, 1), 10L);
            hunter.getInventory().addItem(new ItemStack(Material.RECOVERY_COMPASS, 1));
            hunter.getInventory().addItem(new ItemStack(Material.COMPASS, 1));

            assertTrue(service.enablePublicTracking(hunter, target.getUniqueId(), target.getName()).success());
            assertTrue(service.toggleHunt(hunter, target.getUniqueId(), target.getName()).success());
            assertTrue(service.isHunting(hunter, target.getUniqueId()));
            assertEquals(2, countMaterial(hunter, Material.COMPASS));
            assertEquals(1, hunter.getBossBars().size());

            service.setTrackingCompassEnabled(false);

            assertFalse(database.getPluginSettings().trackingCompassEnabled());
            assertTrue(database.getTrackingState(target.getUniqueId()).isEmpty());
            assertTrue(database.hasActiveBounty(target.getUniqueId()));
            assertFalse(service.isHunting(hunter, target.getUniqueId()));
            assertEquals(1, countMaterial(hunter, Material.COMPASS));
            assertTrue(hunter.getBossBars().isEmpty());
        }
    }

    @Test
    void huntStartFailsWithoutDroppingCompassWhenInventoryIsFull() throws Exception {
        try (BountyDatabase database = openDatabase()) {
            PlayerMock placer = MockBukkit.getMock().addPlayer("Placer");
            PlayerMock target = MockBukkit.getMock().addPlayer("Target");
            PlayerMock hunter = MockBukkit.getMock().addPlayer("Hunter");
            BountyService service = new BountyService(MockBukkit.createMockPlugin(), database);
            KnownPlayer knownTarget = new KnownPlayer(target.getUniqueId(), target.getName(), 1L);
            database.insertActiveBounty(knownTarget, placer.getUniqueId(), placer.getName(), new ItemStack(Material.DIAMOND, 1), 10L);
            hunter.getInventory().addItem(new ItemStack(Material.RECOVERY_COMPASS, 1));

            assertTrue(service.enablePublicTracking(hunter, target.getUniqueId(), target.getName()).success());
            fillEmptySlots(hunter);

            BountyService.HuntResult result = service.toggleHunt(hunter, target.getUniqueId(), target.getName());

            assertFalse(result.success());
            assertFalse(service.isHunting(hunter));
            assertFalse(service.hasHuntCompass(hunter));
            assertEquals(0, countMaterial(hunter, Material.COMPASS));
        }
    }

    @Test
    void claimRewardClaimsOnlySelectedReward() throws Exception {
        try (BountyDatabase database = openDatabase()) {
            PlayerMock placer = MockBukkit.getMock().addPlayer("Placer");
            PlayerMock target = MockBukkit.getMock().addPlayer("Target");
            PlayerMock killer = MockBukkit.getMock().addPlayer("Killer");
            BountyService service = new BountyService(MockBukkit.createMockPlugin(), database);
            KnownPlayer knownTarget = new KnownPlayer(target.getUniqueId(), target.getName(), 1L);

            database.insertActiveBounty(knownTarget, placer.getUniqueId(), placer.getName(), new ItemStack(Material.DIAMOND, 1), 10L);
            database.insertActiveBounty(knownTarget, placer.getUniqueId(), placer.getName(), new ItemStack(Material.EMERALD, 1), 11L);
            database.markActiveRewardsClaimable(target.getUniqueId(), killer.getUniqueId(), killer.getName(), 20L);
            BountyReward selected = database.listClaimableRewards(target.getUniqueId(), killer.getUniqueId()).getFirst();

            BountyService.ClaimResult result = service.claimReward(killer, target.getUniqueId(), selected.id());

            assertTrue(result.success());
            assertEquals(1, database.listClaimableRewards(target.getUniqueId(), killer.getUniqueId()).size());
            assertEquals(1, killer.getInventory().all(selected.item().getType()).values().stream().mapToInt(ItemStack::getAmount).sum());
            assertEquals(RewardState.CLAIMED, database.listVisibleRewards(target.getUniqueId(), killer.getUniqueId()).stream()
                .filter(reward -> reward.id() == selected.id())
                .findFirst()
                .map(BountyReward::state)
                .orElse(RewardState.CLAIMED));
        }
    }

    @Test
    void claimRewardsClaimsSelectedGroupedRewardIdsTogether() throws Exception {
        try (BountyDatabase database = openDatabase()) {
            PlayerMock placer = MockBukkit.getMock().addPlayer("Placer");
            PlayerMock target = MockBukkit.getMock().addPlayer("Target");
            PlayerMock killer = MockBukkit.getMock().addPlayer("Killer");
            BountyService service = new BountyService(MockBukkit.createMockPlugin(), database);
            KnownPlayer knownTarget = new KnownPlayer(target.getUniqueId(), target.getName(), 1L);

            database.insertActiveBounty(knownTarget, placer.getUniqueId(), placer.getName(), new ItemStack(Material.GOLDEN_APPLE, 1), 10L);
            database.insertActiveBounty(knownTarget, placer.getUniqueId(), placer.getName(), new ItemStack(Material.GOLDEN_APPLE, 3), 11L);
            database.insertActiveBounty(knownTarget, placer.getUniqueId(), placer.getName(), new ItemStack(Material.DIAMOND, 1), 12L);
            database.markActiveRewardsClaimable(target.getUniqueId(), killer.getUniqueId(), killer.getName(), 20L);

            List<Long> goldenAppleRewardIds = database.listClaimableRewards(target.getUniqueId(), killer.getUniqueId()).stream()
                .filter(reward -> reward.item().getType() == Material.GOLDEN_APPLE)
                .map(BountyReward::id)
                .toList();

            BountyService.ClaimResult result = service.claimRewards(killer, target.getUniqueId(), goldenAppleRewardIds);

            assertTrue(result.success());
            assertEquals(1, database.listClaimableRewards(target.getUniqueId(), killer.getUniqueId()).size());
            assertEquals(4, killer.getInventory().all(Material.GOLDEN_APPLE).values().stream().mapToInt(ItemStack::getAmount).sum());
            assertEquals(0, killer.getInventory().all(Material.DIAMOND).values().stream().mapToInt(ItemStack::getAmount).sum());
        }
    }

    private BountyDatabase openDatabase() throws Exception {
        BountyDatabase database = new BountyDatabase(tempDir.resolve(UUID.randomUUID() + ".db"));
        database.migrate();
        return database;
    }

    private int countMaterial(PlayerMock player, Material material) {
        return player.getInventory().all(material).values().stream().mapToInt(ItemStack::getAmount).sum();
    }

    private ItemStack namedItem(Material material, String name) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name));
        item.setItemMeta(meta);
        return item;
    }

    private boolean hasSimilarItem(PlayerMock player, ItemStack expected) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.isSimilar(expected)) {
                return true;
            }
        }
        return false;
    }

    private void fillEmptySlots(PlayerMock player) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            if (contents[i] == null || contents[i].getType() == Material.AIR) {
                contents[i] = new ItemStack(Material.DIRT, 64);
            }
        }
        player.getInventory().setStorageContents(contents);
    }
}
