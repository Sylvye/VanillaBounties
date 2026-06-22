package me.vanillabounties;

import me.vanillabounties.model.BountyReward;
import me.vanillabounties.model.BountySummary;
import me.vanillabounties.model.AutoBountyFolder;
import me.vanillabounties.model.AutoBountyTemplate;
import me.vanillabounties.model.BountyVisibility;
import me.vanillabounties.model.KnownPlayer;
import me.vanillabounties.storage.BountyDatabase;
import me.vanillabounties.util.InventoryPlanner;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

public final class BountyService {
    private final Plugin plugin;
    private final BountyDatabase database;

    public BountyService(Plugin plugin, BountyDatabase database) {
        this.plugin = plugin;
        this.database = database;
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
        if (placer.getUniqueId().equals(target.uuid())) {
            return PlaceResult.failure(Component.text("You cannot place a bounty on yourself.", NamedTextColor.RED));
        }

        ItemStack hand = placer.getInventory().getItemInMainHand();
        if (hand.getType() == Material.AIR || hand.getAmount() <= 0) {
            return PlaceResult.failure(Component.text("Hold the item stack you want to place as a bounty.", NamedTextColor.RED));
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
                .append(Component.text(escrowed.getAmount() + "x " + escrowed.getType().name(), NamedTextColor.YELLOW))
                .append(Component.text(" on " + target.name() + ".", NamedTextColor.GREEN)));
        } catch (SQLException exception) {
            giveOrDrop(placer, escrowed);
            plugin.getLogger().log(Level.SEVERE, "Failed to persist bounty; item returned to " + placer.getName(), exception);
            return PlaceResult.failure(Component.text("The bounty could not be saved. Your item was returned.", NamedTextColor.RED));
        }
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

        try {
            BountyDatabase.AutoBountyApplyResult result = database.applyAutomaticBountiesForKill(
                victim.getUniqueId(),
                victim.getName(),
                killer.getUniqueId(),
                killer.getName(),
                System.currentTimeMillis()
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

    public List<BountySummary> listBoard(UUID viewerUuid) throws SQLException {
        return database.listBoardSummaries(viewerUuid);
    }

    public List<BountyReward> listRewards(UUID targetUuid, UUID viewerUuid) throws SQLException {
        return database.listVisibleRewards(targetUuid, viewerUuid);
    }

    public int clearAllActiveBounties() throws SQLException {
        return database.clearActiveBounties();
    }

    public int clearActiveBounties(UUID targetUuid) throws SQLException {
        return database.clearActiveBounties(targetUuid);
    }

    public boolean autoBountiesEnabled() throws SQLException {
        return database.autoBountiesEnabled();
    }

    public void setAutoBountiesEnabled(boolean enabled) throws SQLException {
        database.setAutoBountiesEnabled(enabled);
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
}
