package me.vanillabounties;

import me.vanillabounties.model.BountyReward;
import me.vanillabounties.storage.BountyDatabase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

    private BountyDatabase openDatabase() throws Exception {
        BountyDatabase database = new BountyDatabase(tempDir.resolve(UUID.randomUUID() + ".db"));
        database.migrate();
        return database;
    }
}
