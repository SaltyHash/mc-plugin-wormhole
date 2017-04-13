package info.saltyhash.wormhole;

import java.io.File;

import info.saltyhash.wormhole.persistence.DBManager;
import info.saltyhash.wormhole.persistence.PlayerRecord;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
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
        
        // Save public player record
        PlayerRecord publicPlayerRecord = new PlayerRecord(PlayerRecord.PUBLIC_UUID, null);
        if (!publicPlayerRecord.save()) {
            getLogger().severe("Failed to save public player record to the database");
            disable();
            return;
        }
        // Save logged in players to the database
        for (Player player : getServer().getOnlinePlayers()) {
            PlayerRecord pr = new PlayerRecord(player);
            if (!pr.save()) {
                getLogger().warning("Failed to save player '"+player.getName()+"' to the database");
            }
        }
        
        // Set up PlayerManager
        PlayerManager.setup(this);
        
        // Set up Economy
        EconManager econMgr = new EconManager(this);
        
        // Register event handler and command handler
        getServer().getPluginManager().registerEvents(
                new WormholeEventHandler(this, econMgr), this);
        getCommand("wormhole").setExecutor(new WormholeCommandHandler(this, econMgr));
        
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
}