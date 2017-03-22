package info.saltyhash.wormhole;

import java.util.List;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;

/** Uses player metadata to store volatile information about the player. */
class PlayerManager {
    private final Wormhole wormhole;
    
    PlayerManager(Wormhole wormhole) {
        this.wormhole = wormhole;
    }
    
    private Object getMetadata(Player player, String key) {
        /* Returns the object for key in given player (null if DNE). */
        List<MetadataValue> values = player.getMetadata(key);
        for (MetadataValue value : values) {
            if (value.getOwningPlugin().getDescription().getName().equals(
                this.wormhole.getDescription().getName())) {
                return value.value();
            }
        }
        return null;
    }
    
    /** Sets the object for key in given player. */
    private void setMetadata(Player player, String key, Object object) {
        player.setMetadata(key, new FixedMetadataValue(this.wormhole, object));
    }
    
    /** @return The Player's last Jump (null if DNE). */
    Jump getLastJump(Player player) {
        try {
            return (Jump)this.getMetadata(player, "last");
        }
        catch (ClassCastException e) {
            this.setMetadata(player, "last", null);
            return null;
        }
    }

    /** Sets the player's last Jump. */
    void setLastJump(Player player, Jump jump) {
        this.setMetadata(player, "last", jump);
    }
}