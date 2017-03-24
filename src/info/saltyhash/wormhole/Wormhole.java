package info.saltyhash.wormhole;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

/** Wormhole plugin. */
public class Wormhole extends JavaPlugin {
    private Connection db;
    
    @Override
    public void onDisable() {
        // Close database
        try {
            if (this.db != null) this.db.close();
        }
        catch (SQLException e) {
            // Failed to close database, whatever
            this.getLogger().warning("Failed to close database; "+e.getLocalizedMessage());
        }
        this.getLogger().info("Disabled");
    }
    
    @Override
    public void onEnable() {
        // Save default config (doesn't overwrite)
        this.saveDefaultConfig();
        
        /* Check if we need to rename the DB file from "Wormhole.sqlite.db"
        /* of v1.3.4, to just "Wormhole.sqlite". */
        // dataPath = "/path/to/plugins/Wormhole/".
        String dataPath  = this.getDataFolder().getAbsolutePath()+File.separator;
        File oldDBFile = new File(dataPath+"Wormhole.sqlite.db");
        File newDBFile = new File(dataPath+"Wormhole.sqlite");
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
                this.getServer().getPluginManager().disablePlugin(this);
                return;
            }
        }
        
        // Connect to database
        try {
            Class.forName("org.sqlite.JDBC");
            this.db = DriverManager.getConnection(
                    "jdbc:sqlite:"+newDBFile.getAbsolutePath());
            this.db.createStatement().execute("PRAGMA foreign_keys=ON");
        }
        catch (ClassNotFoundException | SQLException e) {
            // Failed to connect to database; abort.
            this.getLogger().severe(
                    "Could not connect to database; "+e.getLocalizedMessage());
            this.getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Set up Economy, Jump, Player, and Sign Managers
        EconManager   econMgr   = new EconManager(this);
        JumpManager   jumpMgr   = new JumpManager(this.db, this.getLogger());
        PlayerManager playerMgr = new PlayerManager(this);
        SignManager   signMgr   = new SignManager(this.db, jumpMgr, this.getLogger());
        
        // Register event handler and command handler
        this.getServer().getPluginManager().registerEvents(
            new WormholeEventHandler(this, econMgr, playerMgr, signMgr), this);
        WormholeCommandHandler handler =
            new WormholeCommandHandler(this, econMgr, jumpMgr, playerMgr, signMgr);
        this.getCommand("wormhole").setExecutor(handler);

        this.getLogger().info("Enabled");
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