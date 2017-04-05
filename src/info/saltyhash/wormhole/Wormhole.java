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
        String dataPath = this.getDataFolder().getAbsolutePath() + File.separator;
        
        // Save default config (doesn't overwrite)
        this.saveDefaultConfig();
        
        // Set up the database
        DBManager.logger = this.getLogger();
        DBManager.dbFile = new File(dataPath +"Wormhole.sqlite");
        if (!DBManager.migrate()) {
            this.disable();
            return;
        }
        
        // Set up Economy
        EconManager econMgr = new EconManager(this);
        
        // Register event handler and command handler
        this.getServer().getPluginManager().registerEvents(
            new WormholeEventHandler(this, econMgr), this);
        /*
        WormholeCommandHandler handler =
            new WormholeCommandHandler(this, econMgr);
        this.getCommand("wormhole").setExecutor(handler);
        */
        
        // Save logged in players to the database
        for (Player player : this.getServer().getOnlinePlayers()) {
            PlayerRecord pr = new PlayerRecord(player);
            if (!pr.save()) {
                this.getLogger().warning("Failed to save player '" +
                        player.getName() + "' to the database."
                );
            }
        }
        
        this.getLogger().info("Enabled");
    }
    
    @Override
    public void onDisable() {
        DBManager.closeConnection();
        this.getLogger().info("Disabled");
    }
    
    /** Disables the plugin; used in fatal error situations. */
    private void disable() {
        this.getServer().getPluginManager().disablePlugin(this);
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
    
    /*
    /** Sets up the database.  Only run during startup.
     *
     * @return true / false on success / failure.
     *
    private boolean setupDatabase() {
        // Check if we need to rename the DB file from "Wormhole.sqlite.db"
        // (versions <=1.3.4), to just "Wormhole.sqlite" (versions >= 1.4.0).
        // dataPath = "/path/to/plugins/Wormhole/"
        String dataPath = this.getDataFolder().getAbsolutePath()+File.separator;
        File oldDBFile  = new File(dataPath+"Wormhole.sqlite.db");
        File newDBFile  = new File(dataPath+"Wormhole.sqlite");
        // Old DB file exists and the new one does not?
        if (oldDBFile.exists() && !newDBFile.exists()) {
            // Rename the old DB file to the new filename
            if (oldDBFile.renameTo(newDBFile)) {
                // Successfully renamed DB file
                this.getLogger().info("Renamed SQLite database file from "
                        +"'Wormhole.sqlite.db' to 'Wormhole.sqlite'.");
            } else {
                // Failed to rename DB file; abort.
                this.getLogger().severe(
                        "Failed to rename SQLite database file!");
                return false;
            }
        }
        
        return true;
    }
    */
}