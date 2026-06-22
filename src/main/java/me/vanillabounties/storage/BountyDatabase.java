package me.vanillabounties.storage;

import me.vanillabounties.model.BountyReward;
import me.vanillabounties.model.BountySummary;
import me.vanillabounties.model.AutoBountyFolder;
import me.vanillabounties.model.AutoBountyTemplate;
import me.vanillabounties.model.KnownPlayer;
import me.vanillabounties.model.RewardState;
import me.vanillabounties.model.BountyVisibility;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class BountyDatabase implements AutoCloseable {
    public static final int ON_KILL_THRESHOLD = 0;
    public static final UUID SERVER_PLACER_UUID = new UUID(0L, 0L);
    public static final String SERVER_PLACER_NAME = "Server";

    private final Connection connection;

    public BountyDatabase(Path databasePath) throws SQLException {
        this.connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath.toAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA journal_mode = WAL");
            statement.execute("PRAGMA busy_timeout = 5000");
        }
    }

    public synchronized void migrate() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE IF NOT EXISTS known_players (
                    uuid TEXT PRIMARY KEY NOT NULL,
                    name TEXT NOT NULL,
                    name_lower TEXT NOT NULL,
                    last_seen_at INTEGER NOT NULL
                )
                """);
            statement.execute("""
                CREATE INDEX IF NOT EXISTS idx_known_players_name_lower
                ON known_players(name_lower, last_seen_at DESC)
                """);
            statement.execute("""
                CREATE TABLE IF NOT EXISTS bounties (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    target_uuid TEXT NOT NULL,
                    target_name TEXT NOT NULL,
                    placer_uuid TEXT NOT NULL,
                    placer_name TEXT NOT NULL,
                    claimant_uuid TEXT,
                    claimant_name TEXT,
                    item_blob BLOB NOT NULL,
                    item_type TEXT NOT NULL,
                    item_amount INTEGER NOT NULL,
                    visibility TEXT NOT NULL DEFAULT 'NORMAL',
                    state TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    claimable_at INTEGER,
                    claimed_at INTEGER
                )
                """);
            statement.execute("""
                CREATE INDEX IF NOT EXISTS idx_bounties_target_state
                ON bounties(target_uuid, state)
                """);
            statement.execute("""
                CREATE INDEX IF NOT EXISTS idx_bounties_claimant_state
                ON bounties(claimant_uuid, state)
                """);
            statement.execute("""
                CREATE TABLE IF NOT EXISTS auto_bounty_settings (
                    key TEXT PRIMARY KEY NOT NULL,
                    value TEXT NOT NULL
                )
                """);
            statement.execute("""
                CREATE TABLE IF NOT EXISTS kill_streaks (
                    uuid TEXT PRIMARY KEY NOT NULL,
                    name TEXT NOT NULL,
                    kills INTEGER NOT NULL
                )
                """);
            statement.execute("""
                CREATE TABLE IF NOT EXISTS auto_bounty_folders (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    threshold INTEGER NOT NULL UNIQUE,
                    name TEXT NOT NULL,
                    protected INTEGER NOT NULL,
                    created_at INTEGER NOT NULL
                )
                """);
            statement.execute("""
                CREATE TABLE IF NOT EXISTS auto_bounty_items (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    folder_id INTEGER NOT NULL,
                    slot INTEGER NOT NULL,
                    item_blob BLOB NOT NULL,
                    item_type TEXT NOT NULL,
                    item_amount INTEGER NOT NULL,
                    FOREIGN KEY(folder_id) REFERENCES auto_bounty_folders(id) ON DELETE CASCADE,
                    UNIQUE(folder_id, slot)
                )
                """);
            statement.execute("""
                CREATE INDEX IF NOT EXISTS idx_auto_bounty_items_folder
                ON auto_bounty_items(folder_id, slot)
                """);
        }
        addColumnIfMissing("bounties", "visibility", "TEXT NOT NULL DEFAULT 'NORMAL'");
        seedAutoBountyDefaults();
    }

    public synchronized void upsertKnownPlayer(UUID uuid, String name, long lastSeenAt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO known_players(uuid, name, name_lower, last_seen_at)
            VALUES (?, ?, ?, ?)
            ON CONFLICT(uuid) DO UPDATE SET
                name = excluded.name,
                name_lower = excluded.name_lower,
                last_seen_at = excluded.last_seen_at
            """)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, name);
            statement.setString(3, name.toLowerCase());
            statement.setLong(4, lastSeenAt);
            statement.executeUpdate();
        }
    }

    public synchronized Optional<KnownPlayer> findKnownPlayerByName(String name) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT uuid, name, last_seen_at
            FROM known_players
            WHERE name_lower = ?
            ORDER BY last_seen_at DESC
            LIMIT 1
            """)) {
            statement.setString(1, name.toLowerCase());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(readKnownPlayer(resultSet));
            }
        }
    }

    public synchronized List<String> searchKnownPlayerNames(String prefix, int limit) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT name
            FROM known_players
            WHERE name_lower LIKE ?
            ORDER BY last_seen_at DESC
            LIMIT ?
            """)) {
            statement.setString(1, prefix.toLowerCase() + "%");
            statement.setInt(2, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<String> names = new ArrayList<>();
                while (resultSet.next()) {
                    names.add(resultSet.getString("name"));
                }
                return names;
            }
        }
    }

    public synchronized void insertActiveBounty(
        KnownPlayer target,
        UUID placerUuid,
        String placerName,
        ItemStack item,
        long now
    ) throws SQLException {
        insertActiveBounty(target.uuid(), target.name(), placerUuid, placerName, item, now, BountyVisibility.NORMAL);
    }

    public synchronized void insertActiveBounty(
        UUID targetUuid,
        String targetName,
        UUID placerUuid,
        String placerName,
        ItemStack item,
        long now
    ) throws SQLException {
        insertActiveBounty(targetUuid, targetName, placerUuid, placerName, item, now, BountyVisibility.NORMAL);
    }

    public synchronized void insertActiveBounty(
        KnownPlayer target,
        UUID placerUuid,
        String placerName,
        ItemStack item,
        long now,
        BountyVisibility visibility
    ) throws SQLException {
        insertActiveBounty(target.uuid(), target.name(), placerUuid, placerName, item, now, visibility);
    }

    public synchronized void insertActiveBounty(
        UUID targetUuid,
        String targetName,
        UUID placerUuid,
        String placerName,
        ItemStack item,
        long now,
        BountyVisibility visibility
    ) throws SQLException {
        byte[] encoded = encode(item);
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO bounties(
                target_uuid, target_name, placer_uuid, placer_name,
                item_blob, item_type, item_amount, visibility, state, created_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """)) {
            statement.setString(1, targetUuid.toString());
            statement.setString(2, targetName);
            statement.setString(3, placerUuid.toString());
            statement.setString(4, placerName);
            statement.setBytes(5, encoded);
            statement.setString(6, item.getType().name());
            statement.setInt(7, item.getAmount());
            statement.setString(8, visibility.name());
            statement.setString(9, RewardState.ACTIVE.name());
            statement.setLong(10, now);
            statement.executeUpdate();
        }
    }

    public synchronized boolean autoBountiesEnabled() throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT value
            FROM auto_bounty_settings
            WHERE key = 'enabled'
            """);
             ResultSet resultSet = statement.executeQuery()) {
            return !resultSet.next() || Boolean.parseBoolean(resultSet.getString("value"));
        }
    }

    public synchronized void setAutoBountiesEnabled(boolean enabled) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO auto_bounty_settings(key, value)
            VALUES ('enabled', ?)
            ON CONFLICT(key) DO UPDATE SET value = excluded.value
            """)) {
            statement.setString(1, Boolean.toString(enabled));
            statement.executeUpdate();
        }
    }

    public synchronized int getKillStreak(UUID uuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT kills
            FROM kill_streaks
            WHERE uuid = ?
            """)) {
            statement.setString(1, uuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt("kills") : 0;
            }
        }
    }

    public synchronized List<AutoBountyFolder> listAutoBountyFolders() throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT f.id, f.threshold, f.name, f.protected, COUNT(i.id) AS template_count
            FROM auto_bounty_folders f
            LEFT JOIN auto_bounty_items i ON i.folder_id = f.id
            GROUP BY f.id, f.threshold, f.name, f.protected
            ORDER BY f.threshold ASC
            """);
             ResultSet resultSet = statement.executeQuery()) {
            List<AutoBountyFolder> folders = new ArrayList<>();
            while (resultSet.next()) {
                folders.add(readAutoBountyFolder(resultSet));
            }
            return folders;
        }
    }

    public synchronized Optional<AutoBountyFolder> findAutoBountyFolderByThreshold(int threshold) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT f.id, f.threshold, f.name, f.protected, COUNT(i.id) AS template_count
            FROM auto_bounty_folders f
            LEFT JOIN auto_bounty_items i ON i.folder_id = f.id
            WHERE f.threshold = ?
            GROUP BY f.id, f.threshold, f.name, f.protected
            """)) {
            statement.setInt(1, threshold);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(readAutoBountyFolder(resultSet));
            }
        }
    }

    public synchronized CreateFolderResult createAutoBountyThreshold(int threshold, long now) throws SQLException {
        if (threshold < 1) {
            return CreateFolderResult.INVALID;
        }

        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT OR IGNORE INTO auto_bounty_folders(threshold, name, protected, created_at)
            VALUES (?, ?, 0, ?)
            """)) {
            statement.setInt(1, threshold);
            statement.setString(2, threshold + " Kill Streak");
            statement.setLong(3, now);
            return statement.executeUpdate() == 1 ? CreateFolderResult.CREATED : CreateFolderResult.DUPLICATE;
        }
    }

    public synchronized boolean deleteAutoBountyThreshold(int threshold) throws SQLException {
        if (threshold == ON_KILL_THRESHOLD) {
            return false;
        }

        return inTransaction(() -> {
            try (PreparedStatement deleteItems = connection.prepareStatement("""
                DELETE FROM auto_bounty_items
                WHERE folder_id IN (
                    SELECT id FROM auto_bounty_folders WHERE threshold = ? AND protected = 0
                )
                """);
                 PreparedStatement deleteFolder = connection.prepareStatement("""
                     DELETE FROM auto_bounty_folders
                     WHERE threshold = ? AND protected = 0
                     """)) {
                deleteItems.setInt(1, threshold);
                deleteItems.executeUpdate();
                deleteFolder.setInt(1, threshold);
                return deleteFolder.executeUpdate() == 1;
            }
        });
    }

    public synchronized List<AutoBountyTemplate> listAutoBountyTemplates(long folderId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT id, folder_id, slot, item_blob
            FROM auto_bounty_items
            WHERE folder_id = ?
            ORDER BY slot ASC
            """)) {
            statement.setLong(1, folderId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<AutoBountyTemplate> templates = new ArrayList<>();
                while (resultSet.next()) {
                    templates.add(new AutoBountyTemplate(
                        resultSet.getLong("id"),
                        resultSet.getLong("folder_id"),
                        resultSet.getInt("slot"),
                        decode(resultSet.getBytes("item_blob"))
                    ));
                }
                return templates;
            }
        }
    }

    public synchronized boolean addAutoBountyTemplate(long folderId, ItemStack item) throws SQLException {
        if (item == null || item.getType() == Material.AIR || item.getAmount() <= 0) {
            return false;
        }

        return inTransaction(() -> {
            boolean[] occupied = new boolean[45];
            try (PreparedStatement slots = connection.prepareStatement("""
                SELECT slot
                FROM auto_bounty_items
                WHERE folder_id = ?
                """)) {
                slots.setLong(1, folderId);
                try (ResultSet resultSet = slots.executeQuery()) {
                    while (resultSet.next()) {
                        int slot = resultSet.getInt("slot");
                        if (slot >= 0 && slot < occupied.length) {
                            occupied[slot] = true;
                        }
                    }
                }
            }

            int openSlot = -1;
            for (int i = 0; i < occupied.length; i++) {
                if (!occupied[i]) {
                    openSlot = i;
                    break;
                }
            }
            if (openSlot == -1) {
                return false;
            }

            try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO auto_bounty_items(folder_id, slot, item_blob, item_type, item_amount)
                VALUES (?, ?, ?, ?, ?)
                """)) {
                insert.setLong(1, folderId);
                insert.setInt(2, openSlot);
                insert.setBytes(3, encode(item));
                insert.setString(4, item.getType().name());
                insert.setInt(5, item.getAmount());
                insert.executeUpdate();
                return true;
            }
        });
    }

    public synchronized boolean removeAutoBountyTemplate(long folderId, int slot) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            DELETE FROM auto_bounty_items
            WHERE folder_id = ? AND slot = ?
            """)) {
            statement.setLong(1, folderId);
            statement.setInt(2, slot);
            return statement.executeUpdate() == 1;
        }
    }

    public synchronized AutoBountyApplyResult applyAutomaticBountiesForKill(
        UUID victimUuid,
        String victimName,
        UUID killerUuid,
        String killerName,
        long now
    ) throws SQLException {
        return inTransaction(() -> {
            setKillStreakInTransaction(victimUuid, victimName, 0);
            int newStreak = incrementKillStreakInTransaction(killerUuid, killerName);

            if (!autoBountiesEnabledInTransaction()) {
                return new AutoBountyApplyResult(newStreak, 0, false);
            }

            List<ItemStack> templates = listTemplatesForThresholdsInTransaction(ON_KILL_THRESHOLD, newStreak);
            try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO bounties(
                    target_uuid, target_name, placer_uuid, placer_name,
                    item_blob, item_type, item_amount, visibility, state, created_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
                for (ItemStack template : templates) {
                    ItemStack item = template.clone();
                    insert.setString(1, killerUuid.toString());
                    insert.setString(2, killerName);
                    insert.setString(3, SERVER_PLACER_UUID.toString());
                    insert.setString(4, SERVER_PLACER_NAME);
                    insert.setBytes(5, encode(item));
                    insert.setString(6, item.getType().name());
                    insert.setInt(7, item.getAmount());
                    insert.setString(8, BountyVisibility.NORMAL.name());
                    insert.setString(9, RewardState.ACTIVE.name());
                    insert.setLong(10, now);
                    insert.addBatch();
                }
                insert.executeBatch();
            }

            return new AutoBountyApplyResult(newStreak, templates.size(), true);
        });
    }

    public synchronized int markActiveRewardsClaimable(UUID targetUuid, UUID claimantUuid, String claimantName, long now) throws SQLException {
        return inTransaction(() -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE bounties
                SET state = ?, claimant_uuid = ?, claimant_name = ?, claimable_at = ?
                WHERE target_uuid = ? AND state = ?
                """)) {
                statement.setString(1, RewardState.CLAIMABLE.name());
                statement.setString(2, claimantUuid.toString());
                statement.setString(3, claimantName);
                statement.setLong(4, now);
                statement.setString(5, targetUuid.toString());
                statement.setString(6, RewardState.ACTIVE.name());
                return statement.executeUpdate();
            }
        });
    }

    public synchronized int clearActiveBounties() throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            DELETE FROM bounties
            WHERE state = ?
            """)) {
            statement.setString(1, RewardState.ACTIVE.name());
            return statement.executeUpdate();
        }
    }

    public synchronized int clearActiveBounties(UUID targetUuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            DELETE FROM bounties
            WHERE target_uuid = ?
              AND state = ?
            """)) {
            statement.setString(1, targetUuid.toString());
            statement.setString(2, RewardState.ACTIVE.name());
            return statement.executeUpdate();
        }
    }

    public synchronized List<BountySummary> listBoardSummaries(UUID viewerUuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT target_uuid, target_name,
                   SUM(CASE WHEN state = 'ACTIVE' THEN 1 ELSE 0 END) AS active_count,
                   SUM(CASE WHEN state = 'CLAIMABLE' AND claimant_uuid = ? THEN 1 ELSE 0 END) AS claimable_count,
                   COALESCE(ks.kills, 0) AS kill_streak
            FROM bounties
            LEFT JOIN kill_streaks ks ON ks.uuid = target_uuid
            WHERE state = 'ACTIVE'
               OR (state = 'CLAIMABLE' AND claimant_uuid = ?)
            GROUP BY target_uuid, target_name, ks.kills
            HAVING active_count > 0 OR claimable_count > 0
            ORDER BY claimable_count DESC, active_count DESC, lower(target_name) ASC
            """)) {
            statement.setString(1, viewerUuid.toString());
            statement.setString(2, viewerUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                List<BountySummary> summaries = new ArrayList<>();
                while (resultSet.next()) {
                    summaries.add(new BountySummary(
                        UUID.fromString(resultSet.getString("target_uuid")),
                        resultSet.getString("target_name"),
                        resultSet.getInt("active_count"),
                        resultSet.getInt("claimable_count"),
                        resultSet.getInt("kill_streak")
                    ));
                }
                return summaries;
            }
        }
    }

    public synchronized List<BountyReward> listVisibleRewards(UUID targetUuid, UUID viewerUuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT *
            FROM bounties
            WHERE target_uuid = ?
              AND (state = 'ACTIVE' OR (state = 'CLAIMABLE' AND claimant_uuid = ?))
            ORDER BY state = 'CLAIMABLE' DESC, id ASC
            """)) {
            statement.setString(1, targetUuid.toString());
            statement.setString(2, viewerUuid.toString());
            return readRewards(statement);
        }
    }

    public synchronized List<BountyReward> listClaimableRewards(UUID targetUuid, UUID claimantUuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT *
            FROM bounties
            WHERE target_uuid = ?
              AND claimant_uuid = ?
              AND state = 'CLAIMABLE'
            ORDER BY id ASC
            """)) {
            statement.setString(1, targetUuid.toString());
            statement.setString(2, claimantUuid.toString());
            return readRewards(statement);
        }
    }

    public synchronized Optional<BountyReward> findClaimableReward(long rewardId, UUID targetUuid, UUID claimantUuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT *
            FROM bounties
            WHERE id = ?
              AND target_uuid = ?
              AND claimant_uuid = ?
              AND state = 'CLAIMABLE'
            LIMIT 1
            """)) {
            statement.setLong(1, rewardId);
            statement.setString(2, targetUuid.toString());
            statement.setString(3, claimantUuid.toString());
            List<BountyReward> rewards = readRewards(statement);
            return rewards.isEmpty() ? Optional.empty() : Optional.of(rewards.getFirst());
        }
    }

    public synchronized List<BountyReward> listClaimableRewardsByIds(List<Long> rewardIds, UUID targetUuid, UUID claimantUuid) throws SQLException {
        if (rewardIds.isEmpty()) {
            return List.of();
        }

        List<BountyReward> rewards = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT *
            FROM bounties
            WHERE id = ?
              AND target_uuid = ?
              AND claimant_uuid = ?
              AND state = 'CLAIMABLE'
            """)) {
            for (long rewardId : rewardIds) {
                statement.setLong(1, rewardId);
                statement.setString(2, targetUuid.toString());
                statement.setString(3, claimantUuid.toString());
                rewards.addAll(readRewards(statement));
            }
        }
        return rewards;
    }

    public synchronized boolean markRewardsDelivering(List<Long> rewardIds, UUID claimantUuid) throws SQLException {
        if (rewardIds.isEmpty()) {
            return false;
        }

        return inTransaction(() -> {
            int updated = 0;
            try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE bounties
                SET state = ?
                WHERE id = ?
                  AND claimant_uuid = ?
                  AND state = ?
                """)) {
                for (long rewardId : rewardIds) {
                    statement.setString(1, RewardState.DELIVERING.name());
                    statement.setLong(2, rewardId);
                    statement.setString(3, claimantUuid.toString());
                    statement.setString(4, RewardState.CLAIMABLE.name());
                    updated += statement.executeUpdate();
                }
            }
            if (updated != rewardIds.size()) {
                throw new RollbackOnlyException();
            }
            return true;
        }, false);
    }

    public synchronized void markRewardsClaimed(List<Long> rewardIds, UUID claimantUuid, long now) throws SQLException {
        if (rewardIds.isEmpty()) {
            return;
        }

        inTransaction(() -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE bounties
                SET state = ?, claimed_at = ?
                WHERE id = ?
                  AND claimant_uuid = ?
                  AND state = ?
                """)) {
                for (long rewardId : rewardIds) {
                    statement.setString(1, RewardState.CLAIMED.name());
                    statement.setLong(2, now);
                    statement.setLong(3, rewardId);
                    statement.setString(4, claimantUuid.toString());
                    statement.setString(5, RewardState.DELIVERING.name());
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            return null;
        });
    }

    public synchronized void resetDeliveringRewards(List<Long> rewardIds, UUID claimantUuid) throws SQLException {
        if (rewardIds.isEmpty()) {
            return;
        }

        inTransaction(() -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE bounties
                SET state = ?
                WHERE id = ?
                  AND claimant_uuid = ?
                  AND state = ?
                """)) {
                for (long rewardId : rewardIds) {
                    statement.setString(1, RewardState.CLAIMABLE.name());
                    statement.setLong(2, rewardId);
                    statement.setString(3, claimantUuid.toString());
                    statement.setString(4, RewardState.DELIVERING.name());
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            return null;
        });
    }

    @Override
    public synchronized void close() throws SQLException {
        connection.close();
    }

    private void seedAutoBountyDefaults() throws SQLException {
        long now = System.currentTimeMillis();
        boolean createdOnKill;
        try (PreparedStatement enabled = connection.prepareStatement("""
            INSERT OR IGNORE INTO auto_bounty_settings(key, value)
            VALUES ('enabled', 'true')
            """);
             PreparedStatement onKill = connection.prepareStatement("""
                 INSERT OR IGNORE INTO auto_bounty_folders(threshold, name, protected, created_at)
                 VALUES (?, 'On kill', 1, ?)
                 """)) {
            enabled.executeUpdate();
            onKill.setInt(1, ON_KILL_THRESHOLD);
            onKill.setLong(2, now);
            createdOnKill = onKill.executeUpdate() == 1;
        }

        AutoBountyFolder folder = findAutoBountyFolderByThreshold(ON_KILL_THRESHOLD)
            .orElseThrow(() -> new SQLException("Failed to seed On kill auto-bounty folder"));
        if (createdOnKill) {
            addAutoBountyTemplate(folder.id(), new ItemStack(Material.GOLDEN_APPLE, 1));
        }
    }

    private void addColumnIfMissing(String tableName, String columnName, String definition) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("PRAGMA table_info(" + tableName + ")")) {
            while (resultSet.next()) {
                if (columnName.equalsIgnoreCase(resultSet.getString("name"))) {
                    return;
                }
            }
        }

        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + definition);
        }
    }

    private KnownPlayer readKnownPlayer(ResultSet resultSet) throws SQLException {
        return new KnownPlayer(
            UUID.fromString(resultSet.getString("uuid")),
            resultSet.getString("name"),
            resultSet.getLong("last_seen_at")
        );
    }

    private AutoBountyFolder readAutoBountyFolder(ResultSet resultSet) throws SQLException {
        return new AutoBountyFolder(
            resultSet.getLong("id"),
            resultSet.getInt("threshold"),
            resultSet.getString("name"),
            resultSet.getInt("protected") != 0,
            resultSet.getInt("template_count")
        );
    }

    private boolean autoBountiesEnabledInTransaction() throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT value
            FROM auto_bounty_settings
            WHERE key = 'enabled'
            """);
             ResultSet resultSet = statement.executeQuery()) {
            return !resultSet.next() || Boolean.parseBoolean(resultSet.getString("value"));
        }
    }

    private void setKillStreakInTransaction(UUID uuid, String name, int kills) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO kill_streaks(uuid, name, kills)
            VALUES (?, ?, ?)
            ON CONFLICT(uuid) DO UPDATE SET
                name = excluded.name,
                kills = excluded.kills
            """)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, name);
            statement.setInt(3, kills);
            statement.executeUpdate();
        }
    }

    private int incrementKillStreakInTransaction(UUID uuid, String name) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO kill_streaks(uuid, name, kills)
            VALUES (?, ?, 1)
            ON CONFLICT(uuid) DO UPDATE SET
                name = excluded.name,
                kills = kill_streaks.kills + 1
            """)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, name);
            statement.executeUpdate();
        }

        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT kills
            FROM kill_streaks
            WHERE uuid = ?
            """)) {
            statement.setString(1, uuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("Failed to read incremented kill streak");
                }
                return resultSet.getInt("kills");
            }
        }
    }

    private List<ItemStack> listTemplatesForThresholdsInTransaction(int firstThreshold, int secondThreshold) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT i.item_blob
            FROM auto_bounty_items i
            JOIN auto_bounty_folders f ON f.id = i.folder_id
            WHERE f.threshold = ? OR f.threshold = ?
            ORDER BY f.threshold ASC, i.slot ASC
            """)) {
            statement.setInt(1, firstThreshold);
            statement.setInt(2, secondThreshold);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<ItemStack> items = new ArrayList<>();
                while (resultSet.next()) {
                    items.add(decode(resultSet.getBytes("item_blob")));
                }
                return items;
            }
        }
    }

    private List<BountyReward> readRewards(PreparedStatement statement) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery()) {
            List<BountyReward> rewards = new ArrayList<>();
            while (resultSet.next()) {
                rewards.add(new BountyReward(
                    resultSet.getLong("id"),
                    UUID.fromString(resultSet.getString("target_uuid")),
                    resultSet.getString("target_name"),
                    UUID.fromString(resultSet.getString("placer_uuid")),
                    resultSet.getString("placer_name"),
                    decode(resultSet.getBytes("item_blob")),
                    RewardState.valueOf(resultSet.getString("state")),
                    readVisibility(resultSet.getString("visibility"))
                ));
            }
            return rewards;
        }
    }

    private BountyVisibility readVisibility(String rawVisibility) {
        if (rawVisibility == null || rawVisibility.isBlank()) {
            return BountyVisibility.NORMAL;
        }
        try {
            return BountyVisibility.valueOf(rawVisibility);
        } catch (IllegalArgumentException exception) {
            return BountyVisibility.NORMAL;
        }
    }

    private byte[] encode(ItemStack itemStack) throws SQLException {
        try {
            return ItemStackCodec.encode(itemStack);
        } catch (IOException exception) {
            throw new SQLException("Failed to encode ItemStack", exception);
        }
    }

    private ItemStack decode(byte[] bytes) throws SQLException {
        try {
            return ItemStackCodec.decode(bytes);
        } catch (IOException | ClassNotFoundException exception) {
            throw new SQLException("Failed to decode ItemStack", exception);
        }
    }

    private <T> T inTransaction(SqlCallable<T> callable) throws SQLException {
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            T value = callable.call();
            connection.commit();
            return value;
        } catch (SQLException | RuntimeException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    private <T> T inTransaction(SqlCallable<T> callable, T rollbackValue) throws SQLException {
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            T value = callable.call();
            connection.commit();
            return value;
        } catch (RollbackOnlyException exception) {
            connection.rollback();
            return rollbackValue;
        } catch (SQLException | RuntimeException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    public enum CreateFolderResult {
        CREATED,
        DUPLICATE,
        INVALID
    }

    public record AutoBountyApplyResult(int newStreak, int bountyCount, boolean enabled) {
    }

    @FunctionalInterface
    private interface SqlCallable<T> {
        T call() throws SQLException;
    }

    private static final class RollbackOnlyException extends RuntimeException {
    }
}
