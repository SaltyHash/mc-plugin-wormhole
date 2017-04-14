package info.saltyhash.wormhole.persistence;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.logging.Logger;

/** Manages the database. */
@SuppressWarnings({"WeakerAccess", "SameParameterValue"})
public final class DBManager {
    private static Connection connection;
    private static File dbFile;
    private static Logger logger;
    
    private DBManager() {}
    
    @SuppressWarnings("unused")
    public static void setup(File dbFile) {
        DBManager.setup(dbFile, null);
    }
    
    public static void setup(File dbFile, Logger logger) {
        closeConnection();
        DBManager.dbFile = dbFile;
        DBManager.logger = logger;
    }
    
    /**
     * Returns a connection to the database, reusing previous connection if possible.
     * The connection is configured with foreign keys ON.  Logs errors.
     * @return Database connection, or null on error.
     */
    static Connection getConnection() {
        if (dbFile == null) throw new NullPointerException("DBManager.dbFile must not be null");
        
        // Create connection if necessary
        try {
            if (connection == null || connection.isClosed()) {
                Class.forName("org.sqlite.JDBC");
                connection = DriverManager.getConnection(
                        "jdbc:sqlite:"+dbFile.getAbsolutePath());
            }
        } catch (ClassNotFoundException | SQLException e) {
            logSevere("Failed to connect to database");
            logSevere(e.toString());
            return (connection = null);
        }
        
        // Turn on foreign keys
        try (Statement s = connection.createStatement()) {
            s.execute("PRAGMA foreign_keys=ON;");
        } catch (SQLException e) {
            logSevere("Failed to enable database foreign keys");
            logSevere(e.toString());
            DBManager.closeConnection();
            return (connection = null);
        }
        
        return connection;
    }
    
    /**
     * If a database connection exists, then its changes are committed
     * and the connection is closed.  Logs errors.
     * @return true on success; false on error.
     */
    @SuppressWarnings("UnusedReturnValue")
    public static boolean closeConnection() {
        boolean success = true;
        if (connection != null) {
            try {
                if (!connection.isClosed()) {
                    try {
                        if (!connection.getAutoCommit()) connection.commit();
                    } catch (SQLException e) {
                        logSevere("Failed to commit changes to database before closing");
                        success = false;
                    }
                    connection.close();
                }
            } catch (SQLException e) {
                logWarning("Failed to close database connection");
                success = false;
            }
            connection = null;
        }
        return success;
    }
    
    static void logInfo(String msg) {
        if (logger != null) logger.info(msg);
    }
    static void logWarning(String msg) {
        if (logger != null) logger.warning(msg);
    }
    static void logSevere(String msg) {
        if (logger != null) logger.severe(msg);
    }
    
    /** Returns the current database version, or -1 on error. */
    private static int getDatabaseVersion() {
        try (Statement s = getConnection().createStatement()) {
            // Try to get the 1st row
            ResultSet results = s.executeQuery(
                    "SELECT version FROM schema_version LIMIT 1;");
            
            // Results are empty?
            if (!results.isBeforeFirst()) {
                // Either table DNE or row DNE
                return -1;
            }
            return results.getInt("version");
        } catch (SQLException e) {
            // File or table DNE
            return -1;
        }
    }
    
    /**
     * Sets the database version.  Logs errors.
     * @param  version The version number to set the database to.
     * @return true on success; false on error.
     */
    private static boolean setDatabaseVersion(int version) {
        try (Statement s = getConnection().createStatement()) {
            s.executeUpdate("DELETE FROM schema_version;");
            s.executeUpdate("INSERT INTO schema_version\n"+
                    "(`version`) VALUES ("+version+");"
            );
        } catch (SQLException e) {
            logSevere("Failed to set database version");
            logSevere(e.toString());
            return false;
        }
        
        return true;    // Success
    }
    
    /**
     * Migrates the database to the latest version.
     * @return true on success; false on failure.
     */
    public static boolean migrate() {
        switch (getDatabaseVersion()) {
            case -1: if (!migration0()) return false;
            default: break;
        }
        return true;    // Success
    }
    
    /* <Migrations> */
    
    /**
     * Migrates to database version from previous.  Logs errors.
     * @return true on success; false on error.
     */
    private static boolean migration0() {
        final UUID   PUBLIC_PLAYER_UUID = new UUID(0, 0);
        final String LOG_PREFIX = "[Migration 0] ";
        logInfo(LOG_PREFIX+"Starting...");
        
        /* <Create Tables> */
        
        Connection conn = getConnection();
        
        // Create table 'schema_version'
        try (Statement s = conn.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS schema_version (\n" +
                    "  `version` INTEGER);"
            );
        } catch (SQLException e) {
            logSevere(LOG_PREFIX+"Failed to create table schema_version");
            e.printStackTrace();
            return false;
        }
        
        // Create table 'players'
        try (Statement s = conn.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS players (\n" +
                    "  `uuid`     CHAR(36) PRIMARY KEY,\n" +
                    "  `username` VARCHAR(16));"
            );
        } catch (SQLException e) {
            logSevere(LOG_PREFIX+"Failed to create table 'players'");
            e.printStackTrace();
            return false;
        }
        
        // Insert public player into players database
        try (PreparedStatement s = conn.prepareStatement(
                "INSERT INTO players (`uuid`,`username`) VALUES (?,?);")) {
            s.setString(1, PUBLIC_PLAYER_UUID.toString());
            s.setNull(2, Types.CHAR);
            switch (s.executeUpdate()) {
                case 1 : break;
                case 0 : throw new SQLException("Record not added");
                default: throw new SQLException("More than one record affected");
            }
        } catch (SQLException e) {
            logSevere(LOG_PREFIX+"Failed to insert public player into table 'players'");
            e.printStackTrace();
            return false;
        }
        
        // Create table 'jumps'
        try (Statement s = conn.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS jumps (\n" +
                    "  `id`          INTEGER PRIMARY KEY,\n" +
                    "  `player_uuid` CHAR(36) REFERENCES players(`uuid`)\n" +
                    "                ON DELETE CASCADE ON UPDATE CASCADE,\n" +
                    "  `name`        TEXT,\n" +
                    "  `world_uuid`  CHAR(36),\n" +
                    "  `x` REAL, `y` REAL, `z` REAL, `yaw` REAL,\n" +
                    "  UNIQUE (`player_uuid`, `name`));"
            );
        } catch (SQLException e) {
            logSevere(LOG_PREFIX+"Failed to create table 'jumps'");
            e.printStackTrace();
            return false;
        }
        
        // Create table 'signs'
        try (Statement s = conn.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS signs (\n" +
                    "  `world_uuid` CHAR(36),\n" +
                    "  `x` INTEGER, `y` INTEGER, `z` INTEGER,\n" +
                    "  `jump_id` INTEGER REFERENCES jumps(`id`)\n" +
                    "            ON DELETE CASCADE ON UPDATE CASCADE,\n" +
                    "  PRIMARY KEY (`world_uuid`, `x`, `y`, `z`));"
            );
        } catch (SQLException e) {
            logSevere(LOG_PREFIX+"Failed to create table 'signs'");
            e.printStackTrace();
            return false;
        }
        
        if (!setDatabaseVersion(0)) {
            logSevere(LOG_PREFIX+"Failed to set database version to 0");
            return false;
        }
        
        /* </Create Tables> */
        
        /* <Pre-1.4.0 Database Import> */
        
        // Check for old pre-1.4.0 database file
        File oldDbFile = new File(dbFile.getParent()+File.separator+"Wormhole.sqlite.db");
        if (oldDbFile.exists()) {
            logInfo(LOG_PREFIX+"Pre-1.4.0 database found; importing into new database...");
            
            // Get list of players from the server
            OfflinePlayer[] players = Bukkit.getOfflinePlayers();
            if (players.length == 0) {
                logSevere(LOG_PREFIX+"No existing players found on the server");
                return false;
            }
            // Create map of usernames to UUIDs
            Map<String, UUID> serverPlayerUsernamesToUuids = new HashMap<>(players.length);
            for (OfflinePlayer player : players) {
                if (player.getUniqueId() == null) {
                    logWarning(LOG_PREFIX+"Old player '"+player.getName()+
                            "' does not have UUID; moving on...");
                    continue;
                }
                serverPlayerUsernamesToUuids.put(
                        player.getName().toLowerCase(), player.getUniqueId());
            }
            
            // Get list of worlds from the server
            List<World> worlds = Bukkit.getWorlds();
            if (worlds.size() == 0) {
                logSevere(LOG_PREFIX+"No existing worlds found on the server");
                return false;
            }
            // Create map of world names to world UUIDs
            Map<String, UUID> serverWorldNamesToUuids = new HashMap<>(worlds.size());
            for (World world : worlds) {
                serverWorldNamesToUuids.put(world.getName().toLowerCase(), world.getUID());
            }
    
            Set<String> oldDbPlayerUsernames     = new HashSet<>();
            Set<String> oldDbWorldNames          = new HashSet<>();
            List<Map<String, Object>> oldDbJumps = new ArrayList<>();
            List<Map<String, Object>> oldDbSigns = new ArrayList<>();
            
            Connection oldDbConn = null;
            Statement  oldDbStatement = null;
            try {
                // Get connection to old database file
                Class.forName("org.sqlite.JDBC");
                oldDbConn = DriverManager.getConnection("jdbc:sqlite:"+oldDbFile.getAbsolutePath());
                oldDbStatement = oldDbConn.createStatement();
                
                // Get player usernames from old database
                ResultSet rs = oldDbStatement.executeQuery(
                        "SELECT DISTINCT `player_name` FROM jumps WHERE `player_name` <> '';");
                while (rs.next()) {
                    oldDbPlayerUsernames.add(rs.getString("player_name").toLowerCase());
                }
                rs.close();
                
                // Get world names from old database
                rs = oldDbStatement.executeQuery("SELECT DISTINCT `world_name` FROM jumps;");
                while (rs.next()) {
                    oldDbWorldNames.add(rs.getString("world_name").toLowerCase());
                }
                rs.close();
                rs = oldDbStatement.executeQuery("SELECT DISTINCT `world_name` FROM signs;");
                while (rs.next()) {
                    oldDbWorldNames.add(rs.getString("world_name").toLowerCase());
                }
                rs.close();
                
                // Get jumps from old database
                rs = oldDbStatement.executeQuery("SELECT * FROM jumps;");
                while (rs.next()) {
                    Map<String, Object> oldDbJump = new HashMap<>(7);
                    oldDbJump.put("player_name", rs.getString("player_name"));
                    oldDbJump.put("jump_name",   rs.getString("jump_name"));
                    oldDbJump.put("world_name",  rs.getString("world_name"));
                    oldDbJump.put("x",           rs.getDouble("x"));
                    oldDbJump.put("y",           rs.getDouble("y"));
                    oldDbJump.put("z",           rs.getDouble("z"));
                    oldDbJump.put("yaw",         rs.getFloat("yaw"));
                    oldDbJumps.add(oldDbJump);
                }
                rs.close();
                
                // Get signs from old database
                rs = oldDbStatement.executeQuery("SELECT * FROM signs;");
                while (rs.next()) {
                    Map<String, Object> oldDbSign = new HashMap<>(6);
                    oldDbSign.put("world_name",  rs.getString("world_name"));
                    oldDbSign.put("x",           rs.getInt("x"));
                    oldDbSign.put("y",           rs.getInt("y"));
                    oldDbSign.put("z",           rs.getInt("z"));
                    oldDbSign.put("player_name", rs.getString("player_name"));
                    oldDbSign.put("jump_name",   rs.getString("jump_name"));
                    oldDbSigns.add(oldDbSign);
                }
                rs.close();
            } catch (ClassNotFoundException | SQLException e) {
                logSevere(LOG_PREFIX+"Failed to import pre-1.4.0 database!");
                e.printStackTrace();
                return false;
            } finally {
                if (oldDbStatement != null) {
                    try { oldDbStatement.close(); }
                    catch (SQLException e) {
                        logWarning(LOG_PREFIX+"Failed to close old database statement");
                        e.printStackTrace();
                    }
                }
                if (oldDbConn != null) {
                    try { oldDbConn.close(); }
                    catch (SQLException e) {
                        logWarning(LOG_PREFIX+"Failed to close connection to old database");
                        e.printStackTrace();
                    }
                }
            }
            
            // Make sure old database player usernames exist in server usernames
            for (String oldDbUsername : oldDbPlayerUsernames) {
                if (!oldDbUsername.equals("") &&
                        !serverPlayerUsernamesToUuids.keySet().contains(oldDbUsername)) {
                    logSevere(LOG_PREFIX+"Server does not have player '"+oldDbUsername+
                            "' found in the old database");
                    return false;
                }
            }
            
            // Make sure old database world names exist in server
            for (String oldDbWorldName : oldDbWorldNames) {
                if (!serverWorldNamesToUuids.keySet().contains(oldDbWorldName)) {
                    logSevere(LOG_PREFIX+"Server does not have world '"+oldDbWorldName+
                            "' found in the old database");
                    return false;
                }
            }
            
            // Insert old database players into the new database
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO players (`uuid`,`username`) VALUES (?,?);")) {
                for (String playerUsername : oldDbPlayerUsernames) {
                    UUID playerUuid = serverPlayerUsernamesToUuids.get(playerUsername);
                    ps.setString(1, playerUuid.toString());
                    ps.setString(2, playerUsername);
                    ps.addBatch();
                }
                // Execute batch insert and make sure all succeeded
                for (int result : ps.executeBatch()) {
                    if (result < 1) {
                        logSevere(LOG_PREFIX+"Failed to insert player from old database into new");
                        return false;
                    }
                }
            } catch (SQLException e) {
                logSevere(LOG_PREFIX+"Failed to insert players from old database into new");
                e.printStackTrace();
                return false;
            }
            
            // Insert old database jumps into the new database
            Map<String, Integer> jumpNamesToIds = new HashMap<>();
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO jumps "+
                    "(`player_uuid`,`name`,`world_uuid`,`x`,`y`,`z`,`yaw`)"+
                    "VALUES (?,?,?,?,?,?,?);", Statement.RETURN_GENERATED_KEYS)) {
                for (Map<String, Object> oldDbJump : oldDbJumps) {
                    // Get player UUID
                    String playerUsername = (String) oldDbJump.get("player_name");
                    UUID   playerUuid;
                    // - Public jump?
                    if (playerUsername.equals(""))
                        playerUuid = PUBLIC_PLAYER_UUID;
                    else
                        playerUuid = serverPlayerUsernamesToUuids.get(playerUsername);
                    // Get jump name
                    String jumpName = (String) oldDbJump.get("jump_name");
                    // Get world UUID
                    String worldName = (String) oldDbJump.get("world_name");
                    UUID   worldUuid = serverWorldNamesToUuids.get(worldName);
                    // Set parameters and execute
                    ps.setString(1, playerUuid.toString());
                    ps.setString(2, jumpName);
                    ps.setString(3, worldUuid.toString());
                    ps.setDouble(4, (double) oldDbJump.get("x"));
                    ps.setDouble(5, (double) oldDbJump.get("y"));
                    ps.setDouble(6, (double) oldDbJump.get("z"));
                    ps.setFloat (7,  (float) oldDbJump.get("yaw"));
                    if (ps.executeUpdate() < 1) {
                        logSevere(LOG_PREFIX+"Failed to insert jump from old database into new");
                        return false;
                    }
                    // Get the new ID of the inserted jump
                    ResultSet rs = ps.getGeneratedKeys();
                    if (rs.next()) {
                        String key = playerUsername+"\n"+jumpName;
                        jumpNamesToIds.put(key, rs.getInt(1));
                    } else {
                        logSevere(LOG_PREFIX+"Failed to get generated jump ID");
                        return false;
                    }
                    rs.close();
                }
            } catch (SQLException e) {
                logSevere(LOG_PREFIX+"Failed to insert jumps from old database into new");
                e.printStackTrace();
                return false;
            }
            
            // Insert old database signs into the new database
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO signs "+
                    "(`world_uuid`,`x`,`y`,`z`,`jump_id`) VALUES (?,?,?,?,?);")) {
                for (Map<String, Object> oldDbSign : oldDbSigns) {
                    // Get jump ID
                    String playerUsername = (String) oldDbSign.get("player_name");
                    String jumpName       = (String) oldDbSign.get("jump_name");
                    String key = playerUsername+"\n"+jumpName;
                    int jumpId = jumpNamesToIds.get(key);
                    // Get world UUID
                    String worldName = (String) oldDbSign.get("world_name");
                    UUID   worldUuid = serverWorldNamesToUuids.get(worldName);
                    // Set parameters and add to batch
                    ps.setString(1, worldUuid.toString());
                    ps.setInt(2, (int) oldDbSign.get("x"));
                    ps.setInt(3, (int) oldDbSign.get("y"));
                    ps.setInt(4, (int) oldDbSign.get("z"));
                    ps.setInt(5, jumpId);
                    ps.addBatch();
                }
                // Execute batch and make sure all succeeded
                for (int result : ps.executeBatch()) {
                    if (result < 1) {
                        logSevere(LOG_PREFIX+"Failed to insert sign from old database into new");
                        return false;
                    }
                }
            } catch (SQLException e) {
                logSevere(LOG_PREFIX+"Failed to insert signs from old database into new");
                e.printStackTrace();
                return false;
            }
            
            logInfo(LOG_PREFIX+"Done importing pre-1.4.0 database");
        }
        
        /* </Pre-1.4.0 Database Import> */
        
        logInfo(LOG_PREFIX+"Done");
        return true;
    }
    
    /* </Migrations> */
}