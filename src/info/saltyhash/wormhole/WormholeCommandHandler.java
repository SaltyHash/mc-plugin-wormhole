package info.saltyhash.wormhole;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import info.saltyhash.wormhole.persistence.JumpRecord;
import info.saltyhash.wormhole.persistence.PlayerRecord;
import info.saltyhash.wormhole.persistence.SignRecord;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginDescriptionFile;

/** Handles commands given to Wormhole. */
class WormholeCommandHandler implements CommandExecutor {
    private final Wormhole wormhole;
    
    WormholeCommandHandler(Wormhole wormhole) {
        this.wormhole = wormhole;
    }
    
    /**
     * Handles the "add" command.
     * Usage: /worm add [player | public] <jump name>
     */
    private void commandAdd(CommandSender sender, String[] args) {
        final String ERROR_MSG_PREFIX =
                ChatColor.DARK_RED + "Failed to add jump; " + ChatColor.RESET;
        
        // Make sure sender is a player
        if (!(sender instanceof Player)) {
            sender.sendMessage("Must be a player");
            return;
        }
        Player player = (Player)sender;
        
        // Player is in blacklisted world?
        if (wormhole.notifyPlayerIfWorldIsBlacklisted(player, player.getWorld().getName())) return;
        
        // Get jump info from args
        String[] jumpInfo = getJumpInfoFromArgs(player, args);
        // Parse error?
        if (jumpInfo == null) {
            player.sendMessage(getCommandUsage("worm add"));
            return;
        }
        String playerName = jumpInfo[0];
        String jumpName   = jumpInfo[1];
        
        // Check permissions
        // Public jump?
        if (playerName == null) {
            if (!player.hasPermission("wormhole.add.public")) {
                player.sendMessage(ChatColor.DARK_RED+"You cannot add public jumps");
                return;
            }
        }
        // Jump belonging to the player?
        else if (playerName.equalsIgnoreCase(player.getName())) {
            if (!player.hasPermission("wormhole.add.private")) {
                player.sendMessage(ChatColor.DARK_RED+"You cannot add your own jumps");
                return;
            }
        }
        // Jump belonging to another player?
        else {
            if (!player.hasPermission("wormhole.add.other")) {
                player.sendMessage(ChatColor.DARK_RED+"You cannot add jumps for other players");
                return;
            }
        }
        
        // Make sure player can afford this action
        if (!player.hasPermission("wormhole.free")
                && !EconManager.hasBalance(player, "add")) {
            player.sendMessage(ChatColor.DARK_RED+"You cannot afford to add new jumps");
            return;
        }
        
        // Get player ID for jump
        Integer playerId = null;    // Assume public
        // Jump is private?
        if (playerName != null) {
            // Get player record for jump
            PlayerRecord playerRecord = PlayerRecord.load(playerName);
            // Player does not exist?
            if (playerRecord == null) {
                player.sendMessage(ERROR_MSG_PREFIX +
                        "player '" + playerName + "' does not exist");
                return;
            }
            // Set player ID
            playerId = playerRecord.getId();
        }
        
        // Check if jump record already exists
        JumpRecord jumpRecord = JumpRecord.load(playerId, jumpName);
        if (jumpRecord != null) {
            player.sendMessage(ERROR_MSG_PREFIX +
                    "jump " + jumpRecord.getDescription(player) + " already exists");
            return;
        }
        
        // Create new jump record
        jumpRecord = new JumpRecord(playerId, jumpName, player.getLocation());
        
        // Save jump record; failed (unknown reason)?
        if (!jumpRecord.save()) {
            player.sendMessage(ERROR_MSG_PREFIX + "internal error");
            wormhole.getLogger().warning("Player '" + player.getName() + "' failed to add jump " +
                    jumpRecord.getDescription() + "; failed to save jump record");
            return;
        }
        
        player.sendMessage(ChatColor.DARK_GREEN + "Added" + ChatColor.RESET +
                " jump " + jumpRecord.getDescription(player));
        
        // Charge player
        if (!player.hasPermission("wormhole.free")) EconManager.charge(player, "add");
    }
    
    /**
     * Handles the "back" command.
     * Usage: /worm back
     */
    private void commandBack(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Must be a player");
            return;
        }
        Player player = (Player)sender;
        
        // Player is in a blacklisted world?
        if (wormhole.notifyPlayerIfWorldIsBlacklisted(player, player.getWorld().getName())) return;
        
        // Check permissions
        if (!player.hasPermission("wormhole.back")) {
            player.sendMessage(ChatColor.DARK_RED+
                    "You cannot jump back to your previous location");
            return;
        }
        
        // Make sure player can afford this action
        if (!player.hasPermission("wormhole.free")
                && !EconManager.hasBalance(player, "back")) {
            player.sendMessage(ChatColor.DARK_RED+
                    "You cannot afford to jump back to your previous location");
            return;
        }
        
        // Get player's previous jump location
        Location previousLocation = PlayerManager.getPreviousLocation(player);
        if (previousLocation == null) {
            player.sendMessage(ChatColor.DARK_RED+"No previous location to jump to");
            return;
        }
        
        // Create a fake JumpRecord to use its teleport method
        JumpRecord prevJumpRecord = new JumpRecord();
        prevJumpRecord.setLocation(previousLocation);
        
        // Get the player's current location as the new previous location
        Location newPreviousLocation = player.getLocation();
        
        // Teleport the player to the previous location; failed?
        if (!prevJumpRecord.teleportPlayer(player)) {
            player.sendMessage(ChatColor.DARK_RED+
                    "Failed to jump to previous location; internal error");
            wormhole.getLogger().warning("Failed to jump player '"+player.getName()+
                    "' to previous location; unknown reason");
            return;
        }
        
        // Play teleport effect
        wormhole.playTeleportEffect(newPreviousLocation);
        wormhole.playTeleportEffect(player.getLocation());
        
        // Save the new previous location
        PlayerManager.setPreviousLocation(player, newPreviousLocation);
    
        // Charge player
        if (!player.hasPermission("wormhole.free")) EconManager.charge(player, "back");
    }
    
    /**
     * Handles the "cost" command.
     * Usage: /worm cost
     */
    private void commandCost(CommandSender sender) {
        if (!EconManager.isEnabled()) {
            sender.sendMessage(ChatColor.DARK_RED+"Economy system does not exist");
            return;
        }
        
        FileConfiguration config = wormhole.getConfig();
        String add     = EconManager.econ.format(config.getDouble("cost.add"));
        String back    = EconManager.econ.format(config.getDouble("cost.back"));
        String del     = EconManager.econ.format(config.getDouble("cost.del"));
        String jump    = EconManager.econ.format(config.getDouble("cost.jump"));
        String rename  = EconManager.econ.format(config.getDouble("cost.rename"));
        String replace = EconManager.econ.format(config.getDouble("cost.replace"));
        String set     = EconManager.econ.format(config.getDouble("cost.set"));
        String unset   = EconManager.econ.format(config.getDouble("cost.unset"));
        String use     = EconManager.econ.format(config.getDouble("cost.use"));
        
        sender.sendMessage(String.format(
            "%sWormhole Costs%s\n"+
            "- add:  %s\n"+
            "- back:  %s\n"+
            "- del:  %s\n"+
            "- jump:  %s\n"+
            "- rename:  %s\n"+
            "- replace:  %s\n"+
            "- set:  %s\n"+
            "- unset:  %s\n"+
            "- use:  %s",
            ChatColor.DARK_PURPLE, ChatColor.RESET,
            add, back, del, jump, rename, replace, set, unset, use));
    }
    
    /**
     * Handles the "delete" command.
     * Usage: /worm delete [player | public] <jump name>
     */
    private void commandDelete(CommandSender sender, String[] args) {
        final String ERROR_MSG_PREFIX =
                ChatColor.DARK_RED + "Failed to delete jump; " + ChatColor.RESET;
        
        // Make sure sender is a player
        if (!(sender instanceof Player)) {
            sender.sendMessage("Must be a player");
            return;
        }
        Player player = (Player)sender;
        
        // Get jump info from args
        String[] jumpInfo = getJumpInfoFromArgs(player, args);
        // Parse error?
        if (jumpInfo == null) {
            player.sendMessage(getCommandUsage("worm delete"));
            return;
        }
        String playerName = jumpInfo[0];
        String jumpName   = jumpInfo[1];
        
        // Check permissions
        // Public jump?
        if (playerName == null) {
            if (!player.hasPermission("wormhole.del.public")) {
                player.sendMessage(ChatColor.DARK_RED+"You cannot delete public jumps");
                return;
            }
        }
        // Jump belongs to the player?
        else if (playerName.equalsIgnoreCase(player.getName())) {
            if (!player.hasPermission("wormhole.del.private")) {
                player.sendMessage(ChatColor.DARK_RED+"You cannot delete your jumps");
                return;
            }
        }
        // Jump belongs to other player?
        else {
            if (!player.hasPermission("wormhole.del.other")) {
                player.sendMessage(ChatColor.DARK_RED+
                    "You cannot delete other players' jumps");
                return;
            }
        }
        
        // Make sure player can afford this action
        if (!player.hasPermission("wormhole.free")
                && !EconManager.hasBalance(player, "del")) {
            player.sendMessage(ChatColor.DARK_RED+"You cannot afford to delete jumps");
            return;
        }
    
        // Get player ID for jump
        Integer playerId = null;    // Assume public
        // Jump is private?
        if (playerName != null) {
            // Get player record for jump
            PlayerRecord playerRecord = PlayerRecord.load(playerName);
            // Player does not exist?
            if (playerRecord == null) {
                player.sendMessage(ERROR_MSG_PREFIX +
                        "player '" + playerName + "' does not exist");
                return;
            }
            // Set player ID
            playerId = playerRecord.getId();
        }
        
        // Retrieve the jump record
        JumpRecord jumpRecord = JumpRecord.load(playerId, jumpName);
        if (jumpRecord == null) {
            player.sendMessage(ERROR_MSG_PREFIX + "jump does not exist");
            return;
        }
        
        // Delete the jump; failed?
        if (!jumpRecord.delete()) {
            player.sendMessage(ERROR_MSG_PREFIX + "unknown error");
            wormhole.getLogger().warning("Player '" + player.getName() +
                    "' failed to delete jump " + jumpRecord.getDescription() + "; unknown reason.");
            return;
        }
        
        player.sendMessage(ChatColor.RED + "Deleted" + ChatColor.RESET +
            " jump " + jumpRecord.getDescription(player));
        
        // Charge player
        if (!player.hasPermission("wormhole.free")) EconManager.charge(player, "del");
    }
    
    /**
     * Handles the "jump" command.
     * Usage: /worm jump [player | public] <jump name>
     */
    private void commandJump(CommandSender sender, String[] args) {
        final String ERROR_MSG_PREFIX =
                ChatColor.DARK_RED + "Failed to jump; " + ChatColor.RESET;
        
        // Make sure sender is a player
        if (!(sender instanceof Player)) {
            sender.sendMessage("Must be a player");
            return;
        }
        Player player = (Player)sender;
        
        // Player is in blacklisted world?
        if (wormhole.notifyPlayerIfWorldIsBlacklisted(player, player.getWorld().getName())) return;
        
        // Get jump info from args
        String[] jumpInfo = getJumpInfoFromArgs(player, args);
        // Parse error?
        if (jumpInfo == null) {
            player.sendMessage(getCommandUsage("worm jump"));
            return;
        }
        String playerName = jumpInfo[0];
        String jumpName   = jumpInfo[1];
        
        // Check permissions
        // Jump is public?
        if (playerName == null) {
            if (!player.hasPermission("wormhole.jump.public")) {
                player.sendMessage(ChatColor.DARK_RED+"You cannot jump directly to public jumps");
                return;
            }
        }
        // Jump belongs to the player?
        else if (playerName.equalsIgnoreCase(player.getName())) {
            if (!player.hasPermission("wormhole.jump.private")) {
                player.sendMessage(ChatColor.DARK_RED+
                    "You cannot jump directly to your jumps");
                return;
            }
        }
        // Jump belongs to other player?
        else {
            if (!player.hasPermission("wormhole.jump.other")) {
                player.sendMessage(ChatColor.DARK_RED+
                    "You cannot jump directly to jumps that belong to other players");
                return;
            }
        }
        
        // Make sure player can afford this action
        if (!player.hasPermission("wormhole.free")
                && !EconManager.hasBalance(player, "jump")) {
            player.sendMessage(ChatColor.DARK_RED+
                    "You cannot afford to jump directly to a jump");
            return;
        }
    
        // Get player ID for jump
        Integer playerId = null;    // Assume public
        // Jump is private?
        if (playerName != null) {
            // Get player record for jump
            PlayerRecord playerRecord = PlayerRecord.load(playerName);
            // Player does not exist?
            if (playerRecord == null) {
                player.sendMessage(ERROR_MSG_PREFIX +
                        "player '" + playerName + "' does not exist");
                return;
            }
            // Set player ID
            playerId = playerRecord.getId();
        }
        
        // Get the jump record
        JumpRecord jumpRecord = JumpRecord.load(playerId, jumpName);
        // Jump does not exist?
        if (jumpRecord == null) {
            player.sendMessage(ERROR_MSG_PREFIX + "jump " +
                    JumpRecord.getDescription(player, playerName, jumpName) + " does not exist");
            
            // A public jump exists with the same name?  Notify the player.
            if (JumpRecord.load(null, jumpName) != null)
                player.sendMessage("Did you mean \"public " + jumpName + "\"?");
            return;
        }
        
        // Jump destination is in a blacklisted world?
        if (wormhole.notifyPlayerIfWorldIsBlacklisted(player, jumpRecord.getWorld().getName())) return;
        
        Location from = player.getLocation();
        
        // Teleport the player; failed?
        if (!jumpRecord.teleportPlayer(player)) {
            player.sendMessage(ERROR_MSG_PREFIX + "unknown reason");
            wormhole.getLogger().warning("Player '" + player.getName() + "' failed to jump to "+
                    jumpRecord.getDescription() + "; unknown reason");
            return;
        }
        
        // Play teleport effects
        wormhole.playTeleportEffect(from);
        wormhole.playTeleportEffect(player.getLocation());
        
        // Store previous location
        PlayerManager.setPreviousLocation(player, from);
        
        // Charge player
        if (!player.hasPermission("wormhole.free")) EconManager.charge(player, "jump");
    }
    
    /**
     * Handles the "list" command.
     * Usage: /worm list [player | public] [page]
     */
    private void commandList(CommandSender sender, String[] args) {
        final String ERROR_MSG_PREFIX =
                ChatColor.DARK_RED + "Failed to list jumps; " + ChatColor.RESET;
        final int pageSize = 9;
        int page;
        String playerName;
        
        // Parse arguments
        try {
            // First arg should be empty to imply first page,
            // "public", a player name, or a page number.
            
            // No args
            // Sender's jump list and page 1 implied
            if (args.length == 0) {
                playerName = sender.getName();
                page = 1;
            }
            
            // 1 arg
            // 1st arg alone either "public", player name, or page number
            else if (args.length == 1) {
                // Get page from 1st arg.  Sender jump list implied.
                try {
                    playerName = sender.getName();
                    page = Integer.parseInt(args[0]);
                }
                // 1st arg isn't a page number.  Page implied to be 1.
                catch (NumberFormatException e) {
                    page = 1;
                    // Public jump list
                    if (args[0].equalsIgnoreCase("public"))
                        playerName = null;
                    // Player jump list
                    else
                        playerName = args[0];
                }
            }
            
            // >=2 args
            // 1st arg is either "public" or player name
            // 2nd arg is page number
            else {
                // Public jump list
                if (args[0].equalsIgnoreCase("public"))
                    playerName = null;
                // Player jump list
                else
                    playerName = args[0];
                page = Integer.parseInt(args[1]);
            }
        }
        catch (ArrayIndexOutOfBoundsException | NumberFormatException e) {
            // Display command usage
            sender.sendMessage(getCommandUsage("worm list"));
            return;
        }
        if (page < 1) page = 1;
        
        // Check permissions
        // Public jump?
        if (playerName == null) {
            if (!sender.hasPermission("wormhole.list.public")) {
                sender.sendMessage(ChatColor.DARK_RED+"You cannot list public jumps");
                return;
            }
        }
        // Jump belonging to the player?
        else if (playerName.equals(sender.getName())) {
            if (!sender.hasPermission("wormhole.list.private")) {
                sender.sendMessage(ChatColor.DARK_RED+"You cannot list your jumps");
                return;
            }
        }
        // Jump belonging to another player?
        else {
            if (!sender.hasPermission("wormhole.list.other")) {
                sender.sendMessage(ChatColor.DARK_RED+
                    "You cannot list jumps belonging to other players");
                return;
            }
        }
    
        // Get player ID for jump
        Integer playerId = null;    // Assume public
        // Jump is private?
        if (playerName != null) {
            // Get player record for jump
            PlayerRecord playerRecord = PlayerRecord.load(playerName);
            // Player does not exist?
            if (playerRecord == null) {
                sender.sendMessage(ERROR_MSG_PREFIX +
                        "player '" + playerName + "' does not exist");
                return;
            }
            // Set player ID
            playerId   = playerRecord.getId();
            playerName = playerRecord.username;
        }
        
        // Get list of jump records
        List<JumpRecord> jumpRecords = JumpRecord.loadWithPlayerId(playerId);
        // Unknown error?
        if (jumpRecords == null) {
            sender.sendMessage(ERROR_MSG_PREFIX + "unknown reason");
            wormhole.getLogger().warning(sender.getName() + " failed to list jumps for player '" +
                    playerName + "'; unknown reason");
            return;
        }
        
        // Player has no jumps?
        if (jumpRecords.isEmpty()) {
            sender.sendMessage(ChatColor.DARK_PURPLE + "No jumps to list");
            return;
        }
        
        // Get number of pages
        int pages = (jumpRecords.size()/pageSize)+((jumpRecords.size()%pageSize) != 0 ? 1 : 0);
        if (page > pages) page = pages;
        
        // Make page list
        try {
            int start = (page-1)*pageSize;
            int end   = start+pageSize;
            if (end > jumpRecords.size()) end = jumpRecords.size();
            jumpRecords = jumpRecords.subList(start, end);
        }
        // Page does not exist?
        catch (IllegalArgumentException e) {
            sender.sendMessage(ChatColor.DARK_RED+"Page "+page+" does not exist");
            return;
        }
        
        // Display list
        StringBuilder msg = new StringBuilder("\n"+ChatColor.DARK_PURPLE);
        if (playerName == null)
            msg.append("Public Jumps").append(ChatColor.RESET);
        else if (playerName.equalsIgnoreCase(sender.getName()))
            msg.append("Your Jumps").append(ChatColor.RESET);
        else
            msg.append("Jumps").append(ChatColor.RESET).append(" for ").append(playerName);
        msg.append(String.format(
                ":  Page %s%d%s/%s%d%s",
                ChatColor.DARK_AQUA, page, ChatColor.RESET,
                ChatColor.DARK_AQUA, pages, ChatColor.RESET));
        for (JumpRecord jumpRecord : jumpRecords) {
            if (jumpRecord == null) continue;
            String worldName = Bukkit.getServer().getWorld(jumpRecord.worldUuid).getName();
            msg.append(String.format(
                    ChatColor.RESET+"\n- %s%s%s:  W:%s%s%s  X:%s%d%s  Y:%s%d%s  Z:%s%d%s",
                    ChatColor.DARK_PURPLE,     jumpRecord.name, ChatColor.RESET,
                    ChatColor.DARK_AQUA,       worldName,       ChatColor.RESET,
                    ChatColor.DARK_AQUA, (int) jumpRecord.x,    ChatColor.RESET,
                    ChatColor.DARK_AQUA, (int) jumpRecord.y,    ChatColor.RESET,
                    ChatColor.DARK_AQUA, (int) jumpRecord.z,    ChatColor.RESET));
        }
        sender.sendMessage(msg.toString());
    }
    
    /**
     * Handles the "reload" command.
     * Usage: /worm reload
     */
    private void commandReload(CommandSender sender) {
        if (!sender.hasPermission("wormhole.reload")) {
            sender.sendMessage(ChatColor.DARK_RED+"You cannot reload the Wormhole config");
            return;
        }
        wormhole.reloadConfig();
        sender.sendMessage(ChatColor.DARK_GREEN+"Wormhole config reloaded");
        wormhole.getLogger().info("Config reloaded by "+sender.getName());
    }
    
    /**
     * Handles the "rename" command.
     * Usage:  /worm rename [player | public] <old name> <new name>
     */
    private void commandRename(CommandSender sender, String[] args) {
        final String ERROR_MSG_PREFIX =
                ChatColor.DARK_RED + "Failed to rename jump; " + ChatColor.RESET;
        
        // Make sure sender is a player
        if (!(sender instanceof Player)) {
            sender.sendMessage("Must be a player");
            return;
        }
        Player player = (Player)sender;
        
        // Get new jump name from args
        String newJumpName;
        try {
            // Get new name from last arg
            newJumpName = args[args.length-1];
            // Remove the last index from args for getJumpInfoFromArgs method
            args = Arrays.copyOfRange(args, 0, args.length-1);
        }
        catch (ArrayIndexOutOfBoundsException e) {
            player.sendMessage(getCommandUsage("worm rename"));
            return;
        }
        if (newJumpName == null) {
            player.sendMessage(getCommandUsage("worm rename"));
            return;
        }
        
        // Get existing jump info from args
        String[] jumpInfo = getJumpInfoFromArgs(player, args);
        // Parse error?
        if (jumpInfo == null) {
            player.sendMessage(getCommandUsage("worm rename"));
            return;
        }
        String playerName  = jumpInfo[0];
        String oldJumpName = jumpInfo[1];
        
        // Check permissions
        // Jump is public?
        if (playerName == null) {
            if (!player.hasPermission("wormhole.rename.public")) {
                player.sendMessage(ChatColor.DARK_RED+"You cannot rename public jumps");
                return;
            }
        }
        // Jump belongs to the player?
        else if (playerName.equalsIgnoreCase(player.getName())) {
            if (!player.hasPermission("wormhole.rename.private")) {
                player.sendMessage(ChatColor.DARK_RED+"You cannot rename your jumps");
                return;
            }
        }
        // Jump belongs to other player?
        else {
            if (!player.hasPermission("wormhole.rename.other")) {
                player.sendMessage(ChatColor.DARK_RED+
                    "You cannot rename jumps belonging to other players");
                return;
            }
        }
    
        // Make sure player can afford this action
        if (!player.hasPermission("wormhole.free")
                && !EconManager.hasBalance(player, "rename")) {
            player.sendMessage(ChatColor.DARK_RED+"You cannot afford to rename jumps");
            return;
        }
    
        // Get player ID for jump
        Integer playerId = null;    // Assume public
        // Jump is private?
        if (playerName != null) {
            // Get player record for jump
            PlayerRecord playerRecord = PlayerRecord.load(playerName);
            // Player does not exist?
            if (playerRecord == null) {
                player.sendMessage(ERROR_MSG_PREFIX +
                        "player '" + playerName + "' does not exist");
                return;
            }
            // Set player ID
            playerId = playerRecord.getId();
        }
        
        // Try to get a jump record matching the new name
        JumpRecord jumpRecord = JumpRecord.load(playerId, newJumpName);
        // Jump with new name already exists?
        if (jumpRecord != null) {
            player.sendMessage(ERROR_MSG_PREFIX + "a jump named '" + newJumpName +
                    "' already exists");
            return;
        }
        
        // Get the jump record with the old name
        jumpRecord = JumpRecord.load(playerId, oldJumpName);
        // Jump DNE?
        if (jumpRecord == null) {
            player.sendMessage(ERROR_MSG_PREFIX + "jump '" + oldJumpName + "' does not exist");
            return;
        }
        
        // Save the new jump name
        jumpRecord.name = newJumpName;
        // Error?
        if (!jumpRecord.save()) {
            player.sendMessage(ERROR_MSG_PREFIX + "unknown reason");
            wormhole.getLogger().warning("Player '" + player.getName() +
                    "' failed to rename jump '" + oldJumpName + "' to '" + newJumpName +
                    "'; unknown reason");
            return;
        }
        
        player.sendMessage(String.format(
            "%sRenamed%s jump '%s' to %s",
            ChatColor.DARK_GREEN, ChatColor.RESET,
            oldJumpName, jumpRecord.getDescription(player)));
        
        // Charge player
        if (!player.hasPermission("wormhole.free")) EconManager.charge(player, "rename");
    }
    
    /**
     * Handles the "replace" command.
     * Usage:  /worm replace [player | public] <jump name>
     */
    private void commandReplace(CommandSender sender, String[] args) {
        final String ERROR_MSG_PREFIX =
                ChatColor.DARK_RED + "Failed to replace jump; " + ChatColor.RESET;
        
        // Make sure sender is a player
        if (!(sender instanceof Player)) {
            sender.sendMessage("Must be a player");
            return;
        }
        Player player = (Player)sender;
        
        // Player is in blacklisted world?
        if (wormhole.notifyPlayerIfWorldIsBlacklisted(player, player.getWorld().getName())) return;
        
        // Get jump info from args
        String[] jumpInfo = getJumpInfoFromArgs(player, args);
        // Parse error?
        if (jumpInfo == null) {
            player.sendMessage(getCommandUsage("worm replace"));
            return;
        }
        String playerName = jumpInfo[0];
        String jumpName   = jumpInfo[1];
        
        // Check permissions
        // Jump is public?
        if (playerName == null) {
            if (!player.hasPermission("wormhole.replace.public")) {
                player.sendMessage(ChatColor.DARK_RED+"You cannot replace public jumps");
                return;
            }
        }
        // Jump belongs to the player?
        else if (playerName.equalsIgnoreCase(player.getName())) {
            if (!player.hasPermission("wormhole.replace.private")) {
                player.sendMessage(ChatColor.DARK_RED+"You cannot replace your jumps");
                return;
            }
        }
        // Jump belongs to another player?
        else {
            if (!player.hasPermission("wormhole.replace.other")) {
                player.sendMessage(ChatColor.DARK_RED+
                    "You cannot replace jumps that belong to other players");
                return;
            }
        }
        
        // Make sure player can afford this action
        if (!player.hasPermission("wormhole.free")
                && !EconManager.hasBalance(player, "replace")) {
            player.sendMessage(ChatColor.DARK_RED+"You cannot afford to replace jumps");
            return;
        }
    
        // Get player ID for jump
        Integer playerId = null;    // Assume public
        // Jump is private?
        if (playerName != null) {
            // Get player record for jump
            PlayerRecord playerRecord = PlayerRecord.load(playerName);
            // Player does not exist?
            if (playerRecord == null) {
                player.sendMessage(ERROR_MSG_PREFIX +
                        "player '" + playerName + "' does not exist");
                return;
            }
            // Set player ID
            playerId = playerRecord.getId();
        }
        
        // Get the jump record
        JumpRecord jumpRecord = JumpRecord.load(playerId, jumpName);
        // Jump does not exist?
        if (jumpRecord == null) {
            player.sendMessage(ERROR_MSG_PREFIX + "jump " +
                    JumpRecord.getDescription(player, playerName, jumpName) + " does not exist");
            return;
        }
        
        // Set the location of the jump to the player's current location
        jumpRecord.setLocation(player.getLocation());
        
        // Save jump record; failed?
        if (!jumpRecord.save()) {
            player.sendMessage(ERROR_MSG_PREFIX + "internal error");
            wormhole.getLogger().warning("Player '" + player.getName() +
                    "' failed to replace jump " + jumpRecord.getDescription() +
                    "; failed to save jump record");
            return;
        }
        
        player.sendMessage(ChatColor.DARK_GREEN+"Replaced"+ChatColor.RESET+" jump "+
            jumpRecord.getDescription(player));
        
        // Charge player
        if (!player.hasPermission("wormhole.free")) EconManager.charge(player, "replace");
    }
    
    /**
     * Handles the "search" command.
     * Usage:  /worm search [player | public] <jump name>
     */
    private void commandSearch(CommandSender sender, String[] args) {
        final String ERROR_MSG_PREFIX =
                ChatColor.DARK_RED+"Failed to search for jump; " + ChatColor.RESET;
        
        // Make sure sender is a player
        if (!(sender instanceof Player)) {
            sender.sendMessage("Must be a player");
            return;
        }
        Player player = (Player)sender;
        // Get jump info from args
        String[] jumpInfo = getJumpInfoFromArgs(player, args);
        // Parse error?
        if (jumpInfo == null) {
            player.sendMessage(getCommandUsage("worm search"));
            return;
        }
        String playerName = jumpInfo[0];
        String jumpName   = jumpInfo[1];
        
        // Check permissions
        // Jump is public?
        if (playerName == null) {
            if (!player.hasPermission("wormhole.list.public")) {
                player.sendMessage(ChatColor.DARK_RED+"You cannot search public jumps");
                return;
            }
        }
        // Jump belongs to the player?
        else if (playerName.equalsIgnoreCase(player.getName())) {
            if (!player.hasPermission("wormhole.list.private")) {
                player.sendMessage(ChatColor.DARK_RED+"You cannot search your jumps");
                return;
            }
        }
        // Jump belongs to other player?
        else {
            if (!player.hasPermission("wormhole.list.other")) {
                player.sendMessage(ChatColor.DARK_RED+
                        "You cannot search jumps that belong to other players");
                return;
            }
        }
        
        // Get player ID for jump
        Integer playerId = null;    // Assume public
        // Jump is private?
        if (playerName != null) {
            // Get player record for jump
            PlayerRecord playerRecord = PlayerRecord.load(playerName);
            // Player does not exist?
            if (playerRecord == null) {
                player.sendMessage(ERROR_MSG_PREFIX +
                        "player '" + playerName + "' does not exist");
                return;
            }
            // Set player ID
            playerId = playerRecord.getId();
        }
        
        // Get list of jump records
        List<JumpRecord> jumpRecords = JumpRecord.loadWhereNameLike(playerId, jumpName);
        // Unknown error?
        if (jumpRecords == null) {
            player.sendMessage(ERROR_MSG_PREFIX+"internal error");
            wormhole.getLogger().warning(sender.getName()+" failed to search jumps for player '"+
                    playerName+"; unknown reason");
            return;
        }
        
        // Search results empty?
        if (jumpRecords.isEmpty()) {
            player.sendMessage(ChatColor.DARK_PURPLE + "No jumps match your search" +
                    ChatColor.RESET + " for " +
                    JumpRecord.getDescription(player, playerName, jumpName));
            return;
        }
        
        // Display search results
        StringBuilder msg = new StringBuilder(String.format(
                "%sSearch Results%s for %s:",
                ChatColor.DARK_PURPLE, ChatColor.RESET,
                JumpRecord.getDescription(player, playerName, jumpName)));
        for (JumpRecord jumpRecord : jumpRecords) {
            if (jumpRecord == null) continue;
            String worldName = Bukkit.getServer().getWorld(jumpRecord.worldUuid).getName();
            msg.append(String.format(
                    ChatColor.RESET+"\n- %s%s%s:  W:%s%s%s  X:%s%d%s  Y:%s%d%s  Z:%s%d%s",
                    ChatColor.DARK_PURPLE,     jumpRecord.name, ChatColor.RESET,
                    ChatColor.DARK_AQUA,       worldName,       ChatColor.RESET,
                    ChatColor.DARK_AQUA, (int) jumpRecord.x,    ChatColor.RESET,
                    ChatColor.DARK_AQUA, (int) jumpRecord.y,    ChatColor.RESET,
                    ChatColor.DARK_AQUA, (int) jumpRecord.z,    ChatColor.RESET));
        }
        sender.sendMessage(msg.toString());
    }
    
    /**
     * Handles the "set" command.
     * Usage:  /worm set [player | public] <jump name>
     */
    private void commandSet(CommandSender sender, String[] args) {
        final String ERROR_MSG_PREFIX =
                ChatColor.DARK_RED + "Failed to set sign; " + ChatColor.RESET;
        
        // Make sure sender is a player
        if (!(sender instanceof Player)) {
            sender.sendMessage("Must be a player");
            return;
        }
        Player player = (Player)sender;
        
        // Player is in blacklisted world?
        if (wormhole.notifyPlayerIfWorldIsBlacklisted(player, player.getWorld().getName())) return;
        
        // Get jump info from args
        String[] jumpInfo = getJumpInfoFromArgs(player, args);
        // Parse error?
        if (jumpInfo == null) {
            player.sendMessage(getCommandUsage("worm set"));
            return;
        }
        String playerName = jumpInfo[0];
        String jumpName   = jumpInfo[1];
        
        // Check permissions
        // Jump is public?
        if (playerName == null) {
            if (!player.hasPermission("wormhole.set.public")) {
                player.sendMessage(ChatColor.DARK_RED+"You cannot set signs to public jumps");
                return;
            }
        }
        // Jump belongs to the player?
        else if (playerName.equalsIgnoreCase(player.getName())) {
            if (!player.hasPermission("wormhole.set.private")) {
                player.sendMessage(ChatColor.DARK_RED+"You cannot set signs to your jumps");
                return;
            }
        }
        // Jump belongs to other player?
        else {
            if (!player.hasPermission("wormhole.set.other")) {
                player.sendMessage(ChatColor.DARK_RED+
                    "You cannot set signs to jumps that belong to other players");
                return;
            }
        }
    
        // Make sure player can afford this action
        if (!player.hasPermission("wormhole.free")
                && !EconManager.hasBalance(player, "set")) {
            player.sendMessage(ChatColor.DARK_RED+"You cannot afford to set signs to jumps");
            return;
        }
        
        // Get player ID for jump
        Integer playerId = null;    // Assume public
        // Jump is private?
        if (playerName != null) {
            // Get player record for jump
            PlayerRecord playerRecord = PlayerRecord.load(playerName);
            // Player does not exist?
            if (playerRecord == null) {
                player.sendMessage(ERROR_MSG_PREFIX +
                        "player '" + playerName + "' does not exist");
                return;
            }
            // Set player ID
            playerId = playerRecord.getId();
        }
        
        // Get the jump record
        JumpRecord jumpRecord = JumpRecord.load(playerId, jumpName);
        // Jump does not exist?
        if (jumpRecord == null) {
            player.sendMessage(ERROR_MSG_PREFIX + "jump " +
                    JumpRecord.getDescription(player, playerName, jumpName) + " does not exist");
            return;
        }
        
        // Jump destination is in a blacklisted world?
        if (wormhole.notifyPlayerIfWorldIsBlacklisted(player, jumpRecord.getWorld().getName())) return;
    
        // Get sign block
        Block target = player.getTargetBlock((Set<Material>) null, 4);
        if (target == null || !(target.getState() instanceof Sign)) {
            player.sendMessage(ERROR_MSG_PREFIX + "you must be looking at a sign");
            return;
        }
        Sign sign = (Sign) target.getState();
        
        // Sign is already pointing to a jump?
        if (SignRecord.load(sign) != null) {
            player.sendMessage(ERROR_MSG_PREFIX + "sign is already set.\n" +
                    "Use command \"/worm unset\" to unset the sign.");
            return;
        }
        
        // Create new sign record
        SignRecord signRecord = new SignRecord(sign, jumpRecord.id);
        // Save sign record; failed?
        if (!signRecord.save()) {
            player.sendMessage(ERROR_MSG_PREFIX+"internal error");
            wormhole.getLogger().warning("Player '"+player.getName()+
                    "' failed to save sign record");
            return;
        }
        
        player.sendMessage(ChatColor.DARK_GREEN+"Set sign"+ChatColor.RESET+
            " to jump "+jumpRecord.getDescription(player));
        
        // Charge player
        if (!player.hasPermission("wormhole.free")) EconManager.charge(player, "set");
    }
    
    /**
     * Handles the "unset" command.
     * Usage:  /worm unset
     */
    private void commandUnset(CommandSender sender) {
        final String ERROR_MSG_PREFIX =
                ChatColor.DARK_RED + "Failed to unset sign; " + ChatColor.RESET;
        
        // Make sure sender is a player
        if (!(sender instanceof Player)) {
            sender.sendMessage("Must be a player");
            return;
        }
        Player player = (Player)sender;
        
        // Player is in blacklisted world?
        if (wormhole.notifyPlayerIfWorldIsBlacklisted(player, player.getWorld().getName())) return;
        
        // Get target block
        Block target = player.getTargetBlock((Set<Material>) null, 4);
        // Target is not a sign?
        if (target == null || !(target.getState() instanceof Sign)) {
            player.sendMessage(ERROR_MSG_PREFIX+"you must be looking at a sign");
            return;
        }
        Sign sign = (Sign) target.getState();
        
        // Get sign record
        SignRecord signRecord = SignRecord.load(sign);
        // Sign record does not exist?
        if (signRecord == null) {
            player.sendMessage(ERROR_MSG_PREFIX+"sign is not set to a jump");
            return;
        }
        
        // Get jump record
        JumpRecord jumpRecord = signRecord.getJumpRecord();
        // Jump record DNE?  ==>  orphaned sign record, which should not happen.
        if (jumpRecord == null) {
            player.sendMessage(ERROR_MSG_PREFIX+"sign is not set to a jump");
            wormhole.getLogger().warning("Player '"+player.getName()+
                    "' tried to unset an orphaned sign record, which shouldn't exist; deleting.");
            // Delete orphaned sign record; error?
            if (!signRecord.delete()) {
                wormhole.getLogger().warning("Failed to delete orphaned sign record");
            }
            return;
        }
        
        // Check permissions
        // Jump is public?
        if (jumpRecord.isPublic()) {
            if (!player.hasPermission("wormhole.unset.public")) {
                player.sendMessage(ChatColor.DARK_RED+
                    "You cannot unset signs pointing to public jumps");
                return;
            }
        }
        // Jump belongs to the player?
        //else if (player.getUniqueId().equals(playerRecord.uuid)) {
        else if (jumpRecord.belongsTo(player)) {
            if (!player.hasPermission("wormhole.unset.private")) {
                player.sendMessage(ChatColor.DARK_RED+
                    "You cannot unset signs pointing to your jumps");
                return;
            }
        }
        // Jump belongs to another player?
        else {
            if (!player.hasPermission("wormhole.unset.other")) {
                player.sendMessage(ChatColor.DARK_RED+
                    "You cannot unset signs pointing to jumps that belong to other players");
                return;
            }
        }
    
        // Make sure player can afford this action
        if (!player.hasPermission("wormhole.free")
                && !EconManager.hasBalance(player, "unset")) {
            player.sendMessage(ChatColor.DARK_RED+
                    "You cannot afford to unset signs pointing to jumps");
            return;
        }
        
        // Delete the sign record; error?
        if (!signRecord.delete()) {
            player.sendMessage(ERROR_MSG_PREFIX+"internal error");
            wormhole.getLogger().warning(ChatColor.DARK_RED+"Player '"+player.getName()
                    +"' failed to delete sign record");
            return;
        }
        
        player.sendMessage(ChatColor.RED + "Unset sign" + ChatColor.RESET +
                " pointing to jump " + jumpRecord.getDescription(player));
        
        // Charge player
        if (!player.hasPermission("wormhole.free")) EconManager.charge(player, "unset");
    }
    
    /**
     * Handles the "version" command.
     * Usage: /worm version
     */
    private void commandVersion(CommandSender sender) {
        // Check permissions
        if (!sender.hasPermission("wormhole.version")) {
            sender.sendMessage(ChatColor.DARK_RED+"You cannot view Wormhole version information");
            return;
        }
        
        // Build and send message
        PluginDescriptionFile pdf = wormhole.getDescription();
        List<String> authors = pdf.getAuthors();
        StringBuilder msg = new StringBuilder();
        msg.append(ChatColor.DARK_PURPLE).append(pdf.getFullName()).append(ChatColor.RESET);
        if (authors.size() == 1) {
            msg.append("\nAuthor: ").append(authors.get(0));
        } else if (authors.size() > 1) {
            msg.append("\nAuthors:");
            for (String author : authors)
                msg.append("\n    ").append(author);
        }
        sender.sendMessage(msg.toString());
    }
    
    /** Returns the usage string for the command, or null if DNE. */
    private String getCommandUsage(String command) {
        PluginCommand pluginCommand = wormhole.getCommand(command);
        if (pluginCommand == null) return null;
        return ChatColor.DARK_PURPLE+"Usage:"+ChatColor.RESET+" "+pluginCommand.getUsage();
    }
    
    /**
     * Parses arguments and returns an array containing 0) the player name
     * and 1) the jump name, or null on parse error.
     * Format: [player | public] <jump name>
     * @return [0] the player name (null if public), and [1] the jump name; null on parse error.
     */
    static String[] getJumpInfoFromArgs(Player player, String[] args) {
        String playerName;
        String jumpName;
        
        // Parse arguments
        try {
            // Private jump
            if (args.length == 1) {
                // Check args
                if (args[0].isEmpty()) return null;
                // Player name is current player, jump name in 0th arg
                playerName = player.getName();
                jumpName   = args[0];
            }
            
            // Public jump
            else if (args[0].equalsIgnoreCase("public")) {
                // Check args
                if (args[1].isEmpty()) return null;
                // Player name empty to designate public jump, and jump name in 1st arg
                playerName = null;
                jumpName   = args[1];
            }
            
            // Other player's jump
            else {
                // Check args
                if (args[0].isEmpty() || args[1].isEmpty()) return null;
                // Player name in 0th arg, jump name in 1st arg
                playerName = args[0];
                jumpName   = args[1];
            }
        }
        catch (ArrayIndexOutOfBoundsException e) {
            // Parse error
            return null;
        }
        
        String[] result = new String[2];
        result[0] = playerName;
        result[1] = jumpName;
        return result;
    }
    
    /** Called when a command is issued. */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        // Return if command is not "/wormhole ..."
        if (!command.getName().equalsIgnoreCase("wormhole")) return false;
        
        // Get sub-command and args
        String subcommand;
        try {
            // Get action
            subcommand = args[0].toLowerCase();
            // Cut off first arg (it was used to get action)
            args = Arrays.copyOfRange(args, 1, args.length);
        } catch (ArrayIndexOutOfBoundsException e) {
            return false;
        }
        
        // Give action to appropriate handler function
        switch (subcommand) {
            case "add"    : commandAdd(sender, args);     break;
            case "back"   : commandBack(sender);          break;
            case "cost"   : commandCost(sender);          break;
            case "delete" : commandDelete(sender, args);  break;
            case "jump"   : commandJump(sender, args);    break;
            case "list"   : commandList(sender, args);    break;
            case "reload" : commandReload(sender);        break;
            case "rename" : commandRename(sender, args);  break;
            case "replace": commandReplace(sender, args); break;
            case "search" : commandSearch(sender, args);  break;
            case "set"    : commandSet(sender, args);     break;
            case "unset"  : commandUnset(sender);         break;
            case "version": commandVersion(sender);       break;
            default:
                sender.sendMessage(ChatColor.DARK_RED+"Unrecognized command");
        }
        return true;
    }
}