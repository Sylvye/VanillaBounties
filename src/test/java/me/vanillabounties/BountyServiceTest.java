package me.vanillabounties;

import me.vanillabounties.model.BountyReward;
import me.vanillabounties.model.BountyVisibility;
import me.vanillabounties.model.KnownPlayer;
import me.vanillabounties.model.RewardState;
import me.vanillabounties.storage.BountyDatabase;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
