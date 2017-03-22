package info.saltyhash.wormhole;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/** Provides methods for jump management. */
class JumpManager {
    private final Connection db;
    private final Logger logger;

    JumpManager(Connection db, Logger logger) {
        this.db = db;
        this.logger = logger;
        setupDatabase();
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
            
            // Create jumps table if necessary
            smt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS jumps ("+
                "  player_name TEXT,"+
                "  jump_name TEXT,"+
                "  world_name TEXT,"+
                "  x REAL, y REAL, z REAL, yaw REAL,"+
                "  PRIMARY KEY (player_name, jump_name))");
            smt.close();
        }
        catch (SQLException e) {
            // Failed to establish connection
            this.log(2, "Failed to create table \"jumps\"; "+e.getLocalizedMessage());
        }
    }

    /**
     * Adds the given Jump to the manager.
     * @return 0: Success; 1: Player doesn't exist; 2: Jump name already exists.
     */
    int addJump(Jump jump) {
        return this.addJump(jump.playerName, jump.jumpName, jump.getDest());
    }

    /**
     * Adds the given Jump to the manager.
     * @return 0: Success; 1: Player doesn't exist; 2: Jump name already exists.
     */
    int addJump(String playerName, String jumpName, Location dest) {
        // Security check for SQL
        playerName = playerName.replace("'", "''");
        jumpName   = jumpName.replace("'", "''");
        
        // If private Jump, check if player exists
        if (!playerName.isEmpty())
            if (!Bukkit.getServer().getOfflinePlayer(playerName).hasPlayedBefore()) return 1;
        
        try {
            Statement smt = this.db.createStatement();
            // Create Jump
            String worldName = dest.getWorld().getName();
            double x = dest.getX(), y = dest.getY(), z = dest.getZ();
            float yaw = dest.getYaw();
            // Execute command to add jump
            smt.executeUpdate(String.format(
                "INSERT INTO jumps VALUES('%s','%s','%s',%f,%f,%f,%f)",
                playerName, jumpName, worldName, x, y, z, yaw));
            smt.close();
        }
        catch (SQLException e) {
            // Failed, probably because jumpName already exists
            return 2;
        }
        return 0;
    }

    /**
     * Deletes the specified Jump.
     * @return 0: Success; 1: Jump DNE; 2: Unknown failure.
     */
    int delJump(Jump jump) {
        return this.delJump(jump.playerName, jump.jumpName);
    }

    /**
     * Deletes the specified Jump.
     * @return 0: Success; 1: Jump DNE; 2: Unknown failure.
     */
    int delJump(String playerName, String jumpName) {
        // Security check for SQL
            playerName = playerName.replace("'", "''");
            jumpName   = jumpName.replace("'", "''");
        
        try {
            Statement smt = this.db.createStatement();
            // Execute command to delete jump
            int result = smt.executeUpdate(String.format(
                "DELETE FROM jumps WHERE player_name='%s' AND jump_name='%s'",
                playerName, jumpName));
            smt.close();
            
            // If nothing changed...
            if (result == 0) return 1;
        }
        catch (SQLException e) {
            // Failed
            return 2;
        }
        return 0;
    }

    /** @return True if the Jump exists. */
    boolean exists(Jump jump) {
        return this.exists(jump.playerName, jump.jumpName);
    }

    /** @return True if the Jump exists. */
    boolean exists(String playerName, String jumpName) {
        return (this.getJump(playerName, jumpName) != null);
    }

    /** @return The specified Jump (null if not found). */
    Jump getJump(String playerName, String jumpName) {
        // Security check for SQL
        playerName = playerName.replace("'", "''");
        jumpName   = jumpName.replace("'", "''");
        
        Jump jump = new Jump();
        jump.playerName = playerName;
        jump.jumpName   = jumpName;
        
        try {
            Statement smt = this.db.createStatement();
            
            // Execute command to get Jump
            ResultSet results = smt.executeQuery(String.format(
                "SELECT world_name,x,y,z,yaw FROM jumps WHERE "+
                "player_name='%s' AND jump_name='%s'", playerName, jumpName));
            
            // Set jump parameters from results
            results.next();
            jump.worldName = results.getString("world_name");
            jump.x   = results.getDouble("x");
            jump.y   = results.getDouble("y");
            jump.z   = results.getDouble("z");
            jump.yaw = results.getFloat("yaw");
            smt.close();
        }
        catch (SQLException e) {
            return null;
        }
        
        // Reverse the security check for SQL
        jump.playerName = jump.playerName.replace("''", "'");
        jump.jumpName   = jump.jumpName.replace("''", "'");
        
        return jump;
    }

    /** @return A list of jump names belonging to the given player. */
    List<String> getJumpNameList(Player player) {
        return this.getJumpNameList(player.getName());
    }

    /**
     * @param playerName An empty string designates public Jumps.
     * @return A list of jump names belonging to the given player (or public).
     */
    List<String> getJumpNameList(String playerName) {
        List<String> jumpNames = new ArrayList<>();
        
        try {
            Statement smt = this.db.createStatement();
            
            // Execute command to get list of jump names belonging to player
            ResultSet results = smt.executeQuery(String.format(
                "SELECT jump_name FROM jumps WHERE player_name='%s'", playerName));
            
            // Build list from results
            while (results.next()) {
                jumpNames.add(results.getString("jump_name"));
            }
            smt.close();
        }
        // Unknown error
        catch (SQLException e) {
            return null;
        }
        
        return jumpNames;
    }

    /**
     * Updates old jump with new jump.  Update cascades to signs as well.
     * @return 0: Success; 1: New player DNE; 2: Old Jump DNE; 3: Unknown failure.
     */
    int updateJump(Jump oldJump, Jump newJump) {
        // If new private Jump, check if new player exists
        if (newJump.isPrivate())
            if (!Bukkit.getServer().getOfflinePlayer(newJump.playerName).hasPlayedBefore()) return 1;
        
        try {
            Statement smt = this.db.createStatement();
            
            // Get new jump info
            Location dest = newJump.getDest();
            String worldName = dest.getWorld().getName();
            double x = dest.getX(), y = dest.getY(), z = dest.getZ();
            float yaw = dest.getYaw();
            
            // Build set representing new jump
            String new_set = String.format(
                "player_name='%s',jump_name='%s',world_name='%s',"+
                "x=%f,y=%f,z=%f,yaw=%f",
                newJump.playerName, newJump.jumpName, worldName, x, y, z, yaw);
            
            // Execute command to update jump
            int changed = smt.executeUpdate(String.format(
                "UPDATE jumps SET %s WHERE player_name='%s' AND jump_name='%s'",
                new_set, oldJump.playerName, oldJump.jumpName));
            smt.close();
            
            // Nothing changed, so old jump DNE
            if (changed == 0) return 2;
        }
        catch (SQLException e) {
            // Failed, probably because jumpName already exists
            return 3;
        }
        return 0;
    }
}