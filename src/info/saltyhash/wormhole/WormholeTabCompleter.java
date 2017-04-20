package info.saltyhash.wormhole;

import info.saltyhash.wormhole.persistence.JumpRecord;
import info.saltyhash.wormhole.persistence.PlayerRecord;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Handles tab completion for the Wormhole plugin commands. */
class WormholeTabCompleter implements TabCompleter {
    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        // Return if sender is not a player
        if (!(sender instanceof Player)) return null;
        
        // Return if command is not "/wormhole ..."
        if (!command.getName().equalsIgnoreCase("wormhole")) return null;
        
        // Get subcommand and args
        String subcommand;
        try {
            // Get action
            subcommand = args[0].toLowerCase();
            // Cut off first arg (it was used to get action)
            args = Arrays.copyOfRange(args, 1, args.length);
        } catch (ArrayIndexOutOfBoundsException e) {
            return null;
        }
        
        final List<String> subcommands = Arrays.asList("reload", "version", "add", "back",
                "cost", "delete", "jump", "list", "rename", "replace", "search", "set", "unset");
        // Subcommand is not in the list of subcommands?
        if (!subcommands.contains(subcommand)) {
            // Return list of subcommands starting with subcommand
            List<String> autocomplete = new ArrayList<>();
            for (String possibleSubcommand : subcommands) {
                if (possibleSubcommand.startsWith(subcommand))
                    autocomplete.add(possibleSubcommand);
            }
            return autocomplete;
        }
        
        // Return if subcommand does not support autocomplete
        final List<String> autocompleteCommands = Arrays.asList(
                "delete", "jump", "rename", "replace", "set");
        if (!autocompleteCommands.contains(subcommand)) return null;
        
        // Get player and jump info from args
        Player player = (Player) sender;
        String[] jumpInfo = WormholeCommandHandler.getJumpInfoFromArgs(player, args);
        // Parse error?
        if (jumpInfo == null) return null;
        String playerName = jumpInfo[0];
        String jumpName   = jumpInfo[1];
        
        // Check permissions
        // Public jump?
        if (playerName == null) {
            if (!player.hasPermission("wormhole." + subcommand + ".public"))
                return null;
        }
        // Jump belonging to the player?
        else if (playerName.equalsIgnoreCase(player.getName())) {
            if (!player.hasPermission("wormhole." + subcommand + ".private"))
                return null;
        }
        // Jump belonging to another player?
        else {
            if (!player.hasPermission("wormhole." + subcommand + ".other"))
                return null;
        }
        
        // Get the player ID for the given player name
        Integer playerId = null;    // Assume public
        if (playerName != null) {
            // Get player record for jump
            PlayerRecord playerRecord = PlayerRecord.load(playerName);
            // Player does not exist?
            if (playerRecord == null) return null;
            // Set player ID
            playerId = playerRecord.getId();
        }
        
        // Get the jump records that begin with the given jump name
        List<JumpRecord> jumpRecords = JumpRecord.loadWhereNameBeginsWith(playerId, jumpName);
        if (jumpRecords == null) return null;
        
        // Build and return a list of jump names
        List<String> jumpNames = new ArrayList<>();
        for (JumpRecord jumpRecord : jumpRecords) jumpNames.add(jumpRecord.name);
        return jumpNames;
    }
}