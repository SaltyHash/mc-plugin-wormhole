package info.saltyhash.wormhole.persistence;

import org.bukkit.Server;
import org.bukkit.entity.Player;

import java.sql.*;
import java.util.UUID;

/** Represents a row in the database table 'player'. */
public class PlayerRecord {
    public UUID   uuid;         // Primary key
    public String username;
    
    public PlayerRecord(UUID uuid, String username) {
        this.uuid     = uuid;
        this.username = username;
    }
    
    public PlayerRecord(Player player) {
        this(player.getUniqueId(), player.getName());
    }
    
    private PlayerRecord(ResultSet rs) throws SQLException {
        this.uuid     = UUID.fromString(rs.getString("uuid"));
        this.username = rs.getString("username");
    }
    
    /**
     * Deletes the player record from the database.  Logs errors.
     * WARNING: This will delete all jumps and signs associated with the player!
     * @return true on success (even if record DNE); false on SQL error.
     */
    public boolean delete() {
        // Get database connection
        Connection conn = DBManager.getConnection();
        if (conn == null) return false;
        
        // Create statement
        final String sql = "DELETE FROM players WHERE `uuid`=?;";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            // Set statement parameters and execute
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            DBManager.logSevere("Failed to delete player record:\n"+e.toString());
            return false;
        }
        return true;
    }
    
    /** Returns the player on the server corresponding to this player record, if they are online. */
    public Player getPlayer(Server server) {
        return server.getPlayer(uuid);
    }
    
    /**
     * Gets the player record with given username from the database.  Logs errors.
     * @param username Username of the player.
     * @return Player record or null if DNE or error.
     */
    public static PlayerRecord load(String username) {
        // Get database connection
        Connection conn = DBManager.getConnection();
        if (conn == null) return null;
        
        // Create statement
        final String sql = "SELECT * FROM players WHERE `username`=? LIMIT 1;";
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
     * Saves the player record to the database.  Logs errors.
     * @return true on success; false on error.
     */
    public boolean save() {
        // Get database connection
        Connection conn = DBManager.getConnection();
        if (conn == null) return false;
        
        // Create update statement
        final String updateSql = "UPDATE players SET `username`=? WHERE `uuid`=?;";
        try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
            // Set statement parameters and execute, returning on success
            ps.setString(1, username);
            ps.setString(2, uuid.toString());
            if (ps.executeUpdate() > 0) return true;
        } catch (SQLException e) {
            DBManager.logSevere("Failed to save player record via update:\n"+e.toString());
            return false;
        }
        
        // At this point, UPDATE has failed, so try INSERT:
        // Create insert statement
        final String insertSql = "INSERT INTO players (`uuid`,`username`) VALUES (?,?);";
        try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
            // Set statement parameters and execute, returning success or failure
            ps.setString(1, uuid.toString());
            ps.setString(2, username);
            return (ps.executeUpdate() > 0);
        } catch (SQLException e) {
            DBManager.logSevere("Failed to save player record via insert:\n"+e.toString());
            return false;
        }
    }
}