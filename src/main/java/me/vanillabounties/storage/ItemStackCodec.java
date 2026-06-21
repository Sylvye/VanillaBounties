package me.vanillabounties.storage;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class ItemStackCodec {
    private ItemStackCodec() {
    }

    public static byte[] encode(ItemStack itemStack) throws IOException {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             BukkitObjectOutputStream output = new BukkitObjectOutputStream(bytes)) {
            output.writeObject(itemStack);
            return bytes.toByteArray();
        }
    }

    public static ItemStack decode(byte[] bytes) throws IOException, ClassNotFoundException {
        try (BukkitObjectInputStream input = new BukkitObjectInputStream(new ByteArrayInputStream(bytes))) {
            Object object = input.readObject();
            if (!(object instanceof ItemStack itemStack)) {
                throw new IOException("Decoded object is not an ItemStack");
            }
            return itemStack;
        }
    }
}
