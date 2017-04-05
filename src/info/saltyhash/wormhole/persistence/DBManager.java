package info.saltyhash.wormhole.persistence;

import java.io.File;
import java.sql.*;
import java.util.logging.Logger;

/**
 * Manages the database.
 *
 * Usage:
 * DBManager.logger = ...;
 * DBManager.dbFile = new File("/path/to/database.sqlite");
 * Connection conn  = DBManager.getConnection();
 */
public final class DBManager {
    private DBManager() {}
    
    private static Connection connection = null;
    /** Returns a connection to the database, reusing it if possible.
     * Setting the dbFile will close any existing connection.
     *
     * @return Database connection, or null on error.
     */
    public static Connection getConnection() {
        if (dbFile == null) return null;
        
        try {
            if (connection == null || connection.isClosed()) {
                Class.forName("org.sqlite.JDBC");
                connection = DriverManager.getConnection(
                        "jdbc:sqlite:"+dbFile.getAbsolutePath());
                connection.createStatement().execute("PRAGMA foreign_keys=ON");
            }
        } catch (ClassNotFoundException | SQLException e) {
            connection = null;
        }
        
        return connection;
    }
    /** Closes the database connection if it exists and is open. */
    public static void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                connection = null;
            }
        } catch (SQLException e) {
            logWarning("Failed to close database connection.");
            logWarning(e.toString());
        }
    }
    
    /** The database file.  Setting this will close any existing connection. */
    public static File dbFile = null;
    public static void setDbFile(File newDbFile) {
        System.out.print("Database file set.");
        dbFile = newDbFile;
        try {
            if (connection != null && !connection.isClosed())
                connection.close();
        } catch (SQLException e) {
        }
    }
    
    public  static Logger logger = null;
    private static void logInfo(String msg) {
        if (logger != null) logger.info(msg);
    }
    private static void logWarning(String msg) {
        if (logger != null) logger.warning(msg);
    }
    private static void logSevere(String msg) {
        if (logger != null) logger.severe(msg);
    }
    
    /** @return The current database version, or -1 on error. */
    public static int getDatabaseVersion() {
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
    
    /** Sets the database version.  If an error occurs, it is logged.
     *
     * @param  version
     * @return true on success; false on error.
     */
    private static boolean setDatabaseVersion(int version) {
        try (Statement s = getConnection().createStatement()) {
            s.executeUpdate("DELETE FROM schema_version;");
            s.executeUpdate("INSERT INTO schema_version\n"+
                    "(`version`) VALUES ("+version+");"
            );
        } catch (SQLException e) {
            logSevere("Failed to set database version:");
            logSevere(e.toString());
            return false;
        }
        
        return true;    // Success
    }
    
    /** Migrates the database to the latest version.
     * @return true on success; false on failure.
     */
    public static boolean migrate() {
        switch (getDatabaseVersion()) {
            case -1: if (!migration0()) return false;
            default: break;
        }
        
        return true;    // Success
    }
    
    /* Migrations */
    
    private static boolean migration0() {
        logInfo("Creating database v0...");
        
        // Create table 'schema_version'
        try (Statement s = getConnection().createStatement()) {
            s.execute("CREATE TABLE schema_version (\n" +
                    "  `version` INTEGER);"
            );
        } catch (SQLException e) {
            logSevere("Failed to create table schema_version:");
            logSevere(e.toString());
            return false;
        }
        
        // Create table 'players'
        try (Statement s = getConnection().createStatement()) {
            s.execute("CREATE TABLE players (\n" +
                    "  `uuid`     CHAR(36) PRIMARY KEY,\n" +
                    "  `username` VARCHAR(16);"
            );
        } catch (SQLException e) {
            logSevere("Failed to create table 'players':");
            logSevere(e.toString());
            return false;
        }
        
        // Create table 'jumps'
        try (Statement s = getConnection().createStatement()) {
            s.execute("CREATE TABLE jumps (\n" +
                    "  `id`          INTEGER PRIMARY KEY,\n" +
                    "  `player_uuid` CHAR(36) REFERENCES players(`uuid`)\n" +
                    "                ON DELETE CASCADE ON UPDATE CASCADE,\n" +
                    "  `name`        TEXT,\n" +
                    "  `world_uuid`  CHAR(36),\n" +
                    "  `x` REAL, `y` REAL, `z` REAL, `yaw` REAL,\n" +
                    "  UNIQUE (`player_uuid`, `name`);"
            );
        } catch (SQLException e) {
            logSevere("Failed to create table 'jumps':");
            logSevere(e.toString());
            return false;
        }
    
        // Create table 'signs'
        try (Statement s = getConnection().createStatement()) {
            s.execute("CREATE TABLE signs (\n" +
                    "  `world_uuid` CHAR(36),\n" +
                    "  `x` INTEGER, `y` INTEGER, `z` INTEGER,\n" +
                    "  `jump_id` INTEGER REFERENCES jumps(`id`)\n" +
                    "            ON DELETE CASCADE ON UPDATE CASCADE,\n" +
                    "  PRIMARY KEY (`world_uuid`, `x`, `y`, `z`);"
            );
        } catch (SQLException e) {
            logSevere("Failed to create table 'signs':");
            logSevere(e.toString());
            return false;
        }
        
        if (!setDatabaseVersion(0)) return false;
        
        logInfo("Done.");
        return true;
    }
}