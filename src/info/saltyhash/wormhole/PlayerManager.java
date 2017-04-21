package info.saltyhash.wormhole;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.java.JavaPlugin;

/** Uses player metadata to store volatile information about the player. */
final class PlayerManager {
    private static JavaPlugin plugin;
    
    private PlayerManager() {}
    
    static void setup(JavaPlugin plugin) {
        PlayerManager.plugin = plugin;
    }
    
    /** Returns the object for key in given player (null if DNE). */
    private static Object getMetadata(Player player, String key) {
        for (MetadataValue value : player.getMetadata(key)) {
            if (value.getOwningPlugin().equals(plugin)) return value.value();
        }
        return null;
    }
    
    /** Sets the object for key in given player. */
    private static void setMetadata(Player player, String key, Object object) {
        player.setMetadata(key, new FixedMetadataValue(plugin, object));
    }
    
    private static final String previousLocationKey = "previousLocation";
    /** Returns the player's last jump record, or null if DNE. */
    static Location getPreviousLocation(Player player) {
        try {
            return (Location) getMetadata(player, previousLocationKey);
        }
        catch (ClassCastException e) {
            setMetadata(player, previousLocationKey, null);
            return null;
        }
    }
    
    /** Sets the player's last jump. */
    static void setPreviousLocation(Player player, Location location) {
        setMetadata(player, previousLocationKey, location);
    }
}