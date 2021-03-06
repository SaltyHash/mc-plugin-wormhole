package info.saltyhash.wormhole;

import java.io.File;

import info.saltyhash.wormhole.persistence.DBManager;
import info.saltyhash.wormhole.persistence.PlayerRecord;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Wormhole plugin. */
public class Wormhole extends JavaPlugin {
    @Override
    public void onEnable() {
        // Get the path of the data folder: "/path/to/plugins/Wormhole/"
        final String dataPath = getDataFolder().getAbsolutePath() + File.separator;
        
        // Save default config (doesn't overwrite)
        saveDefaultConfig();
        
        // Set up the database
        DBManager.setup(new File(dataPath+"Wormhole.sqlite"), getLogger());
        if (!DBManager.migrate()) {
            disable();
            return;
        }
        // Save logged in players to the database
        for (Player player : getServer().getOnlinePlayers()) {
            PlayerRecord pr = PlayerRecord.load(player.getUniqueId());
            // Player record exists?
            if (pr != null) {
                // Player usernames do not match (player changed their username)?
                if (!pr.username.equals(player.getName())) {
                    // Update username and save
                    pr.username = player.getName();
                    if (!pr.save()) {
                        getLogger().warning("Failed to save player '" + player.getName() +
                                "' to the database");
                    }
                }
            }
            // Player record does not exist?
            else {
                // Create new player record and save
                pr = new PlayerRecord(player);
                if (!pr.save()) {
                    getLogger().warning("Failed to save player '" + player.getName() +
                            "' to the database");
                }
            }
        }
        
        // Set up PlayerManager and Economy
        PlayerManager.setup(this);
        EconManager.setup(this);
        
        // Register event handler, command handler, and tab completer
        getServer().getPluginManager().registerEvents(new WormholeEventHandler(this), this);
        getCommand("wormhole").setExecutor(new WormholeCommandHandler(this));
        getCommand("wormhole").setTabCompleter(new WormholeTabCompleter());
        
        getLogger().info("Enabled");
    }
    
    @Override
    public void onDisable() {
        DBManager.closeConnection();
        getLogger().info("Disabled");
    }
    
    /** Disables the plugin; used in fatal error situations. */
    private void disable() {
        getServer().getPluginManager().disablePlugin(this);
    }
    
    /** If the world is blacklisted and the player does not have the permission
     * "ignore_world_blacklist", then the player is notified, and the function returns true.
     * @return true if world is blacklisted for the player; false otherwise.
     */
    boolean notifyPlayerIfWorldIsBlacklisted(
            Player player, String worldName) {
        if (worldIsBlacklisted(worldName) &&
                !player.hasPermission("wormhole.ignore_world_blacklist")) {
            player.sendMessage("Sorry, Wormhole is disabled for world '" + worldName + "'");
            return true;
        }
        return false;
    }
    
    void playTeleportEffect(Location location) {
        /* Plays the teleport effect at the given location. */
        World world = location.getWorld();
        
        // Play sound effect
        if (getConfig().getBoolean("effects.sound")) {
            world.playSound(location, Sound.ENTITY_ENDERMEN_TELEPORT, 1.0f, 1.0f);
        }
        
        // Play smoke effect
        // Directions:  0:SE  1:S  2:SW  3:E  4:Up  5:W  6:NE  7:N  8:NW
        if (getConfig().getBoolean("effects.smoke")) {
            for (int x = 0; x < 8; x++) {
                world.playEffect(location, Effect.SMOKE, x);
            }
        }
        
        // Play Ender Signal effect
        if (getConfig().getBoolean("effects.ring")) {
            Location ringLocation = location.clone();
            ringLocation.setY(ringLocation.getY()+1.0);
            for (int x = 0; x < 2; x++) {
                world.playEffect(ringLocation, Effect.ENDER_SIGNAL, 0);
            }
        }
    }
    
    @SuppressWarnings("WeakerAccess")
    boolean worldIsBlacklisted(String worldName) {
        return getConfig().getStringList("world_blacklist").contains(worldName);
    }
}