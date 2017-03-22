package info.saltyhash.wormhole;

import java.util.Arrays;
import java.util.List;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.util.BlockIterator;

/** Handles commands given to Wormhole. */
class WormholeCommandHandler implements CommandExecutor {
    private final Wormhole wormhole;
    private final EconManager econMgr;
    private final JumpManager jumpMgr;
    private final PlayerManager playerMgr;
    private final SignManager signMgr;
    
    public final String usageAdd     = "/worm add [player | pub] <Jump name>";
    public final String usageBack    = "/worm back [player | pub] <Jump name>";
    public final String usageCost    = "/worm cost";
    public final String usageDel     = "/worm del [player | pub] <Jump name>";
    public final String usageJump    = "/worm jump [player | pub] <Jump name>";
    public final String usageList    = "/worm list [player | pub] [page]";
    public final String usageReload  = "/wormhole reload";
    public final String usageRename  = "/worm rename [player | pub] <old name> <new name>";
    public final String usageReplace = "/worm replace [player | pub] <Jump name>";
    public final String usageSet     = "/worm set [player | pub] <Jump name>";
    public final String usageUnset   = "/worm unset";
    public final String usageVersion = "/wormhole version";
    
    WormholeCommandHandler(Wormhole wormhole, EconManager econMgr,
            JumpManager jumpMgr, PlayerManager playerMgr, SignManager signMgr) {
        this.wormhole  = wormhole;
        this.econMgr   = econMgr;
        this.jumpMgr   = jumpMgr;
        this.playerMgr = playerMgr;
        this.signMgr   = signMgr;
    }
    
    private void commandAdd(CommandSender sender, String[] args) {
        /* Handles the "wormhole add" command.
         * Usage: /worm add [pub | player] <jump name>
         */
        // Make sure sender is a player
        if (!(sender instanceof Player)) {
            sender.sendMessage("Must be a player");
            return;
        }
        Player player = (Player)sender;
        
        // Make sure player can afford this action
        if (!player.hasPermission("wormhole.free")
                && !this.econMgr.hasBalance(player, "add")) {
            player.sendMessage(ChatColor.DARK_RED+
                "You cannot afford to add new Jumps");
            return;
        }
        
        // Get jump from args
        Jump jump = this.getJumpFromArgs(player, args);
        if (jump == null) {
            // Display usage
            player.sendMessage(this.usageAdd);
            return;
        }
        jump.setDest(player.getLocation());
        
        // Check permissions
        if (jump.isPublic()) {
            if (!player.hasPermission("wormhole.add.public")) {
                player.sendMessage(ChatColor.DARK_RED+"You cannot add public Jumps");
                return;
            }
        }
        else if (jump.playerName.equals(player.getName())) {
            if (!player.hasPermission("wormhole.add.private")) {
                player.sendMessage(ChatColor.DARK_RED+"You cannot add your own Jumps");
                return;
            }
        }
        else {
            if (!player.hasPermission("wormhole.add.other")) {
                player.sendMessage(ChatColor.DARK_RED+
                    "You cannot add Jumps for other players");
                return;
            }
        }
        
        // Execute command to add Jump
        int result = this.jumpMgr.addJump(jump);
        
        // Success
        if (result == 0) {
            player.sendMessage(ChatColor.DARK_GREEN+"Added"+ChatColor.RESET+
                " Jump "+jump.getDescriptionForPlayer(player));
            
            // Charge player
            if (!player.hasPermission("wormhole.free"))
                this.econMgr.charge(player, "add");
        }
        
        // Player does not exist
        else if (result == 1)
            player.sendMessage(String.format(ChatColor.DARK_RED+
                "Failed to add Jump; player \"%s\" does not exist", jump.playerName));
        
        // Jump already exists
        else if (result == 2)
            player.sendMessage(String.format(ChatColor.DARK_RED+
                "Failed to add Jump; Jump %s already exists",
                jump.getDescriptionForPlayer(player)));
        
        // Failure; unknown reason
        else {
            player.sendMessage(ChatColor.DARK_RED+"Failed to add Jump; unknown reason");
            this.wormhole.getLogger().warning(String.format(
                "Player \"%s\" failed to add Jump %s; unknown reason",
                player.getName(), jump.getDescription()));
        }
    }
    
    private void commandBack(CommandSender sender) {
        /* Handles the "wormhole back" command.
         * Usage: /worm back
         */
        if (!(sender instanceof Player)) {
            sender.sendMessage("Must be a player");
            return;
        }
        Player player = (Player)sender;
        
        // Make sure player can afford this action
        if (!player.hasPermission("wormhole.free")
                && !this.econMgr.hasBalance(player, "back")) {
            player.sendMessage(ChatColor.DARK_RED+
                "You cannot afford to jump back to your previous location");
            return;
        }
        
        // Check permissions
        if (!player.hasPermission("wormhole.back")) {
            player.sendMessage(ChatColor.DARK_RED+
                "You cannot jump back to your previous location");
            return;
        }
        
        // Get player's previous Jump
        Jump jumpLast = this.playerMgr.getLastJump(player);
        if (jumpLast == null) {
            player.sendMessage(ChatColor.DARK_RED+"No previous location to jump to");
            return;
        }
        
        // Play teleport effect
        this.wormhole.playTeleportEffect(player.getLocation());
        
        // Jump the player to previous location
        int result = jumpLast.jumpPlayer(player);
        
        // Success
        if (result == 0) {
            // Charge player
            if (!player.hasPermission("wormhole.free"))
                this.econMgr.charge(player, "back");
        }
        
        // Failure
        else {
            player.sendMessage(ChatColor.DARK_RED+
                "Failed to jump to previous location; unknown reason");
            this.wormhole.getLogger().warning(String.format(
                "Failed to jump player \"%s\" to previous location; unknown reason",
                player.getName()));
            return;
        }
        
        // Play teleport effect
        this.wormhole.playTeleportEffect(player.getLocation());
    }
    
    private void commandCost(CommandSender sender) {
        /* Handles the "/worm cost" command. */
        if (!this.econMgr.isEnabled()) {
            sender.sendMessage(ChatColor.DARK_RED+
                "Economy system does not exist");
            return;
        }
        
        FileConfiguration config = this.wormhole.getConfig();
        String add     = this.econMgr.econ.format(config.getDouble("cost.add"));
        String back    = this.econMgr.econ.format(config.getDouble("cost.back"));
        String del     = this.econMgr.econ.format(config.getDouble("cost.del"));
        String jump    = this.econMgr.econ.format(config.getDouble("cost.jump"));
        String rename  = this.econMgr.econ.format(config.getDouble("cost.rename"));
        String replace = this.econMgr.econ.format(config.getDouble("cost.replace"));
        String set     = this.econMgr.econ.format(config.getDouble("cost.set"));
        String unset   = this.econMgr.econ.format(config.getDouble("cost.unset"));
        String use     = this.econMgr.econ.format(config.getDouble("cost.use"));
        
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
    
    private void commandDel(CommandSender sender, String[] args) {
        /* Handles the "wormhole del" command.
         * Usage: /worm del [player| pub] <Jump name>
         */
        // Make sure sender is a player
        if (!(sender instanceof Player)) {
            sender.sendMessage("Must be a player");
            return;
        }
        Player player = (Player)sender;
        
        // Make sure player can afford this action
        if (!player.hasPermission("wormhole.free")
                && !this.econMgr.hasBalance(player, "del")) {
            player.sendMessage(ChatColor.DARK_RED+
                "You cannot afford to delete Jumps");
            return;
        }
        
        // Get jump from args
        Jump jump = this.getJumpFromArgs(player, args);
        if (jump == null) {
            // Display usage
            player.sendMessage(this.usageDel);
            return;
        }
        
        // Check permissions
        if (jump.isPublic()) {
            if (!player.hasPermission("wormhole.del.public")) {
                player.sendMessage(ChatColor.DARK_RED+"You cannot delete public Jumps");
                return;
            }
        }
        else if (jump.playerName.equals(player.getName())) {
            if (!player.hasPermission("wormhole.del.private")) {
                player.sendMessage(ChatColor.DARK_RED+"You cannot delete your Jumps");
                return;
            }
        }
        else {
            if (!player.hasPermission("wormhole.del.other")) {
                player.sendMessage(ChatColor.DARK_RED+
                    "You cannot delete other players' Jumps");
                return;
            }
        }
        
        // Delete jump
        int result = this.jumpMgr.delJump(jump);
        
        // Success
        if (result == 0) {
            player.sendMessage(ChatColor.DARK_GREEN+"Deleted"+ChatColor.RESET+
                " Jump "+jump.getDescriptionForPlayer(player));
            
            // Charge player
            if (!player.hasPermission("wormhole.free"))
                this.econMgr.charge(player, "del");
        }
        
        // Failure; jump DNE
        else if (result == 1)
            sender.sendMessage(ChatColor.DARK_RED+
                "Failed to delete Jump; Jump does not exist");
        
        // Failure; unknown reason
        else {
            sender.sendMessage(ChatColor.DARK_RED+"Failed to delete Jump; unknown reason");
            this.wormhole.getLogger().warning(String.format(
                "Player \"%s\" failed to delete Jump %s; unknown reason",
                player.getName(), jump.getDescription()));
        }
    }
    
    private void commandJump(CommandSender sender, String[] args) {
        /* Handles the "wormhole jump" command.
         * Usage: /worm jump [player | pub] <Jump name>
         */
        // Make sure sender is a player
        if (!(sender instanceof Player)) {
            sender.sendMessage("Must be a player");
            return;
        }
        Player player = (Player)sender;
        
        // Make sure player can afford this action
        if (!player.hasPermission("wormhole.free")
                && !this.econMgr.hasBalance(player, "jump")) {
            player.sendMessage(ChatColor.DARK_RED+
                "You cannot afford to jump directly to a Jump");
            return;
        }

        // Get jump from args
        Jump jumpArg = this.getJumpFromArgs(player, args);
        if (jumpArg == null) {
            // Display usage
            player.sendMessage(this.usageJump);
            return;
        }
        
        // Check permissions
        if (jumpArg.isPublic()) {
            if (!player.hasPermission("wormhole.jump.public")) {
                player.sendMessage(ChatColor.DARK_RED+
                    "You cannot jump directly to public Jumps");
                return;
            }
        }
        else if (jumpArg.playerName.equals(player.getName())) {
            if (!player.hasPermission("wormhole.jump.private")) {
                player.sendMessage(ChatColor.DARK_RED+
                    "You cannot jump directly to your Jumps");
                return;
            }
        }
        else {
            if (!player.hasPermission("wormhole.jump.other")) {
                player.sendMessage(ChatColor.DARK_RED+
                    "You cannot jump directly to Jumps belonging to other players");
                return;
            }
        }
        
        // Get specified Jump
        Jump jump = this.jumpMgr.getJump(jumpArg.playerName, jumpArg.jumpName);
        if (jump == null) {
            player.sendMessage(ChatColor.DARK_RED+"Failed to jump; Jump does not exist");
            // Tell player if a public jump with the same name exists
            if (jumpArg.isPrivate() && player.hasPermission("wormhole.list.public")
                    && this.jumpMgr.getJump("", jumpArg.jumpName) != null)
                player.sendMessage("Did you mean \"pub "+jumpArg.jumpName+"\"?");
            return;
        }
        
        Location from = player.getLocation();
        
        // Jump the player
        int result = jump.jumpPlayer(player);
        
        // Success
        if (result == 0) {
            // Charge player
            if (!player.hasPermission("wormhole.free"))
                this.econMgr.charge(player, "jump");
        }
            
        // Failure
        else {
            player.sendMessage(ChatColor.DARK_RED+"Failed to jump; unknown reason");
            this.wormhole.getLogger().warning(String.format(
                "Player \"%s\" failed to Jump to %s; unknown reason",
                player.getName(), jump.getDescription()));
            return;
        }
        
        // Play teleport effects
        this.wormhole.playTeleportEffect(from);
        this.wormhole.playTeleportEffect(player.getLocation());
    }
    
    private void commandList(CommandSender sender, String[] args) {
        /* Handles the "wormhole list" command.
         * Usage: /worm list [pub | player] [page]
         */
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
                        playerName = "";
                    // Player jump list
                    else playerName =
                        this.wormhole.getServer().getOfflinePlayer(args[0]).getName();
                }
            }
            
            // >=2 args
            // 1st arg is either "pub" or player name
            // 2nd arg is page number
            else {
                // Public jump list
                if (args[0].equalsIgnoreCase("pub"))
                    playerName = "";
                // Player jump list
                else playerName =
                    this.wormhole.getServer().getOfflinePlayer(args[0]).getName();
                page = Integer.parseInt(args[1]);
            }
        }
        catch (ArrayIndexOutOfBoundsException | NumberFormatException e) {
            // Display command usage
            sender.sendMessage(this.usageList);
            return;
        }
        if (page < 1) page = 1;
        
        // Check permissions
        if (playerName.isEmpty()) {
            if (!sender.hasPermission("wormhole.list.public")) {
                sender.sendMessage(ChatColor.DARK_RED+"You cannot list public Jumps");
                return;
            }
        }
        else if (playerName.equals(sender.getName())) {
            if (!sender.hasPermission("wormhole.list.private")) {
                sender.sendMessage(ChatColor.DARK_RED+"You cannot list your Jumps");
                return;
            }
        }
        else {
            if (!sender.hasPermission("wormhole.list.other")) {
                sender.sendMessage(ChatColor.DARK_RED+
                    "You cannot list other players' Jumps");
                return;
            }
        }
        
        // Get list of jump names
        List<String> jumpNames = this.jumpMgr.getJumpNameList(playerName);
        // Unknown error
        if (jumpNames == null) {
            sender.sendMessage(ChatColor.DARK_RED+"Failed to list Jumps; unknown reason");
            this.wormhole.getLogger().warning(
                String.format("%s failed to list Jumps for player \"%s\" for unknown reason",
                    sender.getName(), playerName));
            return;
        }
        // Player has no jumps
        if (jumpNames.isEmpty()) {
            sender.sendMessage(ChatColor.DARK_PURPLE+"No Jumps to list");
            return;
        }
        
        // Get number of pages
        int pages = (jumpNames.size()/pageSize)+((jumpNames.size()%pageSize) != 0 ? 1 : 0);
        if (page > pages) page = pages;
        
        // Make page list
        try {
            int start = (page-1)*pageSize;
            int end   = start+pageSize;
            if (end > jumpNames.size()) end = jumpNames.size();
            jumpNames = jumpNames.subList(start, end);
        }
        // Page does not exist
        catch (IllegalArgumentException e) {
            sender.sendMessage(String.format(ChatColor.DARK_RED+
                "Page %d does not exist", page));
            return;
        }
        
        // Display list
        String msg = String.format(
            "\n%sJumps%s for %s:  Page %s%d%s/%s%d%s",
            ChatColor.DARK_PURPLE, ChatColor.RESET,
            (playerName.isEmpty() ? "Public" : playerName),
            ChatColor.DARK_AQUA, page, ChatColor.RESET,
            ChatColor.DARK_AQUA, pages, ChatColor.RESET);
        for (String jumpName : jumpNames) {
            Jump jump = this.jumpMgr.getJump(playerName, jumpName);
            if (jump == null) continue;
            msg += String.format(ChatColor.RESET+
                "\n- %s%s%s:  W:%s%s%s  X:%s%d%s  Y:%s%d%s  Z:%s%d%s",
                ChatColor.DARK_PURPLE, jump.jumpName, ChatColor.RESET,
                ChatColor.DARK_AQUA, jump.worldName, ChatColor.RESET,
                ChatColor.DARK_AQUA, (int)jump.x, ChatColor.RESET,
                ChatColor.DARK_AQUA, (int)jump.y, ChatColor.RESET,
                ChatColor.DARK_AQUA, (int)jump.z, ChatColor.RESET);
        }
        sender.sendMessage(msg);
    }
    
    private void commandReload(CommandSender sender) {
        /* Handles the "/wormhole reload" command. */
        if (!sender.hasPermission("wormhole.reload")) {
            sender.sendMessage(ChatColor.DARK_RED+
                "You cannot reload the Wormhole config");
            return;
        }
        this.wormhole.reloadConfig();
        sender.sendMessage(ChatColor.DARK_GREEN+"Wormhole config reloaded");
        this.wormhole.getLogger().info("Config reloaded by "+sender.getName());
    }
    
    private void commandRename(CommandSender sender, String[] args) {
        /* Handles the "worm rename" command.
         * Usage:  /worm [player | pub] <old name> <new name> 
         */
        // Make sure sender is a player
        if (!(sender instanceof Player)) {
            sender.sendMessage("Must be a player");
            return;
        }
        Player player = (Player)sender;
        
        // Make sure player can afford this action
        if (!player.hasPermission("wormhole.free")
                && !this.econMgr.hasBalance(player, "rename")) {
            player.sendMessage(ChatColor.DARK_RED+
                "You cannot afford to rename Jumps");
            return;
        }
        
        // Get specified jump and new name
        String nameNew;
        try {
            // Get new name from last arg
            nameNew = args[args.length-1];
            // Remove the last index from args for getJumpFromArgs method
            args = Arrays.copyOfRange(args, 0, args.length-1);
        }
        catch (ArrayIndexOutOfBoundsException e) {
            player.sendMessage(this.usageRename);
            return;
        }
        Jump jump = this.getJumpFromArgs(player, args);
        
        // Check jump and new name
        if (jump == null || nameNew == null) {
            // Display usage
            player.sendMessage(this.usageRename);
            return;
        }
        
        // Check permissions
        if (jump.isPublic()) {
            if (!player.hasPermission("wormhole.rename.public")) {
                player.sendMessage(ChatColor.DARK_RED+"You cannot rename public Jumps");
                return;
            }
        }
        else if (jump.playerName.equals(player.getName())) {
            if (!player.hasPermission("wormhole.rename.private")) {
                player.sendMessage(ChatColor.DARK_RED+"You cannot rename your Jumps");
                return;
            }
        }
        else {
            if (!player.hasPermission("wormhole.rename.other")) {
                player.sendMessage(ChatColor.DARK_RED+
                    "You cannot rename Jumps for other players");
                return;
            }
        }
        
        // Get old jump
        Jump jumpOld = this.jumpMgr.getJump(jump.playerName, jump.jumpName);
        if (jumpOld == null) {
            player.sendMessage(String.format(ChatColor.DARK_RED+
                "Failed to rename Jump; Jump %s does not exist",
                jump.getDescriptionForPlayer(player)));
            return;
        }
        
        // Get new Jump
        Jump jumpNew = jumpOld.clone();
        jumpNew.jumpName = nameNew;
        if (this.jumpMgr.exists(jumpNew)) {
            player.sendMessage(String.format(ChatColor.DARK_RED+
                "Failed to rename Jump; Jump %s already exists",
                jumpNew.getDescriptionForPlayer(player)));
            return;
        }
        
        // Execute command to rename Jump
        int result = this.jumpMgr.updateJump(jumpOld, jumpNew);
        
        // Success
        if (result == 0) {
            player.sendMessage(String.format(
                "%sRenamed%s Jump %s to \"%s\"",
                ChatColor.DARK_GREEN, ChatColor.RESET,
                jumpOld.getDescriptionForPlayer(player), nameNew));
            
            // Charge player
            if (!player.hasPermission("wormhole.free"))
                this.econMgr.charge(player, "rename");
        }
        
        // Player does not exist
        else if (result == 1)
            player.sendMessage(String.format(ChatColor.DARK_RED+
                "Failed to rename Jump; player \"%s\" does not exist",
                jumpOld.playerName));
        
        // Jump does not exist
        else if (result == 2)
            player.sendMessage(String.format(ChatColor.DARK_RED+
                "Failed to rename Jump; Jump %s does not exist",
                jumpOld.getDescriptionForPlayer(player)));
        
        // Failure; unknown reason
        else {
            player.sendMessage(ChatColor.DARK_RED+"Failed to rename Jump; unknown reason");
            this.wormhole.getLogger().warning(String.format(
                "Player \"%s\" failed to rename Jump %s to \"%s\"; unknown reason",
                player.getName(), jumpOld.getDescription(), nameNew));
        }
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
                && !this.econMgr.hasBalance(player, "replace")) {
            player.sendMessage(ChatColor.DARK_RED+
                "You cannot afford to replace Jumps");
            return;
        }
        
        // Get jump from args
        Jump jumpOld = this.getJumpFromArgs(player, args);
        if (jumpOld == null) {
            // Display usage
            player.sendMessage(this.usageReplace);
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
        int result = this.jumpMgr.updateJump(jumpOld, jumpNew);
        
        // Success
        if (result == 0) {
            player.sendMessage(ChatColor.DARK_GREEN+"Replaced"+ChatColor.RESET+
                " Jump "+jumpNew.getDescriptionForPlayer(player));
            
            // Charge player
            if (!player.hasPermission("wormhole.free"))
                this.econMgr.charge(player, "replace");
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
            this.wormhole.getLogger().warning(String.format(
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
                && !this.econMgr.hasBalance(player, "set")) {
            player.sendMessage(ChatColor.DARK_RED+
                "You cannot afford to set signs to Jumps");
            return;
        }
        
        // Get jump from args
        Jump jumpArg = this.getJumpFromArgs(player, args);
        if (jumpArg == null) {
            // Display usage
            player.sendMessage(this.usageSet);
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
        Jump jump = this.jumpMgr.getJump(jumpArg.playerName, jumpArg.jumpName);
        if (jump == null) {
            player.sendMessage(ChatColor.DARK_RED+
                "Failed to set sign; Jump does not exist");
            // Tell player if a public jump with the same name exists
            if (jumpArg.isPrivate() && player.hasPermission("wormhole.list.public")
                    && this.jumpMgr.getJump("", jumpArg.jumpName) != null)
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
        int result = this.signMgr.addSignJump(sign, jump);
        
        // Success
        if (result == 0) {
            player.sendMessage(ChatColor.DARK_GREEN+"Set sign"+ChatColor.RESET+
                " to Jump "+jump.getDescriptionForPlayer(player));
            // Charge player
            if (!player.hasPermission("wormhole.free"))
                this.econMgr.charge(player, "set");
        }
        
        // Sign already set
        else if (result == 1)
            player.sendMessage(ChatColor.DARK_RED+
                "Failed to set sign; sign already set");
        
        // Unknown failure
        else {
            player.sendMessage(ChatColor.DARK_RED+
                "Failed to set sign; unknown reason");
            this.wormhole.getLogger().warning(String.format(
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
        Jump jump = this.signMgr.getSignJump(sign);
        // Check jump
        if (jump == null) {
            player.sendMessage(ChatColor.DARK_GREEN+"That sign is not set");
            return;
        }
        
        // Make sure player can afford this action
        if (!player.hasPermission("wormhole.free")
                && !this.econMgr.hasBalance(player, "unset")) {
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
        int result = this.signMgr.delSignJump(sign);
        
        // Success
        if (result == 0) {
            player.sendMessage(String.format(
                "%sUnset sign%s pointing to Jump %s",
                ChatColor.DARK_GREEN, ChatColor.RESET,
                jump.getDescriptionForPlayer(player)));
            // Charge player
            if (!player.hasPermission("wormhole.free"))
                this.econMgr.charge(player, "unset");
        }
        
        // Sign not set
        else if (result == 1)
            player.sendMessage(ChatColor.DARK_GREEN+"Sign not set");
        
        // Failure
        else {
            player.sendMessage(ChatColor.DARK_RED+
                "Failed to unset sign; unknown reason");
            this.wormhole.getLogger().warning(String.format(
                "Player \"%s\" failed to unset sign (%s, %d, %d, %d) "+
                "pointing to Jump %s; unknown reason",
                player.getName(), sign.getWorld().getName(), sign.getX(),
                sign.getY(), sign.getZ(), jump.getDescription()));
        }
    }
    
    private void commandVersion(CommandSender sender) {
        /* Handles the "/wormhole version" command. */
        // Check permissions
        if (!sender.hasPermission("wormhole.version")) {
            sender.sendMessage(ChatColor.DARK_RED+
                "You cannot view Wormhole version information");
            return;
        }
        
        // Display Wormhole version
        sender.sendMessage(String.format(
            "%sWormhole%s v%s\n"+
            "Author: Austin Bowen <austin.bowen.314@gmail.com>",
            ChatColor.DARK_PURPLE, ChatColor.RESET,
            this.wormhole.getDescription().getVersion()));
    }
    
    private Jump getJumpFromArgs(Player player, String[] args) {
        /* Returns a Jump with jumpName and playerName set from args.
         * Format: [player | pub] <Jump name>
         */
        Jump jump = new Jump();
        
        // Parse arguments
        try {
            // Private Jump
            if (args.length == 1) {
                // Check args
                if (args[0].isEmpty()) return null;
                // Player name is current player, jump name in 1st arg
                jump.playerName = player.getName();
                jump.jumpName   = args[0];
            }
            
            // Public Jump
            else if (args[0].equalsIgnoreCase("pub")) {
                // Check args
                if (args[1].isEmpty()) return null;
                // Player name empty to designate public Jump,
                // and jump name in 2nd arg
                jump.setPublic();
                jump.jumpName = args[1];
            }
            
            // Other player's Jump
            else {
                // Check args
                if (args[0].isEmpty() || args[1].isEmpty()) return null;
                // Player name in 1st arg, jump name in 2nd arg
                jump.playerName = this.wormhole.getServer().getOfflinePlayer(args[0]).getName();
                jump.jumpName   = args[1];
            }
        }
        catch (ArrayIndexOutOfBoundsException e) {
            // Failed to get Jump
            return null;
        }
        
        return jump;
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
            if      (action.equals("add"))     this.commandAdd(sender, args);
            else if (action.equals("back"))    this.commandBack(sender);
            else if (action.equals("cost"))    this.commandCost(sender);
            else if (action.equals("del"))     this.commandDel(sender, args);
            else if (action.equals("jump"))    this.commandJump(sender, args);
            else if (action.equals("list"))    this.commandList(sender, args);
            else if (action.equals("reload"))  this.commandReload(sender);
            else if (action.equals("rename"))  this.commandRename(sender, args);
            else if (action.equals("replace")) this.commandReplace(sender, args);
            else if (action.equals("set"))     this.commandSet(sender, args);
            else if (action.equals("unset"))   this.commandUnset(sender, args);
            else if (action.equals("version")) this.commandVersion(sender);
            else return false;
            return true;
        }
        
        return false;
    }
}