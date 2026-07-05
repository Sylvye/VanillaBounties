package me.vanillabounties;

import me.vanillabounties.model.BountyReward;
import me.vanillabounties.model.BountySummary;
import me.vanillabounties.model.AutoBountyFolder;
import me.vanillabounties.model.AutoBountyTemplate;
import me.vanillabounties.model.BountyVisibility;
import me.vanillabounties.model.HuntHudMode;
import me.vanillabounties.model.KnownPlayer;
import me.vanillabounties.model.PluginSettings;
import me.vanillabounties.model.TrackingState;
import me.vanillabounties.storage.BountyDatabase;
import me.vanillabounties.util.InventoryPlanner;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

public final class BountyService {
    private static final List<String> SPOOKY_HUNT_WARNINGS = List.of(
        "The hair on your neck prickles.",
        "You feel eyes observing you from afar...",
        "You hear your name chanted in the distance...",
        "The air feels far too still around you.",
        "Goosebumps crawl up your arms...",
        "You can't shake the feeling you're being followed.",
        "A sense of dread creeps over you."
    );

    private final Plugin plugin;
    private final BountyDatabase database;
    private final NamespacedKey huntCompassKey;
    private final Map<UUID, Long> lastRespawnAt = new HashMap<>();
    private final Map<UUID, UUID> huntedTargets = new HashMap<>();
    private final Map<UUID, BossBar> huntBossBars = new HashMap<>();
    private final Map<UUID, BossBar> huntedBossBars = new HashMap<>();
    private final BukkitTask trackingTask;

    public BountyService(Plugin plugin, BountyDatabase database) {
        this.plugin = plugin;
        this.database = database;
        this.huntCompassKey = new NamespacedKey(plugin, "hunt_target");
        this.trackingTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickTracking, 20L, 20L);
    }

    public void recordKnownPlayer(Player player) {
        try {
            database.upsertKnownPlayer(player.getUniqueId(), player.getName(), System.currentTimeMillis());
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.WARNING, "Failed to record known player " + player.getName(), exception);
        }
    }

    public Optional<KnownPlayer> resolveKnownPlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return Optional.of(new KnownPlayer(online.getUniqueId(), online.getName(), System.currentTimeMillis()));
        }

        try {
            return database.findKnownPlayerByName(name);
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.WARNING, "Failed to resolve bounty target " + name, exception);
            return Optional.empty();
        }
    }

    public List<String> searchKnownPlayerNames(String prefix, int limit) {
        try {
            return database.searchKnownPlayerNames(prefix, limit);
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.WARNING, "Failed to tab complete known players", exception);
            return List.of();
        }
    }

    public PlaceResult placeBounty(Player placer, KnownPlayer target) {
        return placeBounty(placer, target, BountyVisibility.NORMAL);
    }

    public PlaceResult placeBounty(Player placer, KnownPlayer target, BountyVisibility visibility) {
        return placeBounty(placer, target, visibility, null);
    }

    public PlaceResult placeConfirmedBounty(Player placer, KnownPlayer target, BountyVisibility visibility, ItemStack expectedItem) {
        ItemStack hand = placer.getInventory().getItemInMainHand();
        if (!sameStack(hand, expectedItem)) {
            return PlaceResult.failure(Component.text("The held item changed. Run /bounty again.", NamedTextColor.RED));
        }
        return placeBounty(placer, target, visibility, expectedItem);
    }

    private PlaceResult placeBounty(Player placer, KnownPlayer target, BountyVisibility visibility, ItemStack expectedItem) {
        if (placer.getUniqueId().equals(target.uuid())) {
            try {
                if (!database.getPluginSettings().allowSelfBounties()) {
                    return PlaceResult.failure(Component.text("You cannot place a bounty on yourself.", NamedTextColor.RED));
                }
            } catch (SQLException exception) {
                plugin.getLogger().log(Level.WARNING, "Failed to read self-bounty setting", exception);
                return PlaceResult.failure(Component.text("You cannot place a bounty on yourself.", NamedTextColor.RED));
            }
        }

        ItemStack hand = placer.getInventory().getItemInMainHand();
        if (hand.getType() == Material.AIR || hand.getAmount() <= 0) {
            return PlaceResult.failure(Component.text("Hold the item stack you want to place as a bounty.", NamedTextColor.RED));
        }
        if (containsHuntCompass(hand)) {
            return PlaceResult.failure(Component.text("You cannot place a hunt compass as a bounty.", NamedTextColor.RED));
        }
        if (expectedItem != null && !sameStack(hand, expectedItem)) {
            return PlaceResult.failure(Component.text("The held item changed. Run /bounty again.", NamedTextColor.RED));
        }

        ItemStack escrowed = hand.clone();
        placer.getInventory().setItemInMainHand(null);

        try {
            database.insertActiveBounty(target, placer.getUniqueId(), placer.getName(), escrowed, System.currentTimeMillis(), visibility);
            if (visibility == BountyVisibility.PUBLIC) {
                plugin.getServer().sendMessage(publicBountyMessage(placer, target, escrowed));
            }
            return PlaceResult.success(Component.text("Placed ")
                .color(NamedTextColor.GREEN)
                .append(itemComponent(escrowed))
                .append(Component.text(" on " + target.name() + ".", NamedTextColor.GREEN)));
        } catch (SQLException exception) {
            giveOrDrop(placer, escrowed);
            plugin.getLogger().log(Level.SEVERE, "Failed to persist bounty; item returned to " + placer.getName(), exception);
            return PlaceResult.failure(Component.text("The bounty could not be saved. Your item was returned.", NamedTextColor.RED));
        }
    }

    private boolean sameStack(ItemStack actual, ItemStack expected) {
        if (actual == null || expected == null) {
            return false;
        }
        return actual.getAmount() == expected.getAmount() && actual.isSimilar(expected);
    }

    private Component publicBountyMessage(Player placer, KnownPlayer target, ItemStack item) {
        return Component.text("[Bounty] ", NamedTextColor.GOLD)
            .append(Component.text(placer.getName(), NamedTextColor.YELLOW))
            .append(Component.text(" placed ", NamedTextColor.GRAY))
            .append(itemComponent(item))
            .append(Component.text(" on ", NamedTextColor.GRAY))
            .append(Component.text(target.name(), NamedTextColor.RED))
            .append(Component.text(".", NamedTextColor.GRAY));
    }

    private Component itemComponent(ItemStack item) {
        return Component.text(item.getAmount() + "x ", NamedTextColor.YELLOW)
            .append(item.effectiveName().colorIfAbsent(NamedTextColor.YELLOW))
            .hoverEvent(item.asHoverEvent(showItem -> showItem));
    }

    public int assignRewardsToKiller(Player victim, Player killer) {
        if (victim.getUniqueId().equals(killer.getUniqueId())) {
            return 0;
        }

        try {
            int moved = database.markActiveRewardsClaimable(victim.getUniqueId(), killer.getUniqueId(), killer.getName(), System.currentTimeMillis());
            if (moved > 0) {
                killer.sendMessage(Component.text("You can claim " + moved + " bounty reward(s) from /bounties.", NamedTextColor.GOLD));
                clearHuntsForTarget(victim.getUniqueId(), Component.text("The bounty on " + victim.getName() + " reset.", NamedTextColor.YELLOW));
            }
            return moved;
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to assign bounty rewards for " + victim.getName(), exception);
            return 0;
        }
    }

    public void handlePlayerKill(Player victim, Player killer) {
        if (victim.getUniqueId().equals(killer.getUniqueId())) {
            return;
        }

        assignRewardsToKiller(victim, killer);

        PluginSettings settings;
        try {
            settings = database.getPluginSettings();
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load kill-count settings", exception);
            return;
        }

        if (!shouldCountKill(victim, settings, System.currentTimeMillis())) {
            return;
        }

        try {
            BountyDatabase.AutoBountyApplyResult result = database.applyAutomaticBountiesForKill(
                victim.getUniqueId(),
                victim.getName(),
                killer.getUniqueId(),
                killer.getName(),
                System.currentTimeMillis(),
                settings.countRepeatKills()
            );
            if (result.enabled() && result.bountyCount() > 0) {
                killer.sendMessage(Component.text(
                    result.bountyCount() + " automatic bounty reward(s) were placed on your head. Kill streak: " + result.newStreak() + ".",
                    NamedTextColor.GOLD
                ));
            }
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to apply automatic bounties for " + killer.getName(), exception);
        }
    }

    public void recordRespawn(Player player) {
        lastRespawnAt.put(player.getUniqueId(), System.currentTimeMillis());
    }

    private boolean shouldCountKill(Player victim, PluginSettings settings, long now) {
        if (!settings.countNakedKills() && isNaked(victim)) {
            return false;
        }

        long spawnPeriod = settings.spawnKillPeriodMillis();
        Long respawnAt = lastRespawnAt.get(victim.getUniqueId());
        return spawnPeriod <= 0 || respawnAt == null || now - respawnAt >= spawnPeriod;
    }

    private boolean isNaked(Player victim) {
        PlayerInventory inventory = victim.getInventory();
        return isAir(inventory.getHelmet())
            && isAir(inventory.getChestplate())
            && isAir(inventory.getLeggings())
            && isAir(inventory.getBoots());
    }

    private boolean isAir(ItemStack item) {
        return item == null || item.getType() == Material.AIR;
    }

    public List<BountySummary> listBoard(UUID viewerUuid) throws SQLException {
        return database.listBoardSummaries(viewerUuid);
    }

    public List<BountyReward> listRewards(UUID targetUuid, UUID viewerUuid) throws SQLException {
        return database.listVisibleRewards(targetUuid, viewerUuid);
    }

    public int clearAllActiveBounties() throws SQLException {
        return clearAllActiveBounties(ClearMode.DELETE).cleared();
    }

    public ClearResult clearAllActiveBounties(ClearMode mode) throws SQLException {
        List<TrackingState> tracked = database.listTrackingStates();
        List<BountyReward> activeRewards = mode == ClearMode.REFUND ? database.listActiveRewards() : List.of();
        List<String> unavailablePlacers = unavailableRefundPlacers(activeRewards);
        if (!unavailablePlacers.isEmpty()) {
            return new ClearResult(0, 0, unavailablePlacers);
        }

        int cleared = database.clearActiveBounties();
        if (cleared > 0) {
            if (mode == ClearMode.REFUND) {
                refundRewards(activeRewards);
            }
            for (TrackingState state : tracked) {
                clearHuntsForTarget(state.targetUuid(), Component.text("The bounty on " + state.targetName() + " was cleared.", NamedTextColor.YELLOW));
            }
        }
        return new ClearResult(cleared, mode == ClearMode.REFUND ? refundableRewardCount(activeRewards) : 0, List.of());
    }

    public int clearActiveBounties(UUID targetUuid) throws SQLException {
        return clearActiveBounties(targetUuid, ClearMode.DELETE).cleared();
    }

    public ClearResult clearActiveBounties(UUID targetUuid, ClearMode mode) throws SQLException {
        Optional<TrackingState> tracked = database.getTrackingState(targetUuid);
        List<BountyReward> activeRewards = mode == ClearMode.REFUND ? database.listActiveRewards(targetUuid) : List.of();
        List<String> unavailablePlacers = unavailableRefundPlacers(activeRewards);
        if (!unavailablePlacers.isEmpty()) {
            return new ClearResult(0, 0, unavailablePlacers);
        }

        int cleared = database.clearActiveBounties(targetUuid);
        if (cleared > 0 && mode == ClearMode.REFUND) {
            refundRewards(activeRewards);
        }
        if (cleared > 0 && tracked.isPresent()) {
            clearHuntsForTarget(targetUuid, Component.text("The bounty on " + tracked.get().targetName() + " was cleared.", NamedTextColor.YELLOW));
        }
        return new ClearResult(cleared, mode == ClearMode.REFUND ? refundableRewardCount(activeRewards) : 0, List.of());
    }

    private List<String> unavailableRefundPlacers(List<BountyReward> rewards) {
        Set<UUID> seen = new HashSet<>();
        List<String> unavailable = new ArrayList<>();
        for (BountyReward reward : rewards) {
            if (BountyDatabase.SERVER_PLACER_UUID.equals(reward.placerUuid()) || !seen.add(reward.placerUuid())) {
                continue;
            }
            if (Bukkit.getPlayer(reward.placerUuid()) == null) {
                unavailable.add(reward.placerName());
            }
        }
        return unavailable;
    }

    private void refundRewards(List<BountyReward> rewards) {
        for (BountyReward reward : rewards) {
            if (BountyDatabase.SERVER_PLACER_UUID.equals(reward.placerUuid())) {
                continue;
            }
            Player placer = Bukkit.getPlayer(reward.placerUuid());
            if (placer != null) {
                giveOrDrop(placer, reward.item());
            }
        }
    }

    private int refundableRewardCount(List<BountyReward> rewards) {
        int count = 0;
        for (BountyReward reward : rewards) {
            if (!BountyDatabase.SERVER_PLACER_UUID.equals(reward.placerUuid())) {
                count++;
            }
        }
        return count;
    }

    public boolean autoBountiesEnabled() throws SQLException {
        return database.autoBountiesEnabled();
    }

    public void setAutoBountiesEnabled(boolean enabled) throws SQLException {
        database.setAutoBountiesEnabled(enabled);
    }

    public PluginSettings getPluginSettings() throws SQLException {
        return database.getPluginSettings();
    }

    public void setCountNakedKills(boolean enabled) throws SQLException {
        database.setCountNakedKills(enabled);
    }

    public void setSpawnKillPeriodMillis(long millis) throws SQLException {
        database.setSpawnKillPeriodMillis(millis);
    }

    public void setCountRepeatKills(boolean enabled) throws SQLException {
        database.setCountRepeatKills(enabled);
    }

    public void setAllowSelfBounties(boolean enabled) throws SQLException {
        database.setAllowSelfBounties(enabled);
    }

    public void setTrackingItem(Material material) throws SQLException {
        setTrackingItem(material == null || material == Material.AIR ? null : new ItemStack(material, 1));
    }

    public void setTrackingItem(ItemStack item) throws SQLException {
        database.setTrackingItem(item);
        Material material = item == null ? Material.AIR : item.getType();
        if (material == null || material == Material.AIR) {
            shutdownTracking(Component.text("Bounty tracking was disabled by an admin.", NamedTextColor.YELLOW));
        }
    }

    public void setTrackingPeriodMillis(long millis) throws SQLException {
        database.setTrackingPeriodMillis(millis);
    }

    public void setTrackingGlowingDurationMillis(long millis) throws SQLException {
        database.setTrackingGlowingDurationMillis(millis);
    }

    public void setTrackingCompassEnabled(boolean enabled) throws SQLException {
        database.setTrackingCompassEnabled(enabled);
        if (!enabled) {
            shutdownTracking(Component.text("Bounty tracking compasses were disabled by an admin.", NamedTextColor.YELLOW));
        }
    }

    public void setHuntHud(HuntHudMode mode) throws SQLException {
        database.setHuntHud(mode);
    }

    public void setHuntWarningHud(HuntHudMode mode) throws SQLException {
        database.setHuntWarningHud(mode);
    }

    public void setSpookyHuntWarningsEnabled(boolean enabled) throws SQLException {
        database.setSpookyHuntWarningsEnabled(enabled);
    }

    public void setHuntGracePeriodMillis(long millis) throws SQLException {
        database.setHuntGracePeriodMillis(millis);
    }

    public void setHuntRevealWarningMillis(long millis) throws SQLException {
        database.setHuntRevealWarningMillis(millis);
    }

    public void setHuntDurationMillis(long millis) throws SQLException {
        database.setHuntDurationMillis(millis);
        if (millis <= 0) {
            hideAllHuntTimerBossBars();
        }
    }

    public void setHuntTimerBossBarEnabled(boolean enabled) throws SQLException {
        database.setHuntTimerBossBarEnabled(enabled);
        if (!enabled) {
            hideAllHuntTimerBossBars();
        }
    }

    public Optional<TrackingState> getTrackingState(UUID targetUuid) throws SQLException {
        return database.getTrackingState(targetUuid);
    }

    public boolean isHunting(Player hunter, UUID targetUuid) {
        return targetUuid.equals(huntedTargets.get(hunter.getUniqueId()));
    }

    public boolean isHunting(Player hunter) {
        return huntedTargets.containsKey(hunter.getUniqueId());
    }

    public TrackingResult enablePublicTracking(Player viewer, UUID targetUuid, String targetName) {
        PluginSettings settings;
        try {
            settings = database.getPluginSettings();
            if (!settings.trackingEnabled()) {
                return TrackingResult.failure(Component.text("Public tracking is disabled.", NamedTextColor.RED));
            }
            if (!database.hasActiveBounty(targetUuid)) {
                return TrackingResult.failure(Component.text("That player no longer has an active bounty.", NamedTextColor.RED));
            }
            if (database.getTrackingState(targetUuid).isPresent()) {
                return TrackingResult.failure(Component.text("That bounty is already being tracked.", NamedTextColor.RED));
            }
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load tracking state", exception);
            return TrackingResult.failure(Component.text("Could not start tracking.", NamedTextColor.RED));
        }

        ItemStack trackingItem = settings.trackingItem();
        if (!consumeOne(viewer, trackingItem)) {
            return TrackingResult.failure(Component.text("You need 1x ", NamedTextColor.RED)
                .append(trackingItemDisplayName(trackingItem))
                .append(Component.text(" to track that bounty.", NamedTextColor.RED)));
        }

        try {
            boolean enabled = database.enableTracking(targetUuid, targetName, viewer.getUniqueId(), viewer.getName(), System.currentTimeMillis());
            if (!enabled) {
                giveOrDrop(viewer, oneItem(trackingItem));
                return TrackingResult.failure(Component.text("That bounty is already being tracked.", NamedTextColor.RED));
            }
        } catch (SQLException exception) {
            giveOrDrop(viewer, oneItem(trackingItem));
            plugin.getLogger().log(Level.SEVERE, "Failed to enable tracking", exception);
            return TrackingResult.failure(Component.text("Could not start tracking.", NamedTextColor.RED));
        }

        plugin.getServer().sendMessage(Component.text("[Bounty] ", NamedTextColor.GOLD)
            .append(Component.text(viewer.getName(), NamedTextColor.YELLOW))
            .append(Component.text(" started public tracking on ", NamedTextColor.GRAY))
            .append(Component.text(targetName, NamedTextColor.RED))
            .append(Component.text(".", NamedTextColor.GRAY)));
        try {
            Optional<TrackingState> state = database.getTrackingState(targetUuid);
            if (state.isPresent()) {
                tickTrackingState(state.get(), settings, System.currentTimeMillis());
            }
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.WARNING, "Failed to start hunt grace period", exception);
        }
        return TrackingResult.success(Component.text("Started public tracking on " + targetName + ".", NamedTextColor.GREEN));
    }

    public HuntResult toggleHunt(Player hunter, UUID targetUuid, String targetName) {
        UUID currentTarget = huntedTargets.get(hunter.getUniqueId());
        if (targetUuid.equals(currentTarget)) {
            stopHunt(hunter, null);
            return HuntResult.success(Component.text("Stopped hunting " + targetName + ".", NamedTextColor.YELLOW));
        }

        PluginSettings settings;
        TrackingState trackingState;
        try {
            settings = database.getPluginSettings();
            if (!settings.trackingCompassEnabled()) {
                return HuntResult.failure(Component.text("Hunt compasses are disabled.", NamedTextColor.RED));
            }
            Optional<TrackingState> maybeState = database.getTrackingState(targetUuid);
            if (maybeState.isEmpty()) {
                return HuntResult.failure(Component.text("That bounty is not being tracked.", NamedTextColor.RED));
            }
            trackingState = maybeState.get();
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to start hunt", exception);
            return HuntResult.failure(Component.text("Could not start hunting.", NamedTextColor.RED));
        }

        stopHunt(hunter, null);
        huntedTargets.put(hunter.getUniqueId(), targetUuid);
        if (!updateHuntCompass(hunter, trackingState)) {
            stopHunt(hunter, Component.text("Make inventory space before starting a hunt.", NamedTextColor.YELLOW));
            return HuntResult.failure(Component.text("Make inventory space before starting a hunt.", NamedTextColor.RED));
        }
        if (trackingState.huntStartedAt() > 0) {
            updateHuntTimerBossBars(trackingState, settings, trackingState.huntStartedAt(), System.currentTimeMillis());
        }
        sendHuntHud(hunter, settings, Component.text("Hunting " + targetName + ".", NamedTextColor.GOLD));
        return HuntResult.success(Component.text("Hunting " + targetName + ".", NamedTextColor.GREEN));
    }

    public List<AutoBountyFolder> listAutoBountyFolders() throws SQLException {
        return database.listAutoBountyFolders();
    }

    public Optional<AutoBountyFolder> findAutoBountyFolder(int threshold) throws SQLException {
        return database.findAutoBountyFolderByThreshold(threshold);
    }

    public BountyDatabase.CreateFolderResult createAutoBountyThreshold(int threshold) throws SQLException {
        return database.createAutoBountyThreshold(threshold, System.currentTimeMillis());
    }

    public boolean deleteAutoBountyThreshold(int threshold) throws SQLException {
        return database.deleteAutoBountyThreshold(threshold);
    }

    public List<AutoBountyTemplate> listAutoBountyTemplates(long folderId) throws SQLException {
        return database.listAutoBountyTemplates(folderId);
    }

    public boolean addAutoBountyTemplate(long folderId, ItemStack item) throws SQLException {
        return database.addAutoBountyTemplate(folderId, item == null ? null : item.clone());
    }

    public boolean removeAutoBountyTemplate(long folderId, int slot) throws SQLException {
        return database.removeAutoBountyTemplate(folderId, slot);
    }

    public ClaimResult claimRewards(Player claimant, UUID targetUuid) {
        List<BountyReward> rewards;
        try {
            rewards = database.listClaimableRewards(targetUuid, claimant.getUniqueId());
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to read claimable rewards for " + claimant.getName(), exception);
            return ClaimResult.failure(Component.text("Claim failed because rewards could not be loaded.", NamedTextColor.RED));
        }

        if (rewards.isEmpty()) {
            return ClaimResult.failure(Component.text("You do not have any claimable rewards for that player.", NamedTextColor.RED));
        }

        List<ItemStack> items = rewards.stream().map(BountyReward::item).toList();
        Optional<ItemStack[]> plannedContents = InventoryPlanner.planStorageContents(claimant.getInventory().getStorageContents(), items);
        if (plannedContents.isEmpty()) {
            return ClaimResult.failure(Component.text("Make enough inventory space to claim every reward at once.", NamedTextColor.RED));
        }

        List<Long> rewardIds = rewards.stream().map(BountyReward::id).toList();
        try {
            boolean locked = database.markRewardsDelivering(rewardIds, claimant.getUniqueId());
            if (!locked) {
                return ClaimResult.failure(Component.text("Those rewards were already claimed or changed. Reopen /bounties.", NamedTextColor.RED));
            }

            claimant.getInventory().setStorageContents(plannedContents.get());
            database.markRewardsClaimed(rewardIds, claimant.getUniqueId(), System.currentTimeMillis());
            return ClaimResult.success(Component.text("Claimed " + rewards.size() + " bounty reward(s).", NamedTextColor.GREEN));
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to claim bounty rewards for " + claimant.getName(), exception);
            try {
                database.resetDeliveringRewards(rewardIds, claimant.getUniqueId());
            } catch (SQLException resetException) {
                plugin.getLogger().log(Level.SEVERE, "Failed to reset undelivered bounty rewards", resetException);
            }
            return ClaimResult.failure(Component.text("Claim failed before delivery. Try again.", NamedTextColor.RED));
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed while delivering bounty rewards to " + claimant.getName(), exception);
            return ClaimResult.failure(Component.text("Claim failed during delivery. Contact an admin.", NamedTextColor.RED));
        }
    }

    public ClaimResult claimReward(Player claimant, UUID targetUuid, long rewardId) {
        return claimRewards(claimant, targetUuid, List.of(rewardId));
    }

    public ClaimResult claimRewards(Player claimant, UUID targetUuid, List<Long> rewardIds) {
        if (rewardIds.isEmpty()) {
            return ClaimResult.failure(Component.text("No rewards were selected to claim.", NamedTextColor.RED));
        }

        List<BountyReward> rewards;
        try {
            rewards = database.listClaimableRewardsByIds(rewardIds, targetUuid, claimant.getUniqueId());
            if (rewards.size() != rewardIds.size()) {
                return ClaimResult.failure(Component.text("One or more rewards are no longer claimable. Reopen /bounties.", NamedTextColor.RED));
            }
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to read selected claimable rewards for " + claimant.getName(), exception);
            return ClaimResult.failure(Component.text("Claim failed because rewards could not be loaded.", NamedTextColor.RED));
        }

        List<ItemStack> items = rewards.stream().map(BountyReward::item).toList();
        Optional<ItemStack[]> plannedContents = InventoryPlanner.planStorageContents(claimant.getInventory().getStorageContents(), items);
        if (plannedContents.isEmpty()) {
            return ClaimResult.failure(Component.text("Make enough inventory space to claim that reward stack.", NamedTextColor.RED));
        }

        try {
            boolean locked = database.markRewardsDelivering(rewardIds, claimant.getUniqueId());
            if (!locked) {
                return ClaimResult.failure(Component.text("Those rewards were already claimed or changed. Reopen /bounties.", NamedTextColor.RED));
            }

            claimant.getInventory().setStorageContents(plannedContents.get());
            database.markRewardsClaimed(rewardIds, claimant.getUniqueId(), System.currentTimeMillis());
            return ClaimResult.success(Component.text("Claimed " + rewards.size() + " bounty reward(s).", NamedTextColor.GREEN));
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to claim selected bounty rewards for " + claimant.getName(), exception);
            try {
                database.resetDeliveringRewards(rewardIds, claimant.getUniqueId());
            } catch (SQLException resetException) {
                plugin.getLogger().log(Level.SEVERE, "Failed to reset undelivered bounty rewards", resetException);
            }
            return ClaimResult.failure(Component.text("Claim failed before delivery. Try again.", NamedTextColor.RED));
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed while delivering selected bounty rewards to " + claimant.getName(), exception);
            return ClaimResult.failure(Component.text("Claim failed during delivery. Contact an admin.", NamedTextColor.RED));
        }
    }

    public void stopHunt(Player hunter) {
        stopHunt(hunter, null);
    }

    public void stopHuntForDroppedCompass(Player hunter) {
        removeHuntCompasses(hunter);
        stopHunt(hunter, Component.text("Hunt stopped because the compass was dropped.", NamedTextColor.YELLOW));
        Bukkit.getScheduler().runTask(plugin, () -> removeHuntCompasses(hunter));
    }

    private void shutdownTracking(Component message) throws SQLException {
        database.disableAllTracking();
        clearAllHunts(message);
    }

    public void shutdown() {
        trackingTask.cancel();
        for (Player player : Bukkit.getOnlinePlayers()) {
            stopHunt(player, null);
        }
        hideAllHuntTimerBossBars();
    }

    private void tickTracking() {
        PluginSettings settings;
        try {
            settings = database.getPluginSettings();
            if (!settings.trackingEnabled()) {
                return;
            }

            long now = System.currentTimeMillis();
            for (TrackingState state : database.listTrackingStates()) {
                if (!database.hasActiveBounty(state.targetUuid())) {
                    database.disableTracking(state.targetUuid());
                    clearHuntsForTarget(state.targetUuid(), Component.text("The bounty on " + state.targetName() + " reset.", NamedTextColor.YELLOW));
                    hideHuntTimerBossBarForTarget(state.targetUuid());
                    continue;
                }
                tickTrackingState(state, settings, now);
            }
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.WARNING, "Failed to tick public bounty tracking", exception);
        }
    }

    private void tickTrackingState(TrackingState state, PluginSettings settings, long now) throws SQLException {
        long warnedAt = state.warnedAt();
        if (warnedAt <= 0) {
            Player target = Bukkit.getPlayer(state.targetUuid());
            if (target == null || !target.isOnline()) {
                hideHuntTimerBossBarForTarget(state.targetUuid());
                return;
            }
            sendInitialHuntWarning(target, settings);
            warnedAt = now;
            database.updateTrackingWarnedAt(state.targetUuid(), warnedAt);
        }

        long huntStartsAt = warnedAt + settings.huntGracePeriodMillis();
        if (now < huntStartsAt) {
            sendRevealWarningIfDue(state, settings, huntStartsAt, now);
            hideHuntTimerBossBars(state);
            return;
        }

        long huntStartedAt = state.huntStartedAt();
        if (huntStartedAt <= 0) {
            huntStartedAt = huntStartsAt;
            database.updateTrackingHuntStartedAt(state.targetUuid(), huntStartedAt);
        }

        if (settings.huntDurationMillis() > 0 && now - huntStartedAt >= settings.huntDurationMillis()) {
            endTrackedHunt(state);
            return;
        }

        updateHuntTimerBossBars(state, settings, huntStartedAt, now);
        long nextRevealAt = state.lastRevealedAt() <= 0
            ? huntStartsAt
            : state.lastRevealedAt() + settings.trackingPeriodMillis();
        if (now >= nextRevealAt) {
            revealTrackedTarget(state.targetUuid(), state.targetName(), settings);
            return;
        }
        sendRevealWarningIfDue(state, settings, nextRevealAt, now);
    }

    private void sendInitialHuntWarning(Player target, PluginSettings settings) {
        String warning = settings.spookyHuntWarningsEnabled()
            ? SPOOKY_HUNT_WARNINGS.get(ThreadLocalRandom.current().nextInt(SPOOKY_HUNT_WARNINGS.size()))
            : "You are being hunted.";
        if (settings.huntWarningHud() == HuntHudMode.ACTION_BAR) {
            target.sendActionBar(Component.text(warning + " Position revealed in " + formatDuration(settings.huntGracePeriodMillis()) + ".", NamedTextColor.RED));
            return;
        }
        target.sendMessage(Component.text(warning, NamedTextColor.RED));
        sendHuntWarningHud(target, settings, Component.text(
            "Your position will be revealed in " + formatDuration(settings.huntGracePeriodMillis()) + ".",
            NamedTextColor.YELLOW
        ));
    }

    private void sendRevealWarningIfDue(TrackingState state, PluginSettings settings, long revealAt, long now) throws SQLException {
        long warningMillis = settings.huntRevealWarningMillis();
        if (warningMillis <= 0 || revealAt <= now || state.revealWarningSentFor() == revealAt) {
            return;
        }
        if (now < revealAt - warningMillis) {
            return;
        }
        Player target = Bukkit.getPlayer(state.targetUuid());
        if (target == null || !target.isOnline()) {
            return;
        }
        sendHuntWarningHud(target, settings, Component.text(
            "Your position will be revealed in " + formatDuration(revealAt - now) + ".",
            NamedTextColor.YELLOW
        ));
        database.updateTrackingRevealWarningSentFor(state.targetUuid(), revealAt);
    }

    private void endTrackedHunt(TrackingState state) throws SQLException {
        if (!database.disableTracking(state.targetUuid())) {
            return;
        }
        Player target = Bukkit.getPlayer(state.targetUuid());
        if (target != null && target.isOnline()) {
            target.sendMessage(Component.text("The hunt on you has ended.", NamedTextColor.YELLOW));
        }
        clearHuntsForTarget(state.targetUuid(), Component.text("The hunt on " + state.targetName() + " ended.", NamedTextColor.YELLOW));
        hideHuntTimerBossBarForTarget(state.targetUuid());
    }

    private void revealTrackedTarget(UUID targetUuid, String targetName, PluginSettings settings) {
        Player target = Bukkit.getPlayer(targetUuid);
        if (target == null || !target.isOnline()) {
            return;
        }

        Location location = target.getLocation();
        long now = System.currentTimeMillis();
        try {
            database.updateTrackingLocation(
                targetUuid,
                location.getWorld().getName(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ(),
                now
            );
            TrackingState updated = database.getTrackingState(targetUuid).orElse(null);
            if (updated == null) {
                return;
            }

            long glowMillis = settings.trackingGlowingDurationMillis();
            if (glowMillis > 0) {
                int ticks = Math.max(1, (int) (glowMillis / 50L));
                target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, ticks, 0, false, false, false));
            }

            sendHuntWarningHud(target, settings, Component.text("Your position has been revealed.", NamedTextColor.YELLOW));

            Component message = Component.text(targetName + " revealed at " + formatLocation(updated) + ".", NamedTextColor.GOLD);
            notifyHunters(targetUuid, message, updated);
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.WARNING, "Failed to reveal tracked bounty target", exception);
        }
    }

    private void notifyHunters(UUID targetUuid, Component message, TrackingState state) throws SQLException {
        PluginSettings settings = database.getPluginSettings();
        for (Map.Entry<UUID, UUID> entry : new ArrayList<>(huntedTargets.entrySet())) {
            if (!targetUuid.equals(entry.getValue())) {
                continue;
            }
            Player hunter = Bukkit.getPlayer(entry.getKey());
            if (hunter == null || !hunter.isOnline()) {
                huntedTargets.remove(entry.getKey());
                continue;
            }
            if (!updateHuntCompass(hunter, state)) {
                stopHunt(hunter, Component.text("Hunt stopped because your inventory could not hold the compass.", NamedTextColor.YELLOW));
                continue;
            }
            sendHuntHud(hunter, settings, message);
        }
    }

    private boolean updateHuntCompass(Player hunter, TrackingState state) {
        if (!state.hasLocation()) {
            return true;
        }

        World world = Bukkit.getWorld(state.worldName());
        if (world == null) {
            return true;
        }

        removeHuntCompasses(hunter);
        ItemStack compass = new ItemStack(Material.COMPASS);
        ItemMeta meta = compass.getItemMeta();
        if (!(meta instanceof CompassMeta compassMeta)) {
            return false;
        }
        compassMeta.displayName(Component.text("Hunt: " + state.targetName(), NamedTextColor.GOLD));
        compassMeta.lore(List.of(
            Component.text("Tracks last revealed location.", NamedTextColor.GRAY),
            Component.text(formatLocation(state), NamedTextColor.YELLOW)
        ));
        compassMeta.setLodestone(new Location(world, state.x(), state.y(), state.z()));
        compassMeta.setLodestoneTracked(false);
        compassMeta.getPersistentDataContainer().set(huntCompassKey, PersistentDataType.STRING, state.targetUuid().toString());
        compass.setItemMeta(compassMeta);
        return giveHuntCompass(hunter, compass);
    }

    private void sendHuntHud(Player hunter, PluginSettings settings, Component message) {
        if (settings.huntHud() == HuntHudMode.CHAT) {
            hunter.sendMessage(message);
            return;
        }
        hunter.sendActionBar(message);
    }

    private void sendHuntWarningHud(Player target, PluginSettings settings, Component message) {
        if (settings.huntWarningHud() == HuntHudMode.ACTION_BAR) {
            target.sendActionBar(message);
            return;
        }
        target.sendMessage(message);
    }

    private void updateHuntTimerBossBars(TrackingState state, PluginSettings settings, long huntStartedAt, long now) {
        if (!settings.huntTimerBossBarEnabled() || settings.huntDurationMillis() <= 0) {
            hideHuntTimerBossBars(state);
            return;
        }

        long remainingMillis = Math.max(0L, huntStartedAt + settings.huntDurationMillis() - now);
        float progress = Math.max(0.0f, Math.min(1.0f, (float) remainingMillis / (float) settings.huntDurationMillis()));
        Player target = Bukkit.getPlayer(state.targetUuid());
        if (target != null && target.isOnline()) {
            BossBar targetBar = huntedBossBars.computeIfAbsent(state.targetUuid(), ignored ->
                BossBar.bossBar(Component.empty(), progress, BossBar.Color.RED, BossBar.Overlay.PROGRESS));
            targetBar.name(Component.text("Hunted: " + formatTimerDuration(remainingMillis) + " left", NamedTextColor.RED));
            targetBar.progress(progress);
            target.showBossBar(targetBar);
        } else {
            hideHuntTimerBossBarForTarget(state.targetUuid());
        }

        for (Map.Entry<UUID, UUID> entry : new ArrayList<>(huntedTargets.entrySet())) {
            if (!state.targetUuid().equals(entry.getValue())) {
                continue;
            }
            Player hunter = Bukkit.getPlayer(entry.getKey());
            if (hunter == null || !hunter.isOnline()) {
                huntedTargets.remove(entry.getKey());
                continue;
            }
            BossBar hunterBar = huntBossBars.computeIfAbsent(entry.getKey(), ignored ->
                BossBar.bossBar(Component.empty(), progress, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS));
            hunterBar.name(Component.text("Hunt: " + state.targetName() + " - " + formatTimerDuration(remainingMillis) + " left", NamedTextColor.GOLD));
            hunterBar.progress(progress);
            hunter.showBossBar(hunterBar);
        }
    }

    private void hideHuntTimerBossBars(TrackingState state) {
        hideHuntTimerBossBarForTarget(state.targetUuid());
        for (Map.Entry<UUID, UUID> entry : new ArrayList<>(huntedTargets.entrySet())) {
            if (!state.targetUuid().equals(entry.getValue())) {
                continue;
            }
            Player hunter = Bukkit.getPlayer(entry.getKey());
            BossBar bossBar = huntBossBars.remove(entry.getKey());
            if (hunter != null && bossBar != null) {
                hunter.hideBossBar(bossBar);
            }
        }
    }

    private void hideHuntTimerBossBarForTarget(UUID targetUuid) {
        BossBar bossBar = huntedBossBars.remove(targetUuid);
        Player target = Bukkit.getPlayer(targetUuid);
        if (target != null && bossBar != null) {
            target.hideBossBar(bossBar);
        }
    }

    private void hideAllHuntTimerBossBars() {
        for (UUID hunterUuid : new ArrayList<>(huntBossBars.keySet())) {
            Player hunter = Bukkit.getPlayer(hunterUuid);
            BossBar bossBar = huntBossBars.remove(hunterUuid);
            if (hunter != null && bossBar != null) {
                hunter.hideBossBar(bossBar);
            }
        }
        for (UUID targetUuid : new ArrayList<>(huntedBossBars.keySet())) {
            hideHuntTimerBossBarForTarget(targetUuid);
        }
    }

    private void clearHuntsForTarget(UUID targetUuid, Component message) {
        for (UUID hunterUuid : new ArrayList<>(huntedTargets.keySet())) {
            if (!targetUuid.equals(huntedTargets.get(hunterUuid))) {
                continue;
            }
            Player hunter = Bukkit.getPlayer(hunterUuid);
            if (hunter != null) {
                stopHunt(hunter, message);
            } else {
                huntedTargets.remove(hunterUuid);
                huntBossBars.remove(hunterUuid);
            }
        }
        hideHuntTimerBossBarForTarget(targetUuid);
    }

    private void clearAllHunts(Component message) {
        Set<UUID> affectedHunters = new HashSet<>(huntedTargets.keySet());
        affectedHunters.addAll(huntBossBars.keySet());
        for (Player player : Bukkit.getOnlinePlayers()) {
            boolean affected = affectedHunters.contains(player.getUniqueId()) || hasHuntCompass(player);
            stopHunt(player, affected ? message : null);
        }
        hideAllHuntTimerBossBars();
        huntedTargets.clear();
        huntBossBars.clear();
        huntedBossBars.clear();
    }

    private void stopHunt(Player hunter, Component message) {
        huntedTargets.remove(hunter.getUniqueId());
        removeHuntCompasses(hunter);
        BossBar bossBar = huntBossBars.remove(hunter.getUniqueId());
        if (bossBar != null) {
            hunter.hideBossBar(bossBar);
        }
        if (message != null) {
            hunter.sendMessage(message);
        }
    }

    public boolean hasHuntCompass(Player hunter) {
        for (ItemStack item : hunter.getInventory().getContents()) {
            if (containsHuntCompass(item)) {
                return true;
            }
        }
        return false;
    }

    public void removeHuntCompasses(Player hunter) {
        PlayerInventory inventory = hunter.getInventory();
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (!containsHuntCompass(item)) {
                continue;
            }
            inventory.setItem(i, null);
        }
    }

    public boolean isHuntCompass(ItemStack item) {
        if (item == null || item.getType() != Material.COMPASS) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(huntCompassKey, PersistentDataType.STRING);
    }

    public boolean containsHuntCompass(ItemStack item) {
        if (isHuntCompass(item)) {
            return true;
        }
        if (item == null) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof BundleMeta bundleMeta) || !bundleMeta.hasItems()) {
            return false;
        }
        return bundleMeta.getItems().stream().anyMatch(this::containsHuntCompass);
    }

    private boolean giveHuntCompass(Player hunter, ItemStack compass) {
        return hunter.getInventory().addItem(compass).isEmpty();
    }

    private boolean consumeOne(Player player, ItemStack expected) {
        if (expected == null || expected.getType() == Material.AIR) {
            return false;
        }
        PlayerInventory inventory = player.getInventory();
        ItemStack expectedOne = oneItem(expected);
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getAmount() <= 0 || !oneItem(item).isSimilar(expectedOne)) {
                continue;
            }
            item.setAmount(item.getAmount() - 1);
            if (item.getAmount() <= 0) {
                inventory.setItem(i, null);
            } else {
                inventory.setItem(i, item);
            }
            return true;
        }
        return false;
    }

    private ItemStack oneItem(ItemStack item) {
        ItemStack copy = item.clone();
        copy.setAmount(1);
        return copy;
    }

    public String formatLocation(TrackingState state) {
        if (!state.hasLocation()) {
            return "unknown";
        }
        return state.worldName() + " " + state.x() + ", " + state.y() + ", " + state.z();
    }

    public Component trackingItemDisplayName(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return Component.text("None", NamedTextColor.RED);
        }
        return item.effectiveName()
            .colorIfAbsent(NamedTextColor.YELLOW)
            .hoverEvent(item.asHoverEvent(showItem -> showItem));
    }

    private String formatDuration(long millis) {
        long seconds = Math.max(0L, (millis + 999L) / 1_000L);
        if (seconds == 0) {
            return "0s";
        }
        if (seconds % 3_600L == 0) {
            return (seconds / 3_600L) + "h";
        }
        if (seconds % 60L == 0) {
            return (seconds / 60L) + "m";
        }
        return seconds + "s";
    }

    private String formatTimerDuration(long millis) {
        long totalSeconds = Math.max(0L, (millis + 999L) / 1_000L);
        long hours = totalSeconds / 3_600L;
        long minutes = (totalSeconds % 3_600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0) {
            return hours + "h " + minutes + "m " + seconds + "s";
        }
        if (minutes > 0) {
            return minutes + "m " + seconds + "s";
        }
        return seconds + "s";
    }

    public void giveOrDrop(Player player, ItemStack item) {
        player.getInventory().addItem(item).values().forEach(leftover ->
            player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }

    public record PlaceResult(boolean success, Component message) {
        public static PlaceResult success(Component message) {
            return new PlaceResult(true, message);
        }

        public static PlaceResult failure(Component message) {
            return new PlaceResult(false, message);
        }
    }

    public record ClaimResult(boolean success, Component message) {
        public static ClaimResult success(Component message) {
            return new ClaimResult(true, message);
        }

        public static ClaimResult failure(Component message) {
            return new ClaimResult(false, message);
        }
    }

    public enum ClearMode {
        DELETE,
        REFUND
    }

    public record ClearResult(int cleared, int refunded, List<String> unavailablePlacers) {
    }

    public record TrackingResult(boolean success, Component message) {
        public static TrackingResult success(Component message) {
            return new TrackingResult(true, message);
        }

        public static TrackingResult failure(Component message) {
            return new TrackingResult(false, message);
        }
    }

    public record HuntResult(boolean success, Component message) {
        public static HuntResult success(Component message) {
            return new HuntResult(true, message);
        }

        public static HuntResult failure(Component message) {
            return new HuntResult(false, message);
        }
    }
}
