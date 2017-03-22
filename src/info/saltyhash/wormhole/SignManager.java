package info.saltyhash.wormhole;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;

import org.bukkit.block.Sign;

/** Provides methods for sign management. */
class SignManager {
    private final Connection db;
    private final JumpManager jumpMgr;
    private final Logger logger;

    SignManager(Connection db, JumpManager jumpMgr, Logger logger) {
        this.db      = db;
        this.jumpMgr = jumpMgr;
        this.logger  = logger;
        this.setupDatabase();
    }
    
    /** @param level 0: Info; 1: Warning; 2: Severe. */
    private void log(int level, String log) {
        if (this.logger == null) return;
        if (level == 0) this.logger.info(log);
        else if (level == 1) this.logger.warning(log);
        else this.logger.severe(log);
    }

	/** Setup the database. */
    private void setupDatabase() {
        try {
            Statement smt = this.db.createStatement();
            
            // Create signs table if necessary
            smt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS signs ("+
                "  world_name TEXT,"+
                "  x INTEGER, y INTEGER, z INTEGER,"+
                "  player_name TEXT,"+
                "  jump_name TEXT,"+
                "  PRIMARY KEY (world_name, x, y, z),"+
                "  FOREIGN KEY (player_name, jump_name)"+
                "    REFERENCES jumps (player_name, jump_name)"+
                "    ON DELETE CASCADE  ON UPDATE CASCADE)");
            smt.close();
        }
        catch (SQLException e) {
            // Failed for some reason
            this.log(2, "Failed to create table \"signs\"; "+e.getLocalizedMessage());
        }
    }

    /**
     * Assigns the sign to the given Jump.
     * @return 0: Success; 1: Sign already set.
     */
    int addSignJump(Sign sign, Jump jump) {
        String worldName = sign.getWorld().getName();
        return this.addSignJump(worldName, sign.getX(), sign.getY(), sign.getZ(), jump);
    }

    /**
     * Assigns the sign to the given Jump.
     * @return 0: Success; 1: Sign already set.
     */
    int addSignJump(String worldName, int x, int y, int z, Jump jump) {
        // Security check for SQL
        String playerName = jump.playerName.replace("'", "''");
        String jumpName   = jump.jumpName.replace("'", "''");
        
        try {
            Statement smt = this.db.createStatement();
            
            // Execute command to set sign jump
            smt.executeUpdate(String.format(
                "INSERT INTO signs VALUES('%s',%d,%d,%d,'%s','%s')",
                worldName, x, y, z, playerName, jumpName));
            smt.close();
        }
        catch (SQLException e) {
            // Failed, probably because sign is already set
            return 1;
        }
        return 0;
    }

    /**
     * Removes the sign from its Jump.
     * @return 0: Success; 1: Failure.
     */
    int delSignJump(Sign sign) {
        String worldName = sign.getWorld().getName();
        return this.delSignJump(worldName, sign.getX(), sign.getY(), sign.getZ());
    }

    /**
     * Removes the sign at the given coordinates from its Jump.
     * @return 0: Success; 1: Sign not set; 2: Unknown failure.
     */
    public int delSignJump(String worldName, int x, int y, int z) {
        try {
            Statement smt = this.db.createStatement();
            
            // Execute command to delete sign jump connection
            int result = smt.executeUpdate(String.format(
                "DELETE FROM signs WHERE world_name='%s' AND x=%d AND y=%d AND z=%d",
                worldName, x, y, z));
            smt.close();
            
            // Nothing deleted; sign not set
            if (result == 0) return 1;
        }
        catch (SQLException e) {
            // Failed for some reason
            return 2;
        }
        return 0;
    }

    /** @return The Jump assigned to the sign (null if sign has no Jump). */
    Jump getSignJump(Sign sign) {
        String worldName = sign.getWorld().getName();
        return this.getSignJump(worldName, sign.getX(), sign.getY(), sign.getZ());
    }

    /** @return The Jump assigned to the sign at the given coordinates (null if sign has no Jump). */
    Jump getSignJump(String worldName, int x, int y, int z) {
        String playerName, jumpName;
        
        try {
            Statement smt = this.db.createStatement();
            
            // Execute command to get playerName and jumpName for given sign
            ResultSet results = smt.executeQuery(String.format(
                "SELECT player_name,jump_name FROM signs WHERE "+
                "world_name='%s' AND x=%d AND y=%d AND z=%d",
                worldName, x, y, z));
            
            // Get player and jump names and undo the security checks for SQL 
            results.next();
            playerName = results.getString("player_name").replace("''", "'");
            jumpName   = results.getString("jump_name").replace("''", "'");
            smt.close();
        }
        catch (SQLException e) {
            // Query failed for some reason
            return null;
        }
        
        // Get and return Jump from Jump manager
        return this.jumpMgr.getJump(playerName, jumpName);
    }
}