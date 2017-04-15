package info.saltyhash.wormhole.persistence;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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
    
    /** Returns the latest database version. */
    private static int getLatestDatabaseVersion() {
        int latestVersion = 0;
        while (true) {
            try {
                DBManager.class.getDeclaredMethod("migration"+latestVersion,
                        Connection.class, String.class);
            } catch (NoSuchMethodException e) {
                return latestVersion-1;
            }
            latestVersion++;
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
            s.executeUpdate("INSERT INTO schema_version (`version`) VALUES ("+version+");");
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
        // Return if database is already at latest version
        int version       = getDatabaseVersion();
        int latestVersion = getLatestDatabaseVersion();
        if (version == latestVersion) return true;
        // Sanity check
        if (version > latestVersion) {
            logSevere("Current database version is "+version+
                    " but the latest database version is "+latestVersion);
            return false;
        }
        
        // Get database connection
        Connection conn = getConnection();
        
        boolean autoCommit = true;
        try {
            // Commit any previous changes and disable autocommit
            autoCommit = conn.getAutoCommit();
            if (!autoCommit) conn.commit();
            conn.setAutoCommit(false);
            
            // Perform migrations in order
            for (int migration = version+1; migration <= latestVersion; migration++) {
                String logPrefix = "[Migration "+migration+"] ";
                
                // Perform single migration
                logInfo(logPrefix+"Starting:");
                Method migrationMethod = DBManager.class.getDeclaredMethod(
                        "migration"+migration, Connection.class, String.class);
                migrationMethod.invoke(null, conn, logPrefix);
                
                // Set database version; error?
                if (!setDatabaseVersion(migration))
                    throw new SQLException("Failed to set database version to "+migration);
                
                // Commit changes
                logInfo(logPrefix+"Committing changes");
                conn.commit();
                logInfo(logPrefix+"Done");
            }
            
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException
                | SQLException e) {
            e.printStackTrace();
            logSevere("Failed to perform migrations!");
            
            // Roll back changes
            logInfo("Rolling back changes");
            try {
                conn.rollback();
            } catch (SQLException e1) {
                logSevere("Failed to roll back changes");
                e1.printStackTrace();
            }
            return false;
            
        } finally {
            // Reset connection autocommit to previous value
            try {
                conn.setAutoCommit(autoCommit);
            } catch (SQLException e) {
                logWarning("Failed to set database autocommit to "+autoCommit);
                e.printStackTrace();
            }
        }
        
        logInfo("Database migrations complete");
        return true;
    }
    
    /* <Migrations> */
    
    /** Migrates to database version from previous.  Logs errors. */
    @SuppressWarnings("unused")
    private static void migration0(Connection conn, String logPrefix)
            throws IllegalStateException, SQLException {
        final UUID PUBLIC_PLAYER_UUID = new UUID(0, 0);
        
        /* <Create Tables> */
        
        try (Statement s = conn.createStatement()) {
            // Create table 'schema_version'
            logInfo(logPrefix+"Creating table 'schema_version'");
            s.execute("CREATE TABLE schema_version (\n" +
                    "  `version` INTEGER);");
            
            // Create table 'players'
            logInfo(logPrefix+"Creating table 'players'");
            s.execute("CREATE TABLE players (\n" +
                    "  `uuid`     CHAR(36) PRIMARY KEY,\n" +
                    "  `username` VARCHAR(16));");
            
            // Create table 'jumps'
            logInfo(logPrefix+"Creating table 'jumps'");
            s.execute("CREATE TABLE jumps (\n" +
                    "  `id`          INTEGER PRIMARY KEY,\n" +
                    "  `player_uuid` CHAR(36) REFERENCES players(`uuid`)\n" +
                    "                ON DELETE CASCADE ON UPDATE CASCADE,\n" +
                    "  `name`        TEXT,\n" +
                    "  `world_uuid`  CHAR(36),\n" +
                    "  `x` REAL, `y` REAL, `z` REAL, `yaw` REAL,\n" +
                    "  UNIQUE (`player_uuid`, `name`));");
            
            // Create table 'signs'
            logInfo(logPrefix+"Creating table 'signs'");
            s.execute("CREATE TABLE signs (\n" +
                    "  `world_uuid` CHAR(36),\n" +
                    "  `x` INTEGER, `y` INTEGER, `z` INTEGER,\n" +
                    "  `jump_id` INTEGER REFERENCES jumps(`id`)\n" +
                    "            ON DELETE CASCADE ON UPDATE CASCADE,\n" +
                    "  PRIMARY KEY (`world_uuid`, `x`, `y`, `z`));");
        }
        
        // Insert public player into players database
        logInfo(logPrefix+"Adding 'public' player");
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO players (`uuid`,`username`) VALUES (?,?);")) {
            ps.setString(1, PUBLIC_PLAYER_UUID.toString());
            ps.setNull(2, Types.CHAR);
            switch (ps.executeUpdate()) {
                case 0:
                    throw new SQLException("Record not added");
                case 1:
                    break;
                default:
                    throw new SQLException("More than one record affected");
            }
        }
        
        /* </Create Tables> */
        
        /* <Pre-1.4.0 Database Import> */
        
        // Check for old pre-1.4.0 database file
        File oldDbFile = new File(dbFile.getParent()+File.separator+"Wormhole.sqlite.db");
        if (oldDbFile.exists()) {
            logInfo(logPrefix+"Pre-1.4.0 database found; importing into new database "+
                    "(this could take a minute)");
            
            // Get list of players from the server
            OfflinePlayer[] players = Bukkit.getOfflinePlayers();
            if (players.length == 0) {
                throw new IllegalStateException("No existing players found on the server");
            }
            // Create map of usernames to UUIDs
            Map<String, UUID> serverPlayerUsernamesToUuids = new HashMap<>(players.length);
            for (OfflinePlayer player : players) {
                if (player.getUniqueId() == null) {
                    logWarning(logPrefix+"Player '"+player.getName()+
                            "' does not have UUID; moving on...");
                    continue;
                }
                serverPlayerUsernamesToUuids.put(player.getName(), player.getUniqueId());
            }
            
            // Get list of worlds from the server
            List<World> worlds = Bukkit.getWorlds();
            if (worlds.size() == 0) {
                throw new IllegalStateException("No existing worlds found on the server");
            }
            // Create map of world names to world UUIDs
            Map<String, UUID> serverWorldNamesToUuids = new HashMap<>(worlds.size());
            for (World world : worlds) {
                serverWorldNamesToUuids.put(world.getName(), world.getUID());
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
                    oldDbPlayerUsernames.add(rs.getString("player_name"));
                }
                rs.close();
                
                // Get world names from old database
                rs = oldDbStatement.executeQuery("SELECT DISTINCT `world_name` FROM jumps;");
                while (rs.next()) {
                    oldDbWorldNames.add(rs.getString("world_name"));
                }
                rs.close();
                rs = oldDbStatement.executeQuery("SELECT DISTINCT `world_name` FROM signs;");
                while (rs.next()) {
                    oldDbWorldNames.add(rs.getString("world_name"));
                }
                rs.close();
                
                // Get jumps from old database
                logInfo(logPrefix+"Getting jumps from old database");
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
                logInfo(logPrefix+"Getting signs from old database");
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
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("Failed to connect to pre-1.4.0 database!");
            } finally {
                if (oldDbStatement != null) oldDbStatement.close();
                if (oldDbConn != null)      oldDbConn.close();
            }
            
            // Make sure old database player usernames exist in server usernames
            for (String oldDbUsername : oldDbPlayerUsernames) {
                if (!oldDbUsername.equals("") &&
                        !serverPlayerUsernamesToUuids.keySet().contains(oldDbUsername)) {
                    throw new IllegalStateException("Server does not have player '"
                            +oldDbUsername+"' found in the old database");
                }
            }
            
            // Make sure old database world names exist in server
            for (String oldDbWorldName : oldDbWorldNames) {
                if (!serverWorldNamesToUuids.keySet().contains(oldDbWorldName)) {
                    throw new IllegalStateException("Server does not have world '"+
                            oldDbWorldName+"' found in the old database");
                }
            }
            
            // Insert old database players into the new database
            logInfo(logPrefix+"Adding players to new database");
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
                        throw new SQLException("Failed to insert player from old database into new");
                    }
                }
            }
            
            // Insert old database jumps into the new database
            logInfo(logPrefix+"Adding jumps to new database");
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
                        throw new SQLException("Failed to insert jump from old database into new");
                    }
                    // Get the new ID of the inserted jump
                    ResultSet rs = ps.getGeneratedKeys();
                    if (rs.next()) {
                        String key = playerUsername+"\n"+jumpName;
                        jumpNamesToIds.put(key, rs.getInt(1));
                    } else {
                        throw new SQLException("Failed to get generated jump ID");
                    }
                    rs.close();
                }
            }
            
            // Insert old database signs into the new database
            logInfo(logPrefix+"Adding signs to new database");
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
                int[] results = ps.executeBatch();
                for (int result : results) {
                    if (result < 1) {
                        throw new SQLException("Failed to insert sign from old database into new");
                    }
                }
            }
            
            logInfo(logPrefix+"Done importing pre-1.4.0 database");
        }
        
        /* </Pre-1.4.0 Database Import> */
    }
    
    /* </Migrations> */
}