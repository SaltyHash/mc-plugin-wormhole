package info.saltyhash.wormhole.persistence;

import org.bukkit.block.Sign;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/** Represents a row in the database table 'signs'. */
public class SignRecord {
    public UUID worldUuid;  // Primary key
    public int  x;          // Primary key
    public int  y;          // Primary key
    public int  z;          // Primary key
    public int  jumpId;     // References column jumps.id
    
    public SignRecord(UUID worldUuid, int x, int y, int z, int jumpId) {
        this.worldUuid = worldUuid;
        this.x         = x;
        this.y         = y;
        this.z         = z;
        this.jumpId    = jumpId;
    }
    
    public SignRecord(Sign sign, int jumpId) {
        this(sign.getWorld().getUID(), sign.getX(), sign.getY(), sign.getZ(), jumpId);
    }
    
    private SignRecord(ResultSet rs) throws SQLException {
        this.worldUuid = UUID.fromString(rs.getString("world_uuid"));
        this.x         = rs.getInt("x");
        this.y         = rs.getInt("y");
        this.z         = rs.getInt("z");
        this.jumpId    = rs.getInt("jump_id");
    }
    
    public JumpRecord getJumpRecord() {
        return JumpRecord.load(jumpId);
    }
    
    /**
     * Deletes the sign record from the database.  Logs errors.
     * @return true on success (even if record DNE); false on SQL error.
     */
    public boolean delete() {
        // Get database connection
        Connection conn = DBManager.getConnection();
        if (conn == null) return false;
        
        // Create delete statement
        final String sql = "DELETE FROM signs WHERE `world_uuid`=? AND `x`=? AND `y`=? AND `z`=?;";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            // Set statement parameters and execute
            ps.setString(1, worldUuid.toString());
            ps.setInt(2, x);
            ps.setInt(3, y);
            ps.setInt(4, z);
            ps.executeUpdate();
        } catch (SQLException e) {
            DBManager.logSevere("Failed to delete sign record:\n"+e.toString());
            return false;
        }
        return true;
    }
    
    /**
     * Gets the sign record from the database.  Logs errors.
     * @return Sign record or null if DNE or error.
     */
    public static SignRecord load(UUID worldUuid, int x, int y, int z) {
        // Get database connection
        Connection conn = DBManager.getConnection();
        if (conn == null) return null;
        
        // Create statement
        final String sql = "SELECT * FROM signs WHERE "+
                "`world_uuid`=? AND `x`=? AND `y`=? AND `z`=? LIMIT 1;";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            // Set statement parameters and execute
            ps.setString(1, worldUuid.toString());
            ps.setInt(2, x);
            ps.setInt(3, y);
            ps.setInt(4, z);
            ResultSet rs = ps.executeQuery();
            
            // Return a new sign record or null if there are no results
            return rs.next() ? new SignRecord(rs) : null;
        } catch (SQLException e) {
            DBManager.logSevere("Failed to load sign record:\n"+e.toString());
            return null;
        }
    }
    
    public static SignRecord load(Sign sign) {
        return load(sign.getWorld().getUID(), sign.getX(), sign.getY(), sign.getZ());
    }
    
    /**
     * Saves the sign record to the database.  Logs errors.
     * @return true on success; false on error.
     */
    public boolean save() {
        // Get database connection
        Connection conn = DBManager.getConnection();
        if (conn == null) return false;
        
        // Create update statement
        final String updateSql = "UPDATE signs SET `jump_id`=? "+
                "WHERE `world_uuid`=? AND `x`=? AND `y`=? AND `z`=?;";
        try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
            // Set statement parameters and execute, returning on success
            ps.setInt(1, jumpId);
            ps.setString(2, worldUuid.toString());
            ps.setInt(3, x);
            ps.setInt(4, y);
            ps.setInt(5, z);
            if (ps.executeUpdate() > 0) return true;
        } catch (SQLException e) {
            DBManager.logSevere("Failed to save sign record via update:\n"+e.toString());
            return false;
        }
        
        // At this point, UPDATE has failed, so try INSERT:
        // Create insert statement
        final String insertSql = "INSERT INTO signs "+
                "(`world_uuid`,`x`,`y`,`z`,`jump_id`) VALUES (?,?,?,?,?);";
        try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
            // Set statement parameters and execute, returning success or failure
            ps.setString(1, worldUuid.toString());
            ps.setInt(2, x);
            ps.setInt(3, y);
            ps.setInt(4, z);
            ps.setInt(5, jumpId);
            return (ps.executeUpdate() > 0);
        } catch (SQLException e) {
            DBManager.logSevere("Failed to save sign record via insert:\n"+e.toString());
            return false;
        }
    }
}