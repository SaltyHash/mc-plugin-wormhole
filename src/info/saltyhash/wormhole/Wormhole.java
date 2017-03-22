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
        
        // Initiate database
        try {
            Class.forName("org.sqlite.JDBC");
            String path = getDataFolder().getAbsolutePath()+File.separator+"Wormhole.sqlite.db";
            this.db = DriverManager.getConnection("jdbc:sqlite:"+path);
            this.db.createStatement().execute("PRAGMA foreign_keys=ON");
        }
        catch (ClassNotFoundException | SQLException e) {
            // Failed to connect to database; can't run without it
            this.getLogger().severe("Could not connect to database; "+e.getLocalizedMessage());
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