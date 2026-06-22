package me.vanillabounties.storage;

import me.vanillabounties.BukkitTestSupport;
import me.vanillabounties.model.AutoBountyFolder;
import me.vanillabounties.model.AutoBountyTemplate;
import me.vanillabounties.model.BountyReward;
import me.vanillabounties.model.BountyVisibility;
import me.vanillabounties.model.KnownPlayer;
import me.vanillabounties.model.RewardState;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BountyDatabaseTest extends BukkitTestSupport {
    @TempDir
    Path tempDir;

    @Test
    void movesActiveRewardsToOneClaimantAndClaimsOnce() throws Exception {
        try (BountyDatabase database = openDatabase()) {
            KnownPlayer target = new KnownPlayer(UUID.randomUUID(), "Target", 100L);
            UUID placer = UUID.randomUUID();
            UUID killer = UUID.randomUUID();

            database.upsertKnownPlayer(target.uuid(), target.name(), target.lastSeenAt());
            database.insertActiveBounty(target, placer, "Placer", new ItemStack(Material.DIAMOND, 3), 200L);
            database.insertActiveBounty(target, placer, "Placer", new ItemStack(Material.EMERALD, 2), 201L);

            assertEquals(2, database.markActiveRewardsClaimable(target.uuid(), killer, "Killer", 300L));
            assertEquals(0, database.markActiveRewardsClaimable(target.uuid(), UUID.randomUUID(), "Other", 301L));

            List<BountyReward> rewards = database.listClaimableRewards(target.uuid(), killer);
            assertEquals(2, rewards.size());
            assertTrue(rewards.stream().allMatch(reward -> reward.state() == RewardState.CLAIMABLE));

            List<Long> rewardIds = rewards.stream().map(BountyReward::id).toList();
            assertTrue(database.markRewardsDelivering(rewardIds, killer));
            assertFalse(database.markRewardsDelivering(rewardIds, killer));
            database.markRewardsClaimed(rewardIds, killer, 400L);

            assertTrue(database.listClaimableRewards(target.uuid(), killer).isEmpty());
        }
    }

    @Test
    void findsOneClaimableRewardByIdTargetAndClaimant() throws Exception {
        try (BountyDatabase database = openDatabase()) {
            KnownPlayer target = new KnownPlayer(UUID.randomUUID(), "Target", 100L);
            UUID placer = UUID.randomUUID();
            UUID killer = UUID.randomUUID();
            UUID otherKiller = UUID.randomUUID();

            database.insertActiveBounty(target, placer, "Placer", new ItemStack(Material.DIAMOND, 3), 200L);
            database.markActiveRewardsClaimable(target.uuid(), killer, "Killer", 300L);

            BountyReward reward = database.listClaimableRewards(target.uuid(), killer).getFirst();

            assertTrue(database.findClaimableReward(reward.id(), target.uuid(), killer).isPresent());
            assertTrue(database.findClaimableReward(reward.id(), UUID.randomUUID(), killer).isEmpty());
            assertTrue(database.findClaimableReward(reward.id(), target.uuid(), otherKiller).isEmpty());
        }
    }

    @Test
    void boardShowsActiveAndViewerClaimableRewardsOnly() throws Exception {
        try (BountyDatabase database = openDatabase()) {
            KnownPlayer target = new KnownPlayer(UUID.randomUUID(), "Target", 100L);
            UUID placer = UUID.randomUUID();
            UUID killer = UUID.randomUUID();
            UUID otherViewer = UUID.randomUUID();

            database.insertActiveBounty(target, placer, "Placer", new ItemStack(Material.GOLD_INGOT, 1), 200L);
            database.insertActiveBounty(target, placer, "Placer", new ItemStack(Material.IRON_INGOT, 1), 201L);
            database.markActiveRewardsClaimable(target.uuid(), killer, "Killer", 300L);

            assertEquals(1, database.listBoardSummaries(killer).size());
            assertTrue(database.listBoardSummaries(otherViewer).isEmpty());
        }
    }

    @Test
    void clearsOnlyActiveBountiesForOneTargetOrAllTargets() throws Exception {
        try (BountyDatabase database = openDatabase()) {
            KnownPlayer firstTarget = new KnownPlayer(UUID.randomUUID(), "FirstTarget", 100L);
            KnownPlayer secondTarget = new KnownPlayer(UUID.randomUUID(), "SecondTarget", 100L);
            UUID placer = UUID.randomUUID();
            UUID killer = UUID.randomUUID();

            database.insertActiveBounty(firstTarget, placer, "Placer", new ItemStack(Material.DIAMOND, 1), 200L);
            database.insertActiveBounty(firstTarget, placer, "Placer", new ItemStack(Material.EMERALD, 1), 201L);
            database.insertActiveBounty(secondTarget, placer, "Placer", new ItemStack(Material.GOLD_INGOT, 1), 202L);
            database.markActiveRewardsClaimable(firstTarget.uuid(), killer, "Killer", 300L);
            database.insertActiveBounty(firstTarget, placer, "Placer", new ItemStack(Material.IRON_INGOT, 1), 203L);

            assertEquals(1, database.clearActiveBounties(firstTarget.uuid()));
            assertEquals(2, database.listClaimableRewards(firstTarget.uuid(), killer).size());
            assertEquals(1, database.listVisibleRewards(secondTarget.uuid(), killer).size());

            assertEquals(1, database.clearActiveBounties());
            assertTrue(database.listVisibleRewards(secondTarget.uuid(), killer).isEmpty());
            assertEquals(2, database.listClaimableRewards(firstTarget.uuid(), killer).size());
        }
    }

    @Test
    void activeBountiesDefaultToNormalVisibilityAndCanStoreSilentOrPublic() throws Exception {
        try (BountyDatabase database = openDatabase()) {
            KnownPlayer target = new KnownPlayer(UUID.randomUUID(), "Target", 100L);
            UUID placer = UUID.randomUUID();

            database.insertActiveBounty(target, placer, "Placer", new ItemStack(Material.DIAMOND, 1), 200L);
            database.insertActiveBounty(target, placer, "Placer", new ItemStack(Material.EMERALD, 1), 201L, BountyVisibility.SILENT);
            database.insertActiveBounty(target, placer, "Placer", new ItemStack(Material.GOLD_INGOT, 1), 202L, BountyVisibility.PUBLIC);

            List<BountyReward> rewards = database.listVisibleRewards(target.uuid(), UUID.randomUUID());

            assertEquals(BountyVisibility.NORMAL, rewards.get(0).visibility());
            assertEquals(BountyVisibility.SILENT, rewards.get(1).visibility());
            assertEquals(BountyVisibility.PUBLIC, rewards.get(2).visibility());
        }
    }

    @Test
    void migrationSeedsEnabledOnKillFolderAndGoldenApple() throws Exception {
        try (BountyDatabase database = openDatabase()) {
            assertTrue(database.autoBountiesEnabled());

            AutoBountyFolder onKill = database.findAutoBountyFolderByThreshold(BountyDatabase.ON_KILL_THRESHOLD).orElseThrow();
            assertEquals("On kill", onKill.name());
            assertTrue(onKill.protectedFolder());
            assertEquals(1, onKill.templateCount());

            List<AutoBountyTemplate> templates = database.listAutoBountyTemplates(onKill.id());
            assertEquals(1, templates.size());
            assertEquals(Material.GOLDEN_APPLE, templates.getFirst().item().getType());
        }
    }

    @Test
    void autoBountyTogglePersists() throws Exception {
        try (BountyDatabase database = openDatabase()) {
            database.setAutoBountiesEnabled(false);
            assertFalse(database.autoBountiesEnabled());

            database.setAutoBountiesEnabled(true);
            assertTrue(database.autoBountiesEnabled());
        }
    }

    @Test
    void thresholdFoldersCanBeCreatedAndDeletedButOnKillIsProtected() throws Exception {
        try (BountyDatabase database = openDatabase()) {
            assertEquals(BountyDatabase.CreateFolderResult.INVALID, database.createAutoBountyThreshold(0, 1L));
            assertEquals(BountyDatabase.CreateFolderResult.CREATED, database.createAutoBountyThreshold(3, 2L));
            assertEquals(BountyDatabase.CreateFolderResult.DUPLICATE, database.createAutoBountyThreshold(3, 3L));
            assertTrue(database.findAutoBountyFolderByThreshold(3).isPresent());

            assertFalse(database.deleteAutoBountyThreshold(BountyDatabase.ON_KILL_THRESHOLD));
            assertTrue(database.deleteAutoBountyThreshold(3));
            assertTrue(database.findAutoBountyFolderByThreshold(3).isEmpty());
        }
    }

    @Test
    void templateItemsAreStoredAsClonesAndCanBeRemoved() throws Exception {
        try (BountyDatabase database = openDatabase()) {
            database.createAutoBountyThreshold(2, 1L);
            AutoBountyFolder folder = database.findAutoBountyFolderByThreshold(2).orElseThrow();
            ItemStack sample = new ItemStack(Material.DIAMOND, 5);

            assertTrue(database.addAutoBountyTemplate(folder.id(), sample));
            sample.setAmount(1);

            List<AutoBountyTemplate> templates = database.listAutoBountyTemplates(folder.id());
            assertEquals(1, templates.size());
            assertEquals(5, templates.getFirst().item().getAmount());

            assertTrue(database.removeAutoBountyTemplate(folder.id(), templates.getFirst().slot()));
            assertTrue(database.listAutoBountyTemplates(folder.id()).isEmpty());
        }
    }

    @Test
    void migrationDoesNotReAddDefaultTemplateAfterAdminRemovesIt() throws Exception {
        Path databasePath = tempDir.resolve(UUID.randomUUID() + ".db");
        long folderId;
        try (BountyDatabase database = new BountyDatabase(databasePath)) {
            database.migrate();
            AutoBountyFolder onKill = database.findAutoBountyFolderByThreshold(BountyDatabase.ON_KILL_THRESHOLD).orElseThrow();
            folderId = onKill.id();
            AutoBountyTemplate template = database.listAutoBountyTemplates(folderId).getFirst();
            assertTrue(database.removeAutoBountyTemplate(folderId, template.slot()));
        }

        try (BountyDatabase database = new BountyDatabase(databasePath)) {
            database.migrate();
            assertTrue(database.listAutoBountyTemplates(folderId).isEmpty());
        }
    }

    @Test
    void automaticBountiesFireOnKillAndExactThresholdOnly() throws Exception {
        try (BountyDatabase database = openDatabase()) {
            UUID victim = UUID.randomUUID();
            UUID killer = UUID.randomUUID();
            database.createAutoBountyThreshold(3, 1L);
            AutoBountyFolder threshold = database.findAutoBountyFolderByThreshold(3).orElseThrow();
            database.addAutoBountyTemplate(threshold.id(), new ItemStack(Material.DIAMOND, 1));

            BountyDatabase.AutoBountyApplyResult first = database.applyAutomaticBountiesForKill(victim, "Victim", killer, "Killer", 10L);
            BountyDatabase.AutoBountyApplyResult second = database.applyAutomaticBountiesForKill(victim, "Victim", killer, "Killer", 20L);
            BountyDatabase.AutoBountyApplyResult third = database.applyAutomaticBountiesForKill(victim, "Victim", killer, "Killer", 30L);
            BountyDatabase.AutoBountyApplyResult fourth = database.applyAutomaticBountiesForKill(victim, "Victim", killer, "Killer", 40L);

            assertEquals(1, first.newStreak());
            assertEquals(1, first.bountyCount());
            assertEquals(2, second.newStreak());
            assertEquals(1, second.bountyCount());
            assertEquals(3, third.newStreak());
            assertEquals(2, third.bountyCount());
            assertEquals(4, fourth.newStreak());
            assertEquals(1, fourth.bountyCount());
            assertEquals(5, database.listVisibleRewards(killer, killer).size());
        }
    }

    @Test
    void disabledAutomaticBountiesStillUpdateStreaksWithoutCreatingRewards() throws Exception {
        try (BountyDatabase database = openDatabase()) {
            UUID victim = UUID.randomUUID();
            UUID killer = UUID.randomUUID();
            database.setAutoBountiesEnabled(false);

            BountyDatabase.AutoBountyApplyResult result = database.applyAutomaticBountiesForKill(victim, "Victim", killer, "Killer", 10L);

            assertFalse(result.enabled());
            assertEquals(1, result.newStreak());
            assertEquals(0, result.bountyCount());
            assertEquals(1, database.getKillStreak(killer));
            assertEquals(0, database.getKillStreak(victim));
            assertTrue(database.listVisibleRewards(killer, killer).isEmpty());
        }
    }

    private BountyDatabase openDatabase() throws Exception {
        BountyDatabase database = new BountyDatabase(tempDir.resolve(UUID.randomUUID() + ".db"));
        database.migrate();
        return database;
    }
}
