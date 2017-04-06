package info.saltyhash.wormhole.persistence;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.sql.*;
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
    
    /* <Database-related Fields and Methods> */
    
    private JumpRecord(ResultSet rs) throws SQLException {
        this.playerUUID = UUID.fromString(rs.getString("player_uuid"));
        this.name       = rs.getString("name");
        this.worldUUID  = UUID.fromString(rs.getString("world_uuid"));
        this.x          = rs.getDouble("x");
        this.y          = rs.getDouble("y");
        this.z          = rs.getDouble("z");
        this.yaw        = rs.getFloat("yaw");
    }
    
    private static PreparedStatement deleteStmt;
    private static PreparedStatement getDeleteStmt() {
        final String sql = "DELETE FROM jumps WHERE `id`=?;";
        
        PreparedStatement ps = JumpRecord.lazyCreatePreparedStatement(
                JumpRecord.deleteStmt, sql
        );
        return (JumpRecord.deleteStmt = ps);
    }
    
    private static PreparedStatement insertStmt;
    private static PreparedStatement getInsertStmt() {
        final String sql = "INSERT INTO jumps "+
                "(`player_uuid`,`name`,`world_uuid`,`x`,`y`,`z`,`yaw`) "+
                "VALUES (?,?,?,?,?,?,?);";
        
        PreparedStatement ps = JumpRecord.lazyCreatePreparedStatement(
                JumpRecord.insertStmt, sql
        );
        return (JumpRecord.insertStmt = ps);
    }
    
    private static PreparedStatement selectWithId;
    private static PreparedStatement getSelectWithId() {
        final String sql = "SELECT * FROM jumps WHERE `id`=? LIMIT 1;";
        
        PreparedStatement ps = JumpRecord.lazyCreatePreparedStatement(
                JumpRecord.selectWithId, sql
        );
        return (JumpRecord.selectWithId = ps);
    }
    
    private static PreparedStatement selectWithPlayerUuidAndNameStmt;
    private static PreparedStatement getSelectWithPlayerUuidAndNameStmt() {
        final String sql = "SELECT * FROM jumps WHERE `player_uuid`=? AND `name`=? LIMIT 1;";
        
        PreparedStatement ps = JumpRecord.lazyCreatePreparedStatement(
                JumpRecord.selectWithPlayerUuidAndNameStmt, sql
        );
        return (JumpRecord.selectWithPlayerUuidAndNameStmt = ps);
    }
    
    private static PreparedStatement updateStmt;
    private static PreparedStatement getUpdateStmt() {
        final String sql = "UPDATE players SET `player_uuid`=?,`name`=?,"+
                "`world_uuid`=?,`x`=?,`y`=?,`z`=?,`yaw`=? WHERE `id`=?;";
        
        PreparedStatement ps = JumpRecord.lazyCreatePreparedStatement(
                JumpRecord.updateStmt, sql
        );
        return (JumpRecord.updateStmt = ps);
    }
    
    /**
     * Takes a prepared statement and, if null or closed, creates and returns
     * a new prepared statement generated from the sql given.  If the given
     * prepared statement is not null and open, then its parameters are cleared
     * and it is returned.
     * @param  ps  Current prepared statement (may be null).
     * @param  sql SQL for the prepared statement if a new one must be created.
     * @return New or existing prepared statement; null on error.
     */
    private static PreparedStatement lazyCreatePreparedStatement(
            PreparedStatement ps, String sql) {
        assert (sql != null && sql.length() > 0);
        
        try {
            // A new statement needs to be created?
            if (ps == null || ps.isClosed()) {
                // Get a connection to the database
                Connection conn = DBManager.getConnection();
                if (conn == null) return null;
                
                // Create the statement
                return conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            }
            // The statement already exists?
            else {
                ps.clearParameters();
                return ps;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }
    
    /* </Database-related Fields and Methods> */
    
    /* <Persistence Methods> */
    
    /**
     * Deletes the jump record from the database.
     * WARNING: This will delete all signs associated with the jump!
     * @return true on success (even if record DNE); false on SQL error.
     */
    public boolean delete() {
        // Get the statement associated with this action
        PreparedStatement ps = JumpRecord.getDeleteStmt();
        if (ps == null) return false;
        
        try {
            // Set the statement parameters and execute the update
            ps.setInt(1, this.id);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Gets the jump record from the database.
     * @param  id ID of the jump.
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
        return false;
    }
    
    /* </Persistence Methods> */
}