package info.saltyhash.wormhole.persistence;

import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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
    
    /* <SQL-related Fields and Methods> */
    
    private PlayerRecord(ResultSet rs) throws SQLException {
        this.uuid     = UUID.fromString(rs.getString("uuid"));
        this.username = rs.getString("username");
    }
    
    private static PreparedStatement deleteStmt;
    private static PreparedStatement getDeleteStmt() {
        final String sql = "DELETE FROM players WHERE `uuid`=?;";
        
        PreparedStatement ps = PlayerRecord.lazyCreatePreparedStatement(
                PlayerRecord.deleteStmt, sql
        );
        return (PlayerRecord.deleteStmt = ps);
    }
    
    private static PreparedStatement insertStmt;
    private static PreparedStatement getInsertStmt() {
        final String sql = "INSERT INTO players (`uuid`,`username`) VALUES (?,?);";
        
        PreparedStatement ps = PlayerRecord.lazyCreatePreparedStatement(
                PlayerRecord.insertStmt, sql
        );
        return (PlayerRecord.insertStmt = ps);
    }
    
    private static PreparedStatement selectWithUsername;
    private static PreparedStatement getSelectWithUsername() {
        final String sql = "SELECT `uuid`,`username` FROM players "+
                "WHERE `username`=? LIMIT 1;";
        
        PreparedStatement ps = PlayerRecord.lazyCreatePreparedStatement(
                PlayerRecord.selectWithUsername, sql
        );
        return (PlayerRecord.selectWithUsername = ps);
    }
    
    private static PreparedStatement selectWithUuid;
    private static PreparedStatement getSelectWithUuid() {
        final String sql = "SELECT `uuid`,`username` FROM players "+
                "WHERE `uuid`=? LIMIT 1;";
        
        PreparedStatement ps = PlayerRecord.lazyCreatePreparedStatement(
                PlayerRecord.selectWithUuid, sql
        );
        return (PlayerRecord.selectWithUuid = ps);
    }
    
    private static PreparedStatement updateStmt;
    private static PreparedStatement getUpdateStmt() {
        final String sql = "UPDATE players SET `username`=? WHERE `uuid`=?;";
        
        PreparedStatement ps = PlayerRecord.lazyCreatePreparedStatement(
                PlayerRecord.updateStmt, sql
        );
        return (PlayerRecord.updateStmt = ps);
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
                return conn.prepareStatement(sql);
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
    
    /* </SQL-related Fields and Methods> */
    
    /* <Persistence Methods> */
    
    /**
     * Deletes the player record from the database.
     * WARNING: This will delete all jumps and signs associated with the player!
     * @return true on success (even if record DNE); false on SQL error.
     */
    public boolean delete() {
        // Get the statement associated with this action
        PreparedStatement ps = PlayerRecord.getDeleteStmt();
        if (ps == null) return false;
        
        try {
            // Set the statement parameters and execute the update
            ps.setString(1, this.uuid.toString());
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Gets the player record from the database.
     * @param username Username of the player.
     * @return Player record or null if DNE or error.
     */
    public static PlayerRecord load(String username) {
        // Get the prepared statement for this query
        PreparedStatement ps = PlayerRecord.getSelectWithUsername();
        if (ps == null) return null;
        
        try {
            // Setup the prepared statement and execute the query
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            
            // Return new player record or null if there are no results
            return rs.isBeforeFirst() ? new PlayerRecord(rs) : null;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Gets the player record from the database.
     * @param  uuid UUID of the player.
     * @return Player record or null if DNE or error.
     */
    public static PlayerRecord load(UUID uuid) {
        // Get the prepared statement for this query
        PreparedStatement ps = PlayerRecord.getSelectWithUuid();
        if (ps == null) return null;
        
        try {
            // Setup the prepared statement and execute the query
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            
            // Return new player record or null if there are no results
            return rs.isBeforeFirst() ? new PlayerRecord(rs) : null;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }
    
    /** Saves the player record to the database.
     *
     * @return true on success; false on error.
     */
    public boolean save() {
        // Get the update statement
        PreparedStatement ps = PlayerRecord.getUpdateStmt();
        if (ps == null) return false;
        
        // Try to execute update
        try {
            // Set the statement parameters and execute update
            ps.setString(1, this.username);
            ps.setString(2, this.uuid.toString());
            if (ps.executeUpdate() > 0) return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
        
        // At this point, update has failed because row DNE; try insert.
        // Get the insert statement
        ps = PlayerRecord.getInsertStmt();
        if (ps == null) return false;
        
        // Try to execute insert
        try {
            // Set the statement parameters and execute update
            ps.setString(1, this.username);
            ps.setString(2, this.uuid.toString());
            return (ps.executeUpdate() > 0);
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
    
    /* </Persistence Methods> */
}