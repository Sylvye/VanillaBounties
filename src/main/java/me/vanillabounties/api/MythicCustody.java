package me.vanillabounties.api;

import org.bukkit.NamespacedKey;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.UUID;

/** Optional main-thread integration for the authoritative owner of unique mythic items. */
public interface MythicCustody {
    NamespacedKey MYTHIC_TYPE = new NamespacedKey("mythicitems", "type");

    static boolean isMythic(ItemStack item) {
        return item != null && item.hasItemMeta()
            && item.getItemMeta().getPersistentDataContainer().has(MYTHIC_TYPE);
    }

    static boolean containsMythic(ItemStack item) {
        if (isMythic(item)) return true;
        if (item == null || !item.hasItemMeta()) return false;
        var meta = item.getItemMeta();
        if (meta instanceof BundleMeta bundle) return bundle.getItems().stream().anyMatch(MythicCustody::containsMythic);
        return meta instanceof BlockStateMeta block && block.hasBlockState()
            && block.getBlockState() instanceof ShulkerBox box
            && Arrays.stream(box.getInventory().getContents()).anyMatch(MythicCustody::containsMythic);
    }

    /** Persist a reservation for the held item, or reject it by returning null. */
    UUID reserve(Player placer, ItemStack item);

    boolean reserved(UUID token, ItemStack item);

    /** Reconcile a persisted placement or delivery outcome. Safe to retry. */
    void reconcile(UUID token);

    interface Ledger {
        Outcome outcome(UUID token) throws SQLException;
    }

    enum State { MISSING, ESCROWED, DELIVERED, DELETED }

    record Outcome(State state, UUID recipient) {}
}
