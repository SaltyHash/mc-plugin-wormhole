package info.saltyhash.wormhole.persistence;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/** Represents a row in the database table 'jumps'. */
public class JumpRecord {
    public Integer id;          // Primary key
    public UUID    playerUUID;  // Foreign key to players.uuid
    public String  name;
    public UUID    worldUUID;
    public double  x, y, z;
    public float   yaw;
    
    public JumpRecord(UUID playerUUID, String name, UUID worldUUID,
                      double x, double y, double z, float yaw) {
        this.id = null;
        this.playerUUID = playerUUID;
        this.name       = name;
        this.worldUUID  = worldUUID;
        this.x = x; this.y = y; this.z = z; this.yaw = yaw;
    }
    
    public JumpRecord(Player player, String name) {
        this.id = null;
        this.playerUUID = player.getUniqueId();
        this.name       = name;
    
        Location l = player.getLocation();
        this.worldUUID = l.getWorld().getUID();
        this.x = l.getX(); this.y = l.getY(); this.z = l.getZ();
        this.yaw = l.getYaw();
    }
    
    /** Gets the jump record from the database.
     *
     * @param id ID of the jump.
     * @return Jump record or null if DNE or error.
     */
    public static JumpRecord load(int id) {
        Connection conn = DBManager.getConnection();
        if (conn == null) return null;
        
        // Try to get the jump data from the database
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT `player_uuid`,`name`,`world_uuid`,`x`,`y`,`z`,`yaw` "+
                "FROM jumps WHERE `id` = ? LIMIT 1;"
        )) {
            ps.setInt(1, id);
            ResultSet results = ps.executeQuery();
            
            // No matching row?
            if (!results.isBeforeFirst()) return null;
            
            // Return jump record from results
            UUID   playerUUID = UUID.fromString(results.getString("player_uuid"));
            String name       = results.getString("name");
            UUID   worldUUID  = UUID.fromString(results.getString("world_uuid"));
            double x          = results.getDouble("x");
            double y          = results.getDouble("y");
            double z          = results.getDouble("z");
            float  yaw        = results.getFloat("yaw");
            return new JumpRecord(playerUUID, name, worldUUID, x, y, z, yaw);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
        return null;
    }
    
    /** Saves the jump record to the database by inserting or updating.
     *
     * @return true on success, false on error.
     */
    public boolean save() {
        Connection conn = DBManager.getConnection();
        if (conn == null) return false;
        
        // Try updating if id is not null
        if (this.id != null) {
        
        }
        
        return false;
    }
}