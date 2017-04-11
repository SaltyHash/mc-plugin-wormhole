package info.saltyhash.wormhole.persistence;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents a row in the database table 'jumps'.
 * Setting playerUuid to null makes the jump public.
 */
public class JumpRecord {
    public Integer id;          // Primary key
    public UUID    playerUuid;  // Foreign key to players.uuid. Unique with name.
    public String  name;
    public UUID    worldUuid;
    public double  x, y, z;
    public float   yaw;
    
    private static final UUID SqlPublicUuid = new UUID(0, 0);
    
    public JumpRecord() {}
    
    public JumpRecord(UUID playerUuid, String name, Location l) {
        this(playerUuid, name, l.getWorld().getUID(), l.getX(), l.getY(), l.getZ(), l.getYaw());
    }
    
    public JumpRecord(UUID playerUuid, String name, UUID worldUuid,
                      double x, double y, double z, float yaw) {
        this.id = null;
        this.playerUuid = playerUuid;
        this.name       = name;
        this.worldUuid  = worldUuid;
        this.x = x; this.y = y; this.z = z; this.yaw = yaw;
    }
    
    /** Constructs a jump record from a ResultSet containing all columns of the table. */
    private JumpRecord(ResultSet rs) throws SQLException {
        this.id         = rs.getInt("id");
        this.playerUuid = UUID.fromString(rs.getString("player_uuid"));
        this.name       = rs.getString("name");
        this.worldUuid  = UUID.fromString(rs.getString("world_uuid"));
        this.x          = rs.getDouble("x");
        this.y          = rs.getDouble("y");
        this.z          = rs.getDouble("z");
        this.yaw        = rs.getFloat("yaw");
    }
    
    /**
     * Deletes the jump record from the database.  Logs errors.
     * WARNING: This will delete all signs associated with the jump!
     * @return true on success (even if record DNE); false on SQL error.
     */
    public boolean delete() {
        // Cannot delete a jump record with no id
        if (id == null) return true;
        
        // Get database connection
        Connection conn = DBManager.getConnection();
        if (conn == null) return false;
        
        // Create delete statement
        final String sql = "DELETE FROM jumps WHERE `id`=?;";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            // Set statement parameters and execute
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            DBManager.logSevere("Failed to delete jump record:\n"+e.toString());
            return false;
        }
        return true;
    }
    
    /** Returns a general description of the jump. */
    public String getDescription() {
        return getDescription(null, getPlayerRecord().username, name);
    }
    
    /**
     * Returns a description of the jump formatted for the given player.
     * @param player Player for whom to format the description.
     */
    public String getDescription(Player player) {
        return getDescription(player, getPlayerRecord().username, name);
    }
    
    /**
     * Returns the description of a jump with the given parameters formatted for the given player.
     * @param player    Player for whom to format the description (may be null).
     * @param ownerName Username of player who owns the jump (null if public).
     * @param jumpName  Name of the jump.
     */
    public static String getDescription(Player player, String ownerName, String jumpName) {
        // Jump belongs to the player?
        if (player != null && player.getName().equalsIgnoreCase(ownerName))
            return "'"+jumpName+"'";
        
        // Jump is public or belongs to another player?
        return String.format("'%s' (%s)", jumpName, (ownerName != null ? ownerName : "public"));
    }
    
    /** Returns the jump location. */
    public Location getLocation() {
        World world = Bukkit.getServer().getWorld(worldUuid);
        return new Location(world, x, y, z, yaw, 0);
    }
    
    /** Returns the player record to which the jump record belongs. */
    public PlayerRecord getPlayerRecord() {
        return PlayerRecord.load(playerUuid);
    }
    
    public boolean isPrivate() { return (playerUuid != null); }
    public boolean isPublic()  { return (playerUuid == null); }
    
    /**
     * Gets the jump record with the given ID from the database.  Logs errors.
     * @param  id ID of the jump.
     * @return Jump record or null if DNE or error.
     */
    public static JumpRecord load(int id) {
        // Get database connection
        Connection conn = DBManager.getConnection();
        if (conn == null) return null;
        
        // Create select statement
        final String sql = "SELECT * FROM jumps WHERE `id`=? LIMIT 1;";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            // Set statement parameters and execute
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            
            // Return a new jump record or null if no results
            return rs.next() ? new JumpRecord(rs) : null;
        } catch (SQLException e) {
            DBManager.logSevere("Failed to fetch jump record:\n"+e.toString());
            return null;
        }
    }
    
    /**
     * Returns list of all JumpRecords belonging to the player, in alphabetical order.  Logs errors.
     * @param playerUuid UUID of the player to which the JumpRecords belong.
     * @return List of all JumpRecords belonging to the player (may be empty), or null on error.
     */
    public static List<JumpRecord> load(UUID playerUuid) {
        // Get database connection
        Connection conn = DBManager.getConnection();
        if (conn == null) return null;
        
        // Create select statement
        final String sql = "SELECT * FROM jumps WHERE `player_uuid`=? ORDER BY `name`;";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            // Set statement parameters and execute
            ps.setString(1, (playerUuid != null) ?
                    playerUuid.toString() : SqlPublicUuid.toString());
            ResultSet rs = ps.executeQuery();
            
            // Get number of results
            rs.last();
            int resultCount = rs.getRow()+1;
            rs.beforeFirst();
            
            // Get JumpRecords from the result set and return
            List<JumpRecord> jumpRecords = new ArrayList<>(resultCount);
            while (rs.next())
                jumpRecords.add(new JumpRecord(rs));
            return jumpRecords;
        } catch (SQLException e) {
            DBManager.logSevere("Failed to fetch jump records:\n"+e.toString());
            return null;
        }
    }
    
    /**
     * Gets the jump record with the given ID from the database.  Logs errors.
     * @param  playerUuid UUID of the player to which the jump record belongs.
     * @param  name Jump name.
     * @return Jump record or null if DNE or error.
     */
    public static JumpRecord load(UUID playerUuid, String name) {
        // Get database connection
        Connection conn = DBManager.getConnection();
        if (conn == null) return null;
        
        // Create select statement
        final String sql = "SELECT * FROM jumps WHERE `player_uuid`=? AND `name`=? LIMIT 1;";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            // Set statement parameters and execute
            ps.setString(1, (playerUuid != null) ?
                    playerUuid.toString() : SqlPublicUuid.toString());
            ps.setString(2, name);
            ResultSet rs = ps.executeQuery();
            
            // Return a new jump record or null if no results
            return rs.next() ? new JumpRecord(rs) : null;
        } catch (SQLException e) {
            DBManager.logSevere("Failed to fetch jump record:\n"+e.toString());
            return null;
        }
    }
    
    /**
     * Saves the jump record to the database by inserting or updating.  Logs errors.
     * @return true on success, false on error.
     */
    public boolean save() {
        // Get database connection
        Connection conn = DBManager.getConnection();
        if (conn == null) return false;
        
        // Having an id implies that it exists in the database already
        if (id != null) {
            // Create update statement
            final String updateSql = "UPDATE jumps SET "+
                    "`player_uuid`=?,`name`=?,`world_uuid`=?,`x`=?,`y`=?,`z`=?,`yaw`=? "+
                    "WHERE `id`=?;";
            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                // Set parameters
                ps.setString(1, playerUuid.toString());
                ps.setString(2, name);
                ps.setString(3, worldUuid.toString());
                ps.setDouble(4, x);
                ps.setDouble(5, y);
                ps.setDouble(6, z);
                ps.setFloat(7, yaw);
                ps.setInt(8, id);
                // Execute and return result
                return (ps.executeUpdate() > 0);
            } catch (SQLException e) {
                DBManager.logSevere("Failed to update jump record "+id+":\n"+e.toString());
                return false;
            }
        }
        
        // Having no id implies that it does NOT exist in the database yet
        else {
            // Create insert statement
            final String insertSql = "INSERT INTO jumps "+
                    "(`player_uuid`,`name`,`world_uuid`,`x`,`y`,`z`,`yaw`) "+
                    "VALUES (?,?,?,?,?,?,?);";
            try (PreparedStatement ps = conn.prepareStatement(
                    insertSql, Statement.RETURN_GENERATED_KEYS)) {
                // Set parameters
                ps.setObject(1, (playerUuid != null ) ? playerUuid.toString() : null, Types.CHAR);
                ps.setString(2, name);
                ps.setString(3, worldUuid.toString());
                ps.setDouble(4, x);
                ps.setDouble(5, y);
                ps.setDouble(6, z);
                ps.setFloat(7, yaw);
                
                // Execute statement, returning if failed
                if (ps.executeUpdate() == 0) return false;
                
                // Set id to the generated key
                ResultSet rs = ps.getGeneratedKeys();
                if (rs.next()) {
                    id = rs.getInt(1);
                    DBManager.logInfo("jump id = "+id);
                    return true;
                } else {
                    throw new SQLException("Failed to retrieve generated key");
                }
            } catch (SQLException e) {
                DBManager.logSevere("Failed to insert jump record "+id+":\n"+e.toString());
                return false;
            }
        }
    }
    
    /** Sets the jump location. */
    public void setLocation(Location l) {
        worldUuid = l.getWorld().getUID();
        x = l.getX(); y = l.getY(); z = l.getZ();
        yaw = l.getYaw();
    }
    
    /**
     * Teleports the player to the jump location.
     * @return true if teleport was successful.
     */
    public boolean teleportPlayer(Player player) {
        Location l = getLocation();
        l.getWorld().loadChunk((int)x, (int)z);
        return player.teleport(l, PlayerTeleportEvent.TeleportCause.PLUGIN);
    }
}