package me.vanillabounties.command;

import me.vanillabounties.BountyService;
import me.vanillabounties.gui.BountyGui;
import me.vanillabounties.storage.BountyDatabase;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BountiesCommandTest {
    @TempDir
    Path tempDir;

    @BeforeEach
    void setUpBukkit() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDownBukkit() {
        MockBukkit.unmock();
    }

    @Test
    void menuOpenCooldownBlocksSpamAndAllowsAfterThreeSeconds() throws Exception {
        try (BountyDatabase database = new BountyDatabase(tempDir.resolve("bounties.db"))) {
            database.migrate();
            var plugin = MockBukkit.createMockPlugin();
            BountyService service = new BountyService(plugin, database);
            BountiesCommand command = new BountiesCommand(new BountyGui(service), null, () -> now);
            PlayerMock player = MockBukkit.getMock().addPlayer("Player");
            player.addAttachment(plugin, "vanillabounties.use", true);
            player.getInventory().addItem(new ItemStack(Material.DIAMOND, 1));

            now = 1_000L;
            assertTrue(command.onCommand(player, null, "bs", new String[0]));
            drainMessages(player);

            now = 1_500L;
            assertTrue(command.onCommand(player, null, "bs", new String[0]));
            assertTrue(drainMessages(player).stream().anyMatch(message -> message.contains("before opening the bounty menu again")));

            now = 4_000L;
            assertTrue(command.onCommand(player, null, "bs", new String[] {"menu"}));
            assertTrue(drainMessages(player).stream().noneMatch(message -> message.contains("before opening the bounty menu again")));
        }
    }

    private long now;

    private List<String> drainMessages(PlayerMock player) {
        PlainTextComponentSerializer serializer = PlainTextComponentSerializer.plainText();
        List<String> messages = new ArrayList<>();
        while (true) {
            try {
                Component message = player.nextComponentMessage();
                if (message == null) {
                    return messages;
                }
                messages.add(serializer.serialize(message));
            } catch (AssertionError exception) {
                return messages;
            }
        }
    }
}
