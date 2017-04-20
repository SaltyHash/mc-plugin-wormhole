package info.saltyhash.wormhole.persistence;

import org.bukkit.block.Sign;

import java.sql.*;
import java.util.UUID;

/** Represents a row in the database table 'signs'. */
@SuppressWarnings("WeakerAccess")
public class SignRecord {
    private Integer id;     // Primary key
    public  UUID worldUuid; // Unique with x, y, and z
    public  int  x;         // Unique with world_uuid, y, and z
    public  int  y;         // Unique with world_uuid, x, and z
    public  int  z;         // Unique with world_uuid, x, and y
    public  int  jumpId;    // References column jumps.id
    
    public SignRecord(UUID worldUuid, int x, int y, int z, int jumpId) {
        this.id        = null;
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
        this.id        = rs.getInt("id");
        this.worldUuid = DBManager.BytesToUuid(rs.getBytes("world_uuid"));
        this.x         = rs.getInt("x");
        this.y         = rs.getInt("y");
        this.z         = rs.getInt("z");
        this.jumpId    = rs.getInt("jump_id");
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
        final String sql = "DELETE FROM signs WHERE `id`=?;";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            // Set statement parameters and execute
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            DBManager.logSevere("Failed to delete sign record:\n"+e.toString());
            return false;
        }
        return true;
    }
    
    public JumpRecord getJumpRecord() {
        return JumpRecord.loadWithId(jumpId);
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
            ps.setBytes(1, DBManager.UuidToBytes(worldUuid));
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
        
        // ID != null ==> the record already exists in the database ==> do UPDATE
        if (id != null) {
            // Create update statement
            final String sql = "UPDATE signs SET `jump_id`=?,`world_uuid`=?,`x`=?,`y`=?,`z`=? "+
                    "WHERE `id`=?;";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                // Set statement parameters and execute, returning success
                ps.setInt(1, jumpId);
                ps.setBytes(2, DBManager.UuidToBytes(worldUuid));
                ps.setInt(3, x);
                ps.setInt(4, y);
                ps.setInt(5, z);
                ps.setInt(6, id);
                return (ps.executeUpdate() > 0);
            } catch (SQLException e) {
                DBManager.logSevere("Failed to save sign record via update:\n"+e.toString());
                return false;
            }
        }
        // ID == null ==> the record does not exist in the database ==> do INSERT
        else {
            // Create insert statement
            final String sql = "INSERT INTO signs "+
                    "(`world_uuid`,`x`,`y`,`z`,`jump_id`) VALUES (?,?,?,?,?);";
            try (PreparedStatement ps = conn.prepareStatement(
                    sql, Statement.RETURN_GENERATED_KEYS)) {
                // Set statement parameters and execute, returning success or failure
                ps.setBytes(1, DBManager.UuidToBytes(worldUuid));
                ps.setInt(2, x);
                ps.setInt(3, y);
                ps.setInt(4, z);
                ps.setInt(5, jumpId);
                
                // Execute statement, throwing exception if failed
                if (ps.executeUpdate() == 0) throw new SQLException("Failed to insert");
                
                // Set id to the generated key
                ResultSet rs = ps.getGeneratedKeys();
                if (rs.next()) {
                    id = rs.getInt(1);
                    return true;
                } else {
                    throw new SQLException("Failed to retrieve generated key");
                }
            } catch (SQLException e) {
                DBManager.logSevere("Failed to save sign record via insert:\n"+e.toString());
                return false;
            }
        }
    }
}