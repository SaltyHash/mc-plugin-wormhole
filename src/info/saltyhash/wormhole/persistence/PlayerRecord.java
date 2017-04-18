package info.saltyhash.wormhole.persistence;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.*;
import java.util.UUID;

/** Represents a row in the database table 'player'. */
@SuppressWarnings("WeakerAccess")
public class PlayerRecord {
    private Integer id;         // Primary key
    public  UUID    uuid;
    public  String  username;
    
    public PlayerRecord(UUID uuid, String username) {
        this.id       = null;
        this.uuid     = uuid;
        this.username = username;
    }
    
    public PlayerRecord(Player player) {
        this(player.getUniqueId(), player.getName());
    }
    
    private PlayerRecord(ResultSet rs) throws SQLException {
        this.id       = rs.getInt("id");
        this.uuid     = UUID.fromString(rs.getString("uuid"));
        this.username = rs.getString("username");
    }
    
    /**
     * Deletes the player record from the database.  Logs errors.
     * WARNING: This will delete all jumps and signs associated with the player!
     * @return true on success (even if record DNE); false on SQL error.
     */
    @SuppressWarnings("unused")
    public boolean delete() {
        // Get database connection
        Connection conn = DBManager.getConnection();
        if (conn == null) return false;
        
        // Create statement
        final String sql = "DELETE FROM players WHERE `id`=?;";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            // Set statement parameters and execute
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            DBManager.logSevere("Failed to delete player record:\n"+e.toString());
            return false;
        }
        return true;
    }
    
    /** Returns the database ID of the player record, or null if DNE in database. */
    public Integer getId() {
        return id;
    }
    
    /** Returns the player corresponding to this record, or null if they are not logged in. */
    @SuppressWarnings("unused")
    public Player getPlayer() { return Bukkit.getServer().getPlayer(uuid); }
    
    /** Returns true if the player UUID matches the player record UUID. */
    public boolean isPlayer(Player player) {
        return player.getUniqueId().equals(uuid);
    }
    
    /**
     * Gets the player record with given username (case-insensitive) from the database.  Logs errors.
     * @param  username Username of the player.
     * @return Player record or null if DNE or error.
     */
    public static PlayerRecord load(String username) {
        // Get database connection
        Connection conn = DBManager.getConnection();
        if (conn == null) return null;
        
        // Create statement
        final String sql = "SELECT * FROM players WHERE `username`=? COLLATE NOCASE LIMIT 1;";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            // Set statement parameters and execute
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            
            // Return a new player record or null if there are no results
            return rs.next() ? new PlayerRecord(rs) : null;
        } catch (SQLException e) {
            DBManager.logSevere("Failed to load player record:\n"+e.toString());
            return null;
        }
    }
    
    /**
     * Gets the player record with given UUID from the database.  Logs errors.
     * @param  uuid UUID of the player.
     * @return Player record or null if DNE or error.
     */
    public static PlayerRecord load(UUID uuid) {
        // Get database connection
        Connection conn = DBManager.getConnection();
        if (conn == null) return null;
    
        // Create statement
        final String sql = "SELECT * FROM players WHERE `uuid`=? LIMIT 1;";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            // Set statement parameters and execute
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
        
            // Return a new player record or null if there are no results
            return rs.next() ? new PlayerRecord(rs) : null;
        } catch (SQLException e) {
            DBManager.logSevere("Failed to load player record:\n"+e.toString());
            return null;
        }
    }
    
    /**
     * Gets the player record with given ID from the database.  Logs errors.
     * @param  id Database ID of the player record.
     * @return Player record or null if DNE or error.
     */
    public static PlayerRecord load(int id) {
        // Get database connection
        Connection conn = DBManager.getConnection();
        if (conn == null) return null;
        
        // Create statement
        final String sql = "SELECT * FROM players WHERE `id`=? LIMIT 1;";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            // Set statement parameters and execute
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            // Return a new player record or null if there are no results
            return rs.next() ? new PlayerRecord(rs) : null;
        } catch (SQLException e) {
            DBManager.logSevere("Failed to load player record:\n"+e.toString());
            return null;
        }
    }
    
    /**
     * Saves the player record to the database.  Logs errors.
     * @return true on success; false on error.
     */
    public boolean save() {
        // Get database connection
        Connection conn = DBManager.getConnection();
        if (conn == null) return false;
        
        // ID != null ==> the record already exists in the database ==> do UPDATE
        if (id != null) {
            // Create update statement
            final String sql = "UPDATE players SET `uuid`=?,`username`=? WHERE `id`=?;";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                // Set statement parameters and execute, returning success
                ps.setString(1, uuid.toString());
                ps.setString(2, username);
                ps.setInt(3, id);
                return (ps.executeUpdate() > 0);
            } catch (SQLException e) {
                DBManager.logSevere("Failed to save player record via update:\n"+e.toString());
                return false;
            }
        }
        // ID == null ==> the record does not exist in the database ==> do INSERT
        else {
            // Create insert statement
            final String sql = "INSERT INTO players (`uuid`,`username`) VALUES (?,?);";
            try (PreparedStatement ps = conn.prepareStatement(
                    sql, Statement.RETURN_GENERATED_KEYS)) {
                // Set statement parameters and execute, returning success or failure
                ps.setString(1, uuid.toString());
                ps.setString(2, username);
                
                // Execute statement, throwing exception if failed
                if (ps.executeUpdate() == 0)  throw new SQLException("Failed to insert");
                
                // Set id to the generated key
                ResultSet rs = ps.getGeneratedKeys();
                if (rs.next()) {
                    id = rs.getInt(1);
                    return true;
                } else {
                    throw new SQLException("Failed to retrieve generated key");
                }
            } catch (SQLException e) {
                DBManager.logSevere("Failed to save player record via insert:\n"+e.toString());
                return false;
            }
        }
    }
}