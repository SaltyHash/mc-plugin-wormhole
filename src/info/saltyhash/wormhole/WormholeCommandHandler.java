package info.saltyhash.wormhole;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import info.saltyhash.wormhole.persistence.JumpRecord;
import info.saltyhash.wormhole.persistence.PlayerRecord;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.util.BlockIterator;

/** Handles commands given to Wormhole. */
class WormholeCommandHandler implements CommandExecutor {
    private final Wormhole    wormhole;
    private final EconManager econMgr;
    
    // Command usage strings
    private static final String usageAdd     = "/worm add [player | pub] <jump name>";
    //private static final String usageBack    = "/worm back";
    //private static final String usageCost    = "/worm cost";
    private static final String usageDel     = "/worm del [player | pub] <jump name>";
    private static final String usageJump    = "/worm jump [player | pub] <jump name>";
    private static final String usageList    = "/worm list [player | pub] [page]";
    //private static final String usageReload  = "/wormhole reload";
    private static final String usageRename  = "/worm rename [player | pub] <old name> <new name>";
    private static final String usageReplace = "/worm replace [player | pub] <jump name>";
    private static final String usageSet     = "/worm set [player | pub] <jump name>";
    //private static final String usageUnset   = "/worm unset";
    //private static final String usageVersion = "/wormhole version";
    
    WormholeCommandHandler(Wormhole wormhole, EconManager econMgr) {
        this.wormhole = wormhole;
        this.econMgr  = econMgr;
    }
    
    /**
     *  Handles the "wormhole add" command.
     * Usage: /worm add [pub | player] <jump name>
     */
    private void commandAdd(CommandSender sender, String[] args) {
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
            // Display usage
            player.sendMessage(usageAdd);
            return;
        }
        String playerName = jumpInfo[0];
        String jumpName   = jumpInfo[1];
        
        // Check permissions
        // Public jump?
        if (playerName == null) {
            if (!player.hasPermission("wormhole.add.public")) {
                player.sendMessage(ChatColor.DARK_RED+"You cannot add public Jumps");
                return;
            }
        }
        // Jump belonging to the player?
        else if (playerName.equalsIgnoreCase(player.getName())) {
            if (!player.hasPermission("wormhole.add.private")) {
                player.sendMessage(ChatColor.DARK_RED+"You cannot add your own Jumps");
                return;
            }
        }
        // Jump belonging to another player?
        else {
            if (!player.hasPermission("wormhole.add.other")) {
                player.sendMessage(ChatColor.DARK_RED+
                    "You cannot add Jumps for other players");
                return;
            }
        }
        
        // Make sure player can afford this action
        if (!player.hasPermission("wormhole.free")
                && !econMgr.hasBalance(player, "add")) {
            player.sendMessage(ChatColor.DARK_RED+
                    "You cannot afford to add new Jumps");
            return;
        }
        
        // Get player record for jump
        PlayerRecord playerRecord = PlayerRecord.load(playerName);
        // Player does not exist?
        if (playerRecord == null) {
            player.sendMessage(ChatColor.DARK_RED+"Failed to add jump; "+
                    "player '"+playerName+"' does not exist");
            return;
        }
        
        // Check if jump record already exists
        JumpRecord jumpRecord = JumpRecord.load(playerRecord.uuid, jumpName);
        if (jumpRecord != null) {
            player.sendMessage(ChatColor.DARK_RED+"Failed to add jump; jump "+
                    jumpRecord.getDescriptionForPlayer(player)+" already exists");
        }
        
        // Create new JumpRecord
        jumpRecord = new JumpRecord(playerRecord.uuid, jumpName,
                null, 0, 0, 0, 0);
        jumpRecord.setLocation(player.getLocation());
        
        // Save jump record; failed (unknown reason)?
        if (!jumpRecord.save()) {
            player.sendMessage(ChatColor.DARK_RED+"Failed to add jump; unknown reason");
            wormhole.getLogger().warning("Player '"+player.getName()+"' failed to add jump "+
                    jumpRecord.getDescription()+"; unknown reason");
            return;
        }
        
        player.sendMessage(ChatColor.DARK_GREEN+"Added"+ChatColor.RESET+
                " jump "+jumpRecord.getDescriptionForPlayer(player));
        
        // Charge player
        if (!player.hasPermission("wormhole.free"))
            econMgr.charge(player, "add");
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
        
        // Check permissions
        if (!player.hasPermission("wormhole.back")) {
            player.sendMessage(ChatColor.DARK_RED+
                    "You cannot jump back to your previous location");
            return;
        }
        
        // Make sure player can afford this action
        if (!player.hasPermission("wormhole.free")
                && !econMgr.hasBalance(player, "back")) {
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
                    "Failed to jump to previous location; unknown reason");
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
        if (!player.hasPermission("wormhole.free"))
            econMgr.charge(player, "back");
    }
    
    /**
     * Handles the "cost" command.
     * Usage: /worm cost
     */
    private void commandCost(CommandSender sender) {
        if (!econMgr.isEnabled()) {
            sender.sendMessage(ChatColor.DARK_RED+"Economy system does not exist");
            return;
        }
        
        FileConfiguration config = wormhole.getConfig();
        String add     = econMgr.econ.format(config.getDouble("cost.add"));
        String back    = econMgr.econ.format(config.getDouble("cost.back"));
        String del     = econMgr.econ.format(config.getDouble("cost.del"));
        String jump    = econMgr.econ.format(config.getDouble("cost.jump"));
        String rename  = econMgr.econ.format(config.getDouble("cost.rename"));
        String replace = econMgr.econ.format(config.getDouble("cost.replace"));
        String set     = econMgr.econ.format(config.getDouble("cost.set"));
        String unset   = econMgr.econ.format(config.getDouble("cost.unset"));
        String use     = econMgr.econ.format(config.getDouble("cost.use"));
        
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
     * Handles the "wormhole del" command.
     * Usage: /worm del [player| pub] <jump name>
     */
    private void commandDel(CommandSender sender, String[] args) {
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
            player.sendMessage(usageDel);
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
                && !econMgr.hasBalance(player, "del")) {
            player.sendMessage(ChatColor.DARK_RED+"You cannot afford to delete jumps");
            return;
        }
        
        // Retrieve the player record
        PlayerRecord playerRecord = PlayerRecord.load(playerName);
        if (playerRecord == null) {
            player.sendMessage(ChatColor.DARK_RED+
                    "Failed to delete jump; player '"+playerName+"' does not exist."
            );
            return;
        }
        
        // Retrieve the jump record
        JumpRecord jumpRecord = JumpRecord.load(playerRecord.uuid, jumpName);
        if (jumpRecord == null) {
            player.sendMessage(ChatColor.DARK_RED+"Failed to delete jump; jump does not exist.");
            return;
        }
        
        // Delete the jump; failed?
        if (!jumpRecord.delete()) {
            player.sendMessage(ChatColor.DARK_RED+"Failed to delete jump; unknown error.");
            wormhole.getLogger().warning("Player '"+player.getName()+"' failed to delete jump "+
                    jumpRecord.getDescription()+"; unknown reason.");
            return;
        }
        
        player.sendMessage(ChatColor.DARK_GREEN+"Deleted"+ChatColor.RESET+
            " jump "+jumpRecord.getDescriptionForPlayer(player));
        
        // Charge player
        if (!player.hasPermission("wormhole.free"))
            econMgr.charge(player, "del");
    }
    
    /**
     * Handles the "wormhole jump" command.
     * Usage: /worm jump [player | pub] <jump name>
     */
    private void commandJump(CommandSender sender, String[] args) {
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
            player.sendMessage(usageJump);
            return;
        }
        String playerName = jumpInfo[0];
        String jumpName   = jumpInfo[1];
        
        // Check permissions
        // Jump is public?
        if (playerName == null) {
            if (!player.hasPermission("wormhole.jump.public")) {
                player.sendMessage(ChatColor.DARK_RED+
                    "You cannot jump directly to public Jumps");
                return;
            }
        }
        // Jump belongs to the player?
        else if (playerName.equalsIgnoreCase(player.getName())) {
            if (!player.hasPermission("wormhole.jump.private")) {
                player.sendMessage(ChatColor.DARK_RED+
                    "You cannot jump directly to your Jumps");
                return;
            }
        }
        // Jump belongs to other player?
        else {
            if (!player.hasPermission("wormhole.jump.other")) {
                player.sendMessage(ChatColor.DARK_RED+
                    "You cannot jump directly to Jumps belonging to other players");
                return;
            }
        }
        
        // Make sure player can afford this action
        if (!player.hasPermission("wormhole.free")
                && !econMgr.hasBalance(player, "jump")) {
            player.sendMessage(ChatColor.DARK_RED+
                    "You cannot afford to jump directly to a Jump");
            return;
        }
        
        // Get the player record
        PlayerRecord playerRecord = PlayerRecord.load(playerName);
        // Player does not exist?
        if (playerRecord == null) {
            player.sendMessage(ChatColor.DARK_RED+
                    "Failed to jump; player '"+playerName+"' does not exist");
            return;
        }
    
        // Get the jump record
        JumpRecord jumpRecord = JumpRecord.load(playerRecord.uuid, jumpName);
        // Jump does not exist?
        if (jumpRecord == null) {
            player.sendMessage(ChatColor.DARK_RED+"Failed to jump; jump does not exist");
            // TODO: Tell player if a public jump with the same name exists
            /*if (jumpArg.isPrivate() && player.hasPermission("wormhole.list.public")
                    && jumpMgr.getJump("", jumpArg.jumpName) != null)
                player.sendMessage("Did you mean \"pub "+jumpArg.jumpName+"\"?");*/
            return;
        }
        
        Location from = player.getLocation();
        
        // Teleport the player; failed?
        if (!jumpRecord.teleportPlayer(player)) {
            player.sendMessage(ChatColor.DARK_RED+"Failed to jump; unknown reason");
            wormhole.getLogger().warning("Player '"+player.getName()+"' failed to jump to "+
                    jumpRecord.getDescription()+"; unknown reason");
            return;
        }
        
        // Play teleport effects
        wormhole.playTeleportEffect(from);
        wormhole.playTeleportEffect(player.getLocation());
    
        // Charge player
        if (!player.hasPermission("wormhole.free"))
            econMgr.charge(player, "jump");
    }
    
    /**
     * Handles the "wormhole list" command.
     * Usage: /worm list [pub | player] [page]
     */
    private void commandList(CommandSender sender, String[] args) {
        String playerName;
        int page, pageSize = 10;
        
        // Parse arguments
        try {
            // First arg should be empty to imply first page,
            // "pub", a player name, or a page number.
            
            // No args
            // Sender's jump list and page 1 implied
            if (args.length == 0) {
                playerName = sender.getName();
                page = 1;
            }
            
            // 1 arg
            // 1st arg alone either "pub", player name, or page number
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
                    if (args[0].equalsIgnoreCase("pub"))
                        playerName = null;
                    // Player jump list
                    else
                        playerName = args[0];
                }
            }
            
            // >=2 args
            // 1st arg is either "pub" or player name
            // 2nd arg is page number
            else {
                // Public jump list
                if (args[0].equalsIgnoreCase("pub"))
                    playerName = null;
                // Player jump list
                else
                    playerName = args[0];
                page = Integer.parseInt(args[1]);
            }
        }
        catch (ArrayIndexOutOfBoundsException | NumberFormatException e) {
            // Display command usage
            sender.sendMessage(usageList);
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
        
        // Get the player record
        PlayerRecord playerRecord = PlayerRecord.load(playerName);
        // Player does not exist?
        if (playerRecord == null) {
            sender.sendMessage(ChatColor.DARK_RED+"Player '"+playerName+"' does not exist");
            return;
        }
        
        // Get list of jump records
        List<JumpRecord> jumpRecords = JumpRecord.load(playerRecord.uuid);
        // Unknown error?
        if (jumpRecords == null) {
            sender.sendMessage(ChatColor.DARK_RED+"Failed to list jumps; unknown reason");
            wormhole.getLogger().warning(sender.getName()+" failed to list jumps for player '"+
                    playerName+"; unknown reason");
            return;
        }
        
        // Player has no jumps?
        if (jumpRecords.isEmpty()) {
            sender.sendMessage(ChatColor.DARK_PURPLE+"No jumps to list");
            return;
        }
        
        /*
        // Make list of jump names
        List<String> jumpNames = new ArrayList<>(jumpRecords.size());
        for (JumpRecord jumpRecord : jumpRecords)
            jumpNames.add(jumpRecord.name);
        */
        
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
        StringBuilder msg = new StringBuilder(String.format(
                "\n%sJumps%s for %s:  Page %s%d%s/%s%d%s",
                ChatColor.DARK_PURPLE, ChatColor.RESET,
                (playerName == null ? "Public" : playerName),
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
    
    /** Handles the "/wormhole reload" command. */
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
     * Handles the "worm rename" command.
     * Usage:  /worm [player | pub] <old name> <new name>
     */
    private void commandRename(CommandSender sender, String[] args) {
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
            player.sendMessage(usageRename);
            return;
        }
        if (newJumpName == null) {
            player.sendMessage(usageRename);
            return;
        }
        
        // Get existing jump info from args
        String[] jumpInfo = getJumpInfoFromArgs(player, args);
        // Parse error?
        if (jumpInfo == null) {
            player.sendMessage(usageRename);
            return;
        }
        String playerName  = jumpInfo[0];
        String oldJumpName = jumpInfo[1];
        
        // Check permissions
        // Jump is public?
        if (oldJumpName == null) {
            if (!player.hasPermission("wormhole.rename.public")) {
                player.sendMessage(ChatColor.DARK_RED+"You cannot rename public jumps");
                return;
            }
        }
        // Jump belongs to the player?
        else if (playerName.equals(player.getName())) {
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
                && !econMgr.hasBalance(player, "rename")) {
            player.sendMessage(ChatColor.DARK_RED+"You cannot afford to rename jumps");
            return;
        }
        
        // Get the player record
        PlayerRecord playerRecord = PlayerRecord.load(playerName);
        // Player does not exist?
        if (playerRecord == null) {
            player.sendMessage(ChatColor.DARK_RED+
                    "Failed to rename jump; player '"+playerName+"' does not exist");
            return;
        }
        
        // Try to get a jump record matching the new name
        JumpRecord jumpRecord = JumpRecord.load(playerRecord.uuid, newJumpName);
        // Jump with new name already exists?
        if (jumpRecord != null) {
            player.sendMessage(ChatColor.DARK_RED+
                    "Failed to rename jump; a jump named '"+newJumpName+"' already exists");
            return;
        }
        
        // Get the jump record with the old name
        jumpRecord = JumpRecord.load(playerRecord.uuid, oldJumpName);
        // Jump DNE?
        if (jumpRecord == null) {
            player.sendMessage(ChatColor.DARK_RED+
                    "Failed to rename jump; jump '"+oldJumpName+"' does not exist");
            return;
        }
        
        // Save the new jump name
        jumpRecord.name = newJumpName;
        // Error?
        if (!jumpRecord.save()) {
            player.sendMessage(ChatColor.DARK_RED+"Failed to rename jump; unknown reason");
            wormhole.getLogger().warning("Player '"+player.getName()+"' failed to rename jump '"+
                    oldJumpName+"' to '"+newJumpName+"'; unknown reason");
            return;
        }
        
        player.sendMessage(String.format(
            "%sRenamed%s jump '%s' to %s",
            ChatColor.DARK_GREEN, ChatColor.RESET,
            oldJumpName, jumpRecord.getDescriptionForPlayer(player)));
        
        // Charge player
        if (!player.hasPermission("wormhole.free"))
            econMgr.charge(player, "rename");
    }
    
    private void commandReplace(CommandSender sender, String[] args) {
        /* Handles the "worm replace" command.
         * Usage:  /worm replace [player | pub] <Jump name>
         */
        // Make sure sender is a player
        if (!(sender instanceof Player)) {
            sender.sendMessage("Must be a player");
            return;
        }
        Player player = (Player)sender;
        
        // Make sure player can afford this action
        if (!player.hasPermission("wormhole.free")
                && !econMgr.hasBalance(player, "replace")) {
            player.sendMessage(ChatColor.DARK_RED+
                "You cannot afford to replace Jumps");
            return;
        }
        
        // Get jump from args
        Jump jumpOld = getJumpInfoFromArgs(player, args);
        if (jumpOld == null) {
            // Display usage
            player.sendMessage(usageReplace);
            return;
        }
        Jump jumpNew = jumpOld.clone();
        jumpNew.setDest(player.getLocation());
        
        // Check permissions
        if (jumpOld.isPublic()) {
            if (!player.hasPermission("wormhole.replace.public")) {
                player.sendMessage(ChatColor.DARK_RED+"You cannot replace public Jumps");
                return;
            }
        }
        else if (jumpOld.playerName.equals(player.getName())) {
            if (!player.hasPermission("wormhole.replace.private")) {
                player.sendMessage(ChatColor.DARK_RED+"You cannot replace your Jumps");
                return;
            }
        }
        else {
            if (!player.hasPermission("wormhole.replace.other")) {
                player.sendMessage(ChatColor.DARK_RED+
                    "You cannot replace Jumps for other players");
                return;
            }
        }
        
        // Execute command to replace old jump with new jump
        int result = jumpMgr.updateJump(jumpOld, jumpNew);
        
        // Success
        if (result == 0) {
            player.sendMessage(ChatColor.DARK_GREEN+"Replaced"+ChatColor.RESET+
                " Jump "+jumpNew.getDescriptionForPlayer(player));
            
            // Charge player
            if (!player.hasPermission("wormhole.free"))
                econMgr.charge(player, "replace");
        }
        
        // Player does not exist
        else if (result == 1)
            player.sendMessage(String.format(ChatColor.DARK_RED+
                "Failed to replace Jump; player \"%s\" does not exist", jumpNew.playerName));
        
        // Jump does not exist
        else if (result == 2)
            player.sendMessage(String.format(ChatColor.DARK_RED+
                "Failed to replace Jump; Jump %s does not exist",
                jumpNew.getDescriptionForPlayer(player)));
        
        // Failure; unknown reason
        else {
            player.sendMessage(ChatColor.DARK_RED+"Failed to replace Jump; unknown reason");
            wormhole.getLogger().warning(String.format(
                "Player \"%s\" failed to replace Jump %s; unknown reason",
                player.getName(), jumpNew.getDescription()));
        }
    }
    
    private void commandSet(CommandSender sender, String[] args) {
        /* Handles the "worm set" command.
         * Usage:  /worm set [player | pub] <Jump name>
         */
        // Make sure sender is a player
        if (!(sender instanceof Player)) {
            sender.sendMessage("Must be a player");
            return;
        }
        Player player = (Player)sender;
        
        // Make sure player can afford this action
        if (!player.hasPermission("wormhole.free")
                && !econMgr.hasBalance(player, "set")) {
            player.sendMessage(ChatColor.DARK_RED+
                "You cannot afford to set signs to Jumps");
            return;
        }
        
        // Get jump from args
        Jump jumpArg = getJumpInfoFromArgs(player, args);
        if (jumpArg == null) {
            // Display usage
            player.sendMessage(usageSet);
            return;
        }
        
        // Check permissions
        if (jumpArg.isPublic()) {
            if (!player.hasPermission("wormhole.set.public")) {
                player.sendMessage(ChatColor.DARK_RED+
                    "You cannot set signs to public Jumps");
                return;
            }
        }
        else if (jumpArg.playerName.equals(player.getName())) {
            if (!player.hasPermission("wormhole.set.private")) {
                player.sendMessage(ChatColor.DARK_RED+
                    "You cannot set signs to your Jumps");
                return;
            }
        }
        else {
            if (!player.hasPermission("wormhole.set.other")) {
                player.sendMessage(ChatColor.DARK_RED+
                    "You cannot set signs to other players' Jumps");
                return;
            }
        }
        
        // Get actual jump
        Jump jump = jumpMgr.getJump(jumpArg.playerName, jumpArg.jumpName);
        if (jump == null) {
            player.sendMessage(ChatColor.DARK_RED+
                "Failed to set sign; Jump does not exist");
            // Tell player if a public jump with the same name exists
            if (jumpArg.isPrivate() && player.hasPermission("wormhole.list.public")
                    && jumpMgr.getJump("", jumpArg.jumpName) != null)
                player.sendMessage("Did you mean \"pub "+jumpArg.jumpName+"\"?");
            return;
        }
        
        // Get sign block
        //Block target = player.getTargetBlock(null, 5);
        Block target = null;
        BlockIterator bit = new BlockIterator(player, 5);
        while (bit.hasNext()) {
            target = bit.next();
            if (target.getState() instanceof Sign) break;
            else target = null;
        }
        if (target == null) {
            player.sendMessage(ChatColor.DARK_RED+
                "Failed to set sign; you must be looking at a sign");
            return;
        }
        Sign sign = (Sign)target.getState();
        
        // Set sign jump destination
        int result = signMgr.addSignJump(sign, jump);
        
        // Success
        if (result == 0) {
            player.sendMessage(ChatColor.DARK_GREEN+"Set sign"+ChatColor.RESET+
                " to Jump "+jump.getDescriptionForPlayer(player));
            // Charge player
            if (!player.hasPermission("wormhole.free"))
                econMgr.charge(player, "set");
        }
        
        // Sign already set
        else if (result == 1)
            player.sendMessage(ChatColor.DARK_RED+
                "Failed to set sign; sign already set");
        
        // Unknown failure
        else {
            player.sendMessage(ChatColor.DARK_RED+
                "Failed to set sign; unknown reason");
            wormhole.getLogger().warning(String.format(
                "Player \"%s\" failed to set sign (%s, %d, %d, %d) "+
                "to Jump %s; unknown reason",
                player.getName(), sign.getWorld().getName(), sign.getX(),
                sign.getY(), sign.getZ(), jump.getDescription()));
        }
    }
    
    private void commandUnset(CommandSender sender, String[] args) {
        /* Handles the "wormhole unset" command.
         * Usage:  /worm unset
         */
        // Make sure sender is a player
        if (!(sender instanceof Player)) {
            sender.sendMessage("Must be a player");
            return;
        }
        Player player = (Player)sender;
        
        // Get sign block
        //Block target = player.getTargetBlock(null, 5);
        Block target = null;
        BlockIterator bit = new BlockIterator(player, 5);
        while (bit.hasNext()) {
            target = bit.next();
            if (target.getState() instanceof Sign) break;
            else target = null;
        }
        if (target == null) {
            player.sendMessage(ChatColor.DARK_RED+
                "Failed to unset sign; you must be looking at a sign");
            return;
        }
        Sign sign = (Sign)target.getState();
        
        // Get jump destination from sign
        Jump jump = signMgr.getSignJump(sign);
        // Check jump
        if (jump == null) {
            player.sendMessage(ChatColor.DARK_GREEN+"That sign is not set");
            return;
        }
        
        // Make sure player can afford this action
        if (!player.hasPermission("wormhole.free")
                && !econMgr.hasBalance(player, "unset")) {
            player.sendMessage(ChatColor.DARK_RED+
                "You cannot afford to unset signs pointing to Jumps");
            return;
        }
        
        // Check permissions
        if (jump.isPublic()) {
            if (!player.hasPermission("wormhole.unset.public")) {
                player.sendMessage(ChatColor.DARK_RED+
                    "You cannot unset signs pointing to public Jumps");
                return;
            }
        }
        else if (jump.playerName.equals(player.getName())) {
            if (!player.hasPermission("wormhole.unset.private")) {
                player.sendMessage(ChatColor.DARK_RED+
                    "You cannot unset signs pointing to your Jumps");
                return;
            }
        }
        else {
            if (!player.hasPermission("wormhole.unset.other")) {
                player.sendMessage(ChatColor.DARK_RED+
                    "You cannot unset signs pointing to other players' Jumps");
                return;
            }
        }
        
        // Delete sign
        int result = signMgr.delSignJump(sign);
        
        // Success
        if (result == 0) {
            player.sendMessage(String.format(
                "%sUnset sign%s pointing to Jump %s",
                ChatColor.DARK_GREEN, ChatColor.RESET,
                jump.getDescriptionForPlayer(player)));
            // Charge player
            if (!player.hasPermission("wormhole.free"))
                econMgr.charge(player, "unset");
        }
        
        // Sign not set
        else if (result == 1)
            player.sendMessage(ChatColor.DARK_GREEN+"Sign not set");
        
        // Failure
        else {
            player.sendMessage(ChatColor.DARK_RED+
                "Failed to unset sign; unknown reason");
            wormhole.getLogger().warning(String.format(
                "Player \"%s\" failed to unset sign (%s, %d, %d, %d) "+
                "pointing to Jump %s; unknown reason",
                player.getName(), sign.getWorld().getName(), sign.getX(),
                sign.getY(), sign.getZ(), jump.getDescription()));
        }
    }
    
    /** Handles the "/wormhole version" command. */
    private void commandVersion(CommandSender sender) {
        // Check permissions
        if (!sender.hasPermission("wormhole.version")) {
            sender.sendMessage(ChatColor.DARK_RED+"You cannot view Wormhole version information");
            return;
        }
        
        // Build and send message
        PluginDescriptionFile pdf = wormhole.getDescription();
        List<String> authors = pdf.getAuthors();
        Collections.sort(authors);
        StringBuilder msg = new StringBuilder();
        msg.append(String.format("%s%s%s v%s",
                ChatColor.DARK_PURPLE, pdf.getFullName(), ChatColor.RESET, pdf.getVersion()));
        if (authors.size() == 1) {
            msg.append("\nAuthor: ").append(authors.get(0));
        } else if (authors.size() > 1) {
            msg.append("\nAuthors:");
            for (String author : authors)
                msg.append("\n    ").append(author);
        }
        sender.sendMessage(msg.toString());
    }
    
    /**
     * Parses arguments and returns an array containing 0) the player name
     * and 1) the jump name, or null on parse error.
     * Format: [player | pub] <jump name>
     */
    private String[] getJumpInfoFromArgs(Player player, String[] args) {
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
            else if (args[0].equalsIgnoreCase("pub")) {
                // Check args
                if (args[1].isEmpty()) return null;
                // Player name empty to designate public Jump,
                // and jump name in 1st arg
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
    
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        /* Called when command is issued. */
        String cmdName = cmd.getName().toLowerCase();
        
        if (cmdName.equals("wormhole")) {
            // Get action and args
            String action;
            try {
                // Get action
                action = args[0].toLowerCase();
                // Cut off first arg (it was used to get action)
                args = Arrays.copyOfRange(args, 1, args.length);
            }
            catch (ArrayIndexOutOfBoundsException e) {
                return false;
            }
            
            // Give action to appropriate handler function
            switch (action) {
                case "add"    : commandAdd(sender, args);     break;
                case "back"   : commandBack(sender);          break;
                case "cost"   : commandCost(sender);          break;
                case "del"    : commandDel(sender, args);     break;
                case "jump"   : commandJump(sender, args);    break;
                case "list"   : commandList(sender, args);    break;
                case "reload" : commandReload(sender);        break;
                case "rename" : commandRename(sender, args);  break;
                case "replace": commandReplace(sender, args); break;
                case "set"    : commandSet(sender, args);     break;
                case "unset"  : commandUnset(sender, args);   break;
                case "version": commandVersion(sender);       break;
                default:
                    sender.sendMessage(ChatColor.DARK_RED+"Unrecognized command");
            }
            return true;
        }
        
        return false;
    }
}