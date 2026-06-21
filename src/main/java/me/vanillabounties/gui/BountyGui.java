package me.vanillabounties.gui;

import me.vanillabounties.BountyService;
import me.vanillabounties.model.BountyReward;
import me.vanillabounties.model.BountySummary;
import me.vanillabounties.model.RewardState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public final class BountyGui {
    private static final int INVENTORY_SIZE = 54;
    private static final int PAGE_SIZE = 45;
    private static final int PREVIOUS_SLOT = 45;
    private static final int BACK_SLOT = 45;
    private static final int CLAIM_SLOT = 49;
    private static final int NEXT_SLOT = 53;

    private final BountyService bountyService;

    public BountyGui(BountyService bountyService) {
        this.bountyService = bountyService;
    }

    public void openBoard(Player viewer, int page) {
        List<BountySummary> summaries;
        try {
            summaries = bountyService.listBoard(viewer.getUniqueId());
        } catch (SQLException exception) {
            viewer.sendMessage(Component.text("Could not load bounties.", NamedTextColor.RED));
            Bukkit.getLogger().log(Level.SEVERE, "Failed to load bounty board", exception);
            return;
        }

        int maxPage = Math.max(0, (summaries.size() - 1) / PAGE_SIZE);
        int clampedPage = Math.max(0, Math.min(page, maxPage));
        BountyMenuHolder holder = new BountyMenuHolder(BountyMenuHolder.Type.BOARD, viewer.getUniqueId(), clampedPage, null, "");
        Inventory inventory = Bukkit.createInventory(holder, INVENTORY_SIZE, Component.text("Bounties", NamedTextColor.GOLD));
        holder.setInventory(inventory);

        int start = clampedPage * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, summaries.size());
        for (int index = start; index < end; index++) {
            int slot = index - start;
            BountySummary summary = summaries.get(index);
            inventory.setItem(slot, createSummaryItem(summary));
            holder.targetSlots().put(slot, summary.targetUuid());
        }

        if (summaries.isEmpty()) {
            inventory.setItem(22, namedItem(Material.BARRIER, Component.text("No active bounties", NamedTextColor.GRAY),
                List.of(Component.text("Place one with /bounty <player>.", NamedTextColor.DARK_GRAY))));
        }
        if (clampedPage > 0) {
            inventory.setItem(PREVIOUS_SLOT, namedItem(Material.ARROW, Component.text("Previous Page", NamedTextColor.YELLOW), List.of()));
        }
        if (clampedPage < maxPage) {
            inventory.setItem(NEXT_SLOT, namedItem(Material.ARROW, Component.text("Next Page", NamedTextColor.YELLOW), List.of()));
        }

        viewer.openInventory(inventory);
    }

    public void openDetail(Player viewer, UUID targetUuid, String targetName, int page) {
        List<BountyReward> rewards;
        try {
            rewards = bountyService.listRewards(targetUuid, viewer.getUniqueId());
        } catch (SQLException exception) {
            viewer.sendMessage(Component.text("Could not load that bounty.", NamedTextColor.RED));
            Bukkit.getLogger().log(Level.SEVERE, "Failed to load bounty detail", exception);
            return;
        }
        List<GroupedRewardPreview> previews = groupRewards(rewards);

        int maxPage = Math.max(0, (previews.size() - 1) / PAGE_SIZE);
        int clampedPage = Math.max(0, Math.min(page, maxPage));
        BountyMenuHolder holder = new BountyMenuHolder(BountyMenuHolder.Type.DETAIL, viewer.getUniqueId(), clampedPage, targetUuid, targetName);
        Inventory inventory = Bukkit.createInventory(holder, INVENTORY_SIZE, Component.text("Bounty: " + targetName, NamedTextColor.GOLD));
        holder.setInventory(inventory);

        int start = clampedPage * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, previews.size());
        for (int index = start; index < end; index++) {
            inventory.setItem(index - start, createRewardPreview(previews.get(index)));
        }

        inventory.setItem(BACK_SLOT, namedItem(Material.ARROW, Component.text("Back", NamedTextColor.YELLOW), List.of()));
        if (rewards.stream().anyMatch(reward -> reward.state() == RewardState.CLAIMABLE)) {
            inventory.setItem(CLAIM_SLOT, namedItem(Material.CHEST, Component.text("Claim All", NamedTextColor.GREEN),
                List.of(Component.text("Claims only if every reward fits.", NamedTextColor.GRAY))));
        }
        if (clampedPage < maxPage) {
            inventory.setItem(NEXT_SLOT, namedItem(Material.ARROW, Component.text("Next Page", NamedTextColor.YELLOW), List.of()));
        }

        viewer.openInventory(inventory);
    }

    public void handleClick(Player viewer, BountyMenuHolder holder, int rawSlot) {
        if (!viewer.getUniqueId().equals(holder.viewerUuid())) {
            viewer.closeInventory();
            return;
        }

        if (holder.type() == BountyMenuHolder.Type.BOARD) {
            handleBoardClick(viewer, holder, rawSlot);
            return;
        }

        handleDetailClick(viewer, holder, rawSlot);
    }

    private void handleBoardClick(Player viewer, BountyMenuHolder holder, int rawSlot) {
        if (rawSlot == PREVIOUS_SLOT && holder.page() > 0) {
            openBoard(viewer, holder.page() - 1);
            return;
        }
        if (rawSlot == NEXT_SLOT) {
            openBoard(viewer, holder.page() + 1);
            return;
        }

        UUID targetUuid = holder.targetSlots().get(rawSlot);
        if (targetUuid == null) {
            return;
        }

        String targetName = "Unknown";
        try {
            for (BountySummary summary : bountyService.listBoard(viewer.getUniqueId())) {
                if (summary.targetUuid().equals(targetUuid)) {
                    targetName = summary.targetName();
                    break;
                }
            }
        } catch (SQLException exception) {
            viewer.sendMessage(Component.text("Could not refresh that bounty.", NamedTextColor.RED));
            return;
        }
        openDetail(viewer, targetUuid, targetName, 0);
    }

    private void handleDetailClick(Player viewer, BountyMenuHolder holder, int rawSlot) {
        UUID targetUuid = holder.targetUuid();
        if (targetUuid == null) {
            return;
        }

        if (rawSlot == BACK_SLOT) {
            openBoard(viewer, 0);
            return;
        }
        if (rawSlot == NEXT_SLOT) {
            openDetail(viewer, targetUuid, holder.targetName(), holder.page() + 1);
            return;
        }
        if (rawSlot == CLAIM_SLOT) {
            BountyService.ClaimResult result = bountyService.claimRewards(viewer, targetUuid);
            viewer.sendMessage(result.message());
            openDetail(viewer, targetUuid, holder.targetName(), holder.page());
        }
    }

    private ItemStack createSummaryItem(BountySummary summary) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof SkullMeta skullMeta) {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(summary.targetUuid());
            skullMeta.setOwningPlayer(offlinePlayer);
            meta = skullMeta;
        }

        meta.displayName(Component.text(summary.targetName(), summary.hasClaimableRewards() ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Active rewards: " + summary.activeRewardCount(), NamedTextColor.GRAY));
        lore.add(Component.text("Kill streak: " + summary.killStreak(), NamedTextColor.GRAY));
        if (summary.hasClaimableRewards()) {
            lore.add(Component.text("Claimable by you: " + summary.claimableRewardCount(), NamedTextColor.GREEN));
        }
        lore.add(Component.text("Click to view rewards.", NamedTextColor.DARK_GRAY));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private List<GroupedRewardPreview> groupRewards(List<BountyReward> rewards) {
        List<GroupedRewardPreview> previews = new ArrayList<>();
        for (BountyReward reward : rewards) {
            GroupedRewardPreview match = null;
            for (GroupedRewardPreview preview : previews) {
                if (preview.canMerge(reward)) {
                    match = preview;
                    break;
                }
            }
            if (match == null) {
                previews.add(GroupedRewardPreview.from(reward));
            } else {
                match.merge(reward);
            }
        }
        return previews;
    }

    private ItemStack createRewardPreview(GroupedRewardPreview reward) {
        ItemStack item = reward.displayItem();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.add(Component.empty());
        lore.add(Component.text("Placed by: " + reward.placerDisplay(), NamedTextColor.GRAY));
        if (reward.rewardCount() > 1 || reward.totalAmount() != item.getAmount()) {
            lore.add(Component.text("Total: " + reward.totalAmount() + " across " + reward.rewardCount() + " reward stack(s)", NamedTextColor.GRAY));
        }
        if (reward.state() == RewardState.CLAIMABLE) {
            lore.add(Component.text("Claimable by you", NamedTextColor.GREEN));
        } else {
            lore.add(Component.text("Active bounty reward", NamedTextColor.YELLOW));
        }
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack namedItem(Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name);
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }
}
