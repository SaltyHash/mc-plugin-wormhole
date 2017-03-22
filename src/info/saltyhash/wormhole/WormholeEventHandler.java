package info.saltyhash.wormhole;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/** Handles events for Wormhole. */
class WormholeEventHandler implements Listener {
    private final Wormhole wormhole;
    private final EconManager econMgr;
    private final PlayerManager playerMgr;
    private final SignManager signMgr;
    
    WormholeEventHandler(Wormhole wormhole, EconManager econMgr,
            PlayerManager playerMgr, SignManager signMgr) {
        this.wormhole  = wormhole;
        this.econMgr   = econMgr;
        this.playerMgr = playerMgr;
        this.signMgr   = signMgr;
    }

    /** Handles when a player right-clicks a sign.  JUMP! */
    private void handleSignClick(PlayerInteractEvent event, Player player, Sign sign) {
        // Get jump related to this sign
        Jump jump = this.signMgr.getSignJump(sign);
        // Return if no jump associated
        if (jump == null) return;
        
        // Cancel interact event
        event.setCancelled(true);
        
        // Make sure player can afford this action
        if (!player.hasPermission("wormhole.free")
                && !this.econMgr.hasBalance(player, "use")) {
            player.sendMessage(ChatColor.DARK_RED+
                "You cannot afford to use signs pointing to Jumps");
            return;
        }
        
        // Check permissions
        if (jump.isPublic()) {
            if (!player.hasPermission("wormhole.use.public")) {
                player.sendMessage(ChatColor.DARK_RED+
                    "You cannot use signs pointing to public Jumps");
                return;
            }
        }
        else if (jump.playerName.equals(player.getName())) {
            if (!player.hasPermission("wormhole.use.private")) {
                player.sendMessage(ChatColor.DARK_RED+
                    "You cannot use signs pointing to your Jumps");
                return;
            }
        }
        else {
            if (!player.hasPermission("wormhole.use.other")) {
                player.sendMessage(ChatColor.DARK_RED+
                    "You cannot use signs pointing to other players' Jumps");
                return;
            }
        }
        
        // Jump player
        Location from = player.getLocation();
        int result = jump.jumpPlayer(player);
        
        // Success
        if (result == 0) {
            player.sendMessage(ChatColor.DARK_PURPLE+"Jumped"+ChatColor.RESET+
                " to "+jump.getDescriptionForPlayer(player));
            
            // Charge player
            if (!player.hasPermission("wormhole.free"))
                this.econMgr.charge(player, "use");
        }
        
        // Failure
        else {
            player.sendMessage(ChatColor.DARK_RED+"Failed to jump; unknown reason");
            this.wormhole.getLogger().warning(String.format(
                "Player \"%s\" failed to Jump to %s; unknown reason",
                player.getName(), jump.getDescription()));
            return;
        }
        
        // Play teleport effect
        this.wormhole.playTeleportEffect(from);
        this.wormhole.playTeleportEffect(player.getLocation());
    }

    /** Called when a player breaks a block. */
    @EventHandler(priority=EventPriority.NORMAL, ignoreCancelled=true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block   = event.getBlock();
        
        // Handle a sign [post] break event
        if (block.getState() instanceof Sign) {
            Sign sign = (Sign)block.getState();
            
            // Get jump destination from sign
            Jump jump = this.signMgr.getSignJump(sign);
            // Check jump
            if (jump == null) return;
            
            // Make sure player can afford this action
            if (!player.hasPermission("wormhole.free")
                    && !this.econMgr.hasBalance(player, "unset")) {
                player.sendMessage(ChatColor.DARK_RED+
                    "You cannot afford to unset signs pointing to Jumps");
                event.setCancelled(true);
                return;
            }
            
            // Check permissions
            if (jump.isPublic()) {
                if (!player.hasPermission("wormhole.unset.public")) {
                    player.sendMessage(ChatColor.DARK_RED+
                        "You cannot unset signs pointing to public Jumps");
                    event.setCancelled(true);
                    return;
                }
            }
            else if (jump.playerName.equals(player.getName())) {
                if (!player.hasPermission("wormhole.unset.private")) {
                    player.sendMessage(ChatColor.DARK_RED+
                        "You cannot unset signs pointing to your Jumps");
                    event.setCancelled(true);
                    return;
                }
            }
            else {
                if (!player.hasPermission("wormhole.unset.other")) {
                    player.sendMessage(ChatColor.DARK_RED+
                        "You cannot unset signs pointing to other players' Jumps");
                    event.setCancelled(true);
                    return;
                }
            }
            
            // Delete sign
            int result = this.signMgr.delSignJump(sign);
            
            // Success
            if (result == 0) {
                player.sendMessage(ChatColor.DARK_GREEN+"Unset sign"+ChatColor.RESET+
                    " pointing to Jump "+jump.getDescriptionForPlayer(player));
                
                // Charge player
                if (!player.hasPermission("wormhole.free"))
                    this.econMgr.charge(player, "unset");
            }
            
            // Sign not set
            else if (result == 1);
            
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
    }

    /** Called when a player damages a block. */
    @EventHandler(priority=EventPriority.NORMAL)
    public void onBlockDamage(BlockDamageEvent event) {
        Player player = event.getPlayer();
        Block block   = event.getBlock();
        
        // Handle a sign [post] damage event
        if (block.getState() instanceof Sign) {
            Sign sign = (Sign)block.getState();
            
            // Get jump destination from sign
            Jump jump = this.signMgr.getSignJump(sign);
            // Check jump
            if (jump == null) return;
            
            // Check permissions
            if (jump.isPublic() && !player.hasPermission("wormhole.use.public")) return;
            else if (jump.playerName.equals(player.getName())
                    && !player.hasPermission("wormhole.use.private")) return;
            else if (!player.hasPermission("wormhole.use.other")) return;
            
            // Display Jump
            player.sendMessage(String.format(
                "%sSign is set%s to Jump %s",
                ChatColor.DARK_PURPLE, ChatColor.RESET,
                jump.getDescriptionForPlayer(player)));
        }
    }

    /** Called when player interacts with something. */
    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Block block   = event.getClickedBlock();
        
        // Right-click on a block
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            // Handle a right-click event on a sign [post]
            if (block.getState() instanceof Sign) {
                // Get sign
                Sign sign = (Sign)block.getState();
                // Handle sign click
                this.handleSignClick(event, player, sign);
            }
        }
    }

    /** Called when player is teleported. */
    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        
        // Set player's previous Jump (only if jumped by command or plugin)
        PlayerTeleportEvent.TeleportCause cause = event.getCause();
        if (cause == PlayerTeleportEvent.TeleportCause.COMMAND ||
            cause == PlayerTeleportEvent.TeleportCause.PLUGIN) {
            Location fm = event.getFrom();
            Location to = event.getTo();
            
            // Only set previous Jump if it's a significant distance away
            if (fm.getWorld().getName().equals(to.getWorld().getName()))
                if (fm.distance(to) < 10) return;
            
            // Set player's previous Jump
            Jump jumpFrom = new Jump();
            jumpFrom.setDest(event.getFrom());
            this.playerMgr.setLastJump(player, jumpFrom);
        }
    }
}