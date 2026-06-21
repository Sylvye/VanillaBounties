package me.vanillabounties;

import me.vanillabounties.command.BountiesCommand;
import me.vanillabounties.command.AutoBountiesCommand;
import me.vanillabounties.command.BountyCommand;
import me.vanillabounties.gui.AutoBountyGui;
import me.vanillabounties.gui.BountyGui;
import me.vanillabounties.listener.BountyListener;
import me.vanillabounties.storage.BountyDatabase;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.Objects;

public final class VanillaBountiesPlugin extends JavaPlugin {
    private BountyDatabase database;
    private BountyService bountyService;
    private BountyGui bountyGui;
    private AutoBountyGui autoBountyGui;

    @Override
    public void onEnable() {
        try {
            getDataFolder().mkdirs();
            database = new BountyDatabase(getDataFolder().toPath().resolve("bounties.db"));
            database.migrate();
        } catch (SQLException exception) {
            getLogger().severe("Failed to initialize bounty database: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        bountyService = new BountyService(this, database);
        bountyGui = new BountyGui(bountyService);
        autoBountyGui = new AutoBountyGui(bountyService);

        BountyCommand bountyCommand = new BountyCommand(bountyService);
        PluginCommand bounty = Objects.requireNonNull(getCommand("bounty"), "bounty command missing from plugin.yml");
        bounty.setExecutor(bountyCommand);
        bounty.setTabCompleter(bountyCommand);

        Objects.requireNonNull(getCommand("bounties"), "bounties command missing from plugin.yml")
            .setExecutor(new BountiesCommand(bountyGui));

        AutoBountiesCommand autoBountiesCommand = new AutoBountiesCommand(bountyService, autoBountyGui);
        PluginCommand autoBounties = Objects.requireNonNull(getCommand("autobounties"), "autobounties command missing from plugin.yml");
        autoBounties.setExecutor(autoBountiesCommand);
        autoBounties.setTabCompleter(autoBountiesCommand);

        getServer().getPluginManager().registerEvents(new BountyListener(bountyService, bountyGui, autoBountyGui), this);
    }

    @Override
    public void onDisable() {
        if (database != null) {
            try {
                database.close();
            } catch (SQLException exception) {
                getLogger().warning("Failed to close bounty database: " + exception.getMessage());
            }
        }
    }
}
