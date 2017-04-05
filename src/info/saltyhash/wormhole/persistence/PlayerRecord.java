package info.saltyhash.wormhole.persistence;

import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/** Represents a row in the database table 'player'. */
public class PlayerRecord {
    public final UUID uuid;    // Primary key
    public String username;
    
    public PlayerRecord(UUID uuid, String username) {
        this.uuid     = uuid;
        this.username = username;
    }
    public PlayerRecord(Player player) {
        this(player.getUniqueId(), player.getName());
    }
    
    /** Gets the player record from the database.
     *
     * @param uuid UUID of the player.
     * @return Player record or null if DNE or error.
     */
    public static PlayerRecord load(UUID uuid) {
        Connection conn = DBManager.getConnection();
        if (conn == null) return null;
        
        // Try to get the player data from the database
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT `username` FROM players WHERE `uuid` = ? LIMIT 1;"
        )) {
            ps.setString(1, uuid.toString());
            ResultSet results = ps.executeQuery();
            
            // No matching row?
            if (!results.isBeforeFirst()) return null;
            
            // Return player record from results
            String username = results.getString("username");
            return new PlayerRecord(uuid, username);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
        return null;
    }
    
    /** Gets the player record from the database.
     *
     * @param username Username of the player.
     * @return Player record or null if DNE or error.
     */
    public static PlayerRecord load(String username) {
        Connection conn = DBManager.getConnection();
        if (conn == null) return null;
        
        // Try to get the player data from the database
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT `uuid` FROM players WHERE `username` = ? LIMIT 1;"
        )) {
            ps.setString(1, username);
            ResultSet results = ps.executeQuery();
            
            // No matching row?
            if (!results.isBeforeFirst()) return null;
            
            // Return player record from results
            UUID uuid = UUID.fromString(results.getString("uuid"));
            return new PlayerRecord(uuid, username);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
        return null;
    }
    
    /** Deletes the player record from the database.
     * WARNING: This will delete all jumps and signs associated with the player!
     *
     * @return true on success (even if record DNE); false on SQL error.
     */
    public boolean delete() {
        Connection conn = DBManager.getConnection();
        if (conn == null) return false;
        
        // Try to delete the player record from the database
        try (PreparedStatement delete = conn.prepareStatement(
                "DELETE FROM players WHERE `uuid` = ?;"
        )) {
            delete.setString(1, this.uuid.toString());
            delete.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }
    
    /** Saves the player record to the database.
     *
     * @return true on success; false on error.
     */
    public boolean save() {
        Connection conn = DBManager.getConnection();
        if (conn == null) return false;
        
        // Try to execute update
        try (PreparedStatement update = conn.prepareStatement(
                "UPDATE players SET `username` = ? WHERE `uuid` = ?;"
        )) {
            update.setString(1, this.username);
            update.setString(2, this.uuid.toString());
            // Success?
            if (update.executeUpdate() > 0) return true;
            
            // Row does not exist?
            else {
                // Try to insert a new row
                try (PreparedStatement insert = conn.prepareStatement(
                        "INSERT INTO players (`uuid`, `username`) VALUES (?, ?);"
                )) {
                    insert.setString(1, this.uuid.toString());
                    insert.setString(2, this.username);
                    return (insert.executeUpdate() > 0);
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
        return false;
    }
}