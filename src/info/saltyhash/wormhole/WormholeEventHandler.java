package info.saltyhash.wormhole;

import info.saltyhash.wormhole.persistence.JumpRecord;
import info.saltyhash.wormhole.persistence.PlayerRecord;
import info.saltyhash.wormhole.persistence.SignRecord;
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
import org.bukkit.event.player.PlayerLoginEvent;

/** Handles events for Wormhole. */
class WormholeEventHandler implements Listener {
    private final Wormhole    wormhole;
    private final EconManager econMgr;
    
    WormholeEventHandler(Wormhole wormhole, EconManager econMgr) {
        this.wormhole = wormhole;
        this.econMgr  = econMgr;
    }
    
    /** Handles when a player right-clicks a sign.  JUMP! */
    private void handleSignClick(PlayerInteractEvent event, Player player, Sign sign) {
        // Get sign record for this sign
        SignRecord signRecord = SignRecord.load(sign);
        if (signRecord == null) return;
        
        // Get jump related to this sign
        JumpRecord jump = signRecord.getJumpRecord();
        
        // Cancel interact event
        event.setCancelled(true);
        
        // Check permissions
        if (jump.isPublic()) {
            if (!player.hasPermission("wormhole.use.public")) {
                player.sendMessage(ChatColor.DARK_RED+
                    "You cannot use signs pointing to public jumps");
                return;
            }
        }
        else if (jump.belongsTo(player)) {
            if (!player.hasPermission("wormhole.use.private")) {
                player.sendMessage(ChatColor.DARK_RED+
                    "You cannot use signs pointing to your jumps");
                return;
            }
        }
        else {
            if (!player.hasPermission("wormhole.use.other")) {
                player.sendMessage(ChatColor.DARK_RED+
                    "You cannot use signs pointing to jumps that belong to other players");
                return;
            }
        }
        
        // Make sure player can afford this action
        if (!player.hasPermission("wormhole.free") && !econMgr.hasBalance(player, "use")) {
            player.sendMessage(ChatColor.DARK_RED+
                    "You cannot afford to use signs pointing to jumps");
            return;
        }
        
        // Get player's start location
        Location from = player.getLocation();
        
        // Teleport player failed?
        if (!jump.teleportPlayer(player)) {
            player.sendMessage(ChatColor.DARK_RED+"Failed to jump; unknown reason");
            wormhole.getLogger().warning(String.format(
                "Player \"%s\" failed to jump to %s; unknown reason",
                player.getName(), jump.getDescription()));
            return;
        }
        
        // Notify player of where they just jumped to
        player.sendMessage(ChatColor.DARK_PURPLE+"Jumped"+ChatColor.RESET+
                " to "+jump.getDescription(player));
        
        // Play teleport effect
        wormhole.playTeleportEffect(from);
        wormhole.playTeleportEffect(player.getLocation());
        
        // Store previous location
        PlayerManager.setPreviousLocation(player, from);
        
        // Charge player
        if (!player.hasPermission("wormhole.free"))
            econMgr.charge(player, "use");
    }
    
    /** Called when a player breaks a block. */
    @EventHandler(priority=EventPriority.NORMAL, ignoreCancelled=true)
    public void onBlockBreak(BlockBreakEvent event) {
        // Ignore event if the block is not a sign
        if (!(event.getBlock().getState() instanceof Sign)) return;
        Sign sign = (Sign) event.getBlock().getState();
        
        // Get sign record for the sign
        SignRecord signRecord = SignRecord.load(sign);
        if (signRecord == null) return;
        
        // Get jump destination of sign
        JumpRecord jump = signRecord.getJumpRecord();
        if (jump == null) return;   // TODO: This should never happen
        
        // Make sure player can afford this action
        Player player = event.getPlayer();
        if (!player.hasPermission("wormhole.free")
                && !econMgr.hasBalance(player, "unset")) {
            player.sendMessage(ChatColor.DARK_RED+
                "You cannot afford to unset signs pointing to jumps");
            event.setCancelled(true);
            return;
        }
        
        // Check permissions
        if (jump.isPublic()) {
            if (!player.hasPermission("wormhole.unset.public")) {
                player.sendMessage(ChatColor.DARK_RED+
                    "You cannot unset signs pointing to public jumps");
                event.setCancelled(true);
                return;
            }
        }
        else if (jump.belongsTo(player)) {
            if (!player.hasPermission("wormhole.unset.private")) {
                player.sendMessage(ChatColor.DARK_RED+
                    "You cannot unset signs pointing to your jumps");
                event.setCancelled(true);
                return;
            }
        }
        else {
            if (!player.hasPermission("wormhole.unset.other")) {
                player.sendMessage(ChatColor.DARK_RED+
                    "You cannot unset signs pointing to jumps that belong to other players");
                event.setCancelled(true);
                return;
            }
        }
        
        // Delete sign succeeded?
        if (signRecord.delete()) {
            player.sendMessage(ChatColor.DARK_GREEN+"Unset sign"+ChatColor.RESET+
                " pointing to jump "+jump.getDescription(player));
            
            // Charge player
            if (!player.hasPermission("wormhole.free"))
                econMgr.charge(player, "unset");
        }
        // Failed?
        else {
            player.sendMessage(ChatColor.DARK_RED+
                "Failed to unset sign; unknown reason");
            wormhole.getLogger().warning(String.format(
                "Player \"%s\" failed to unset sign (%s, %d, %d, %d) "+
                "pointing to jump %s; unknown reason",
                player.getName(), sign.getWorld().getName(), sign.getX(),
                sign.getY(), sign.getZ(), jump.getDescription()));
        }
    }
    
    /** Called when a player damages a block. */
    @EventHandler(priority=EventPriority.NORMAL)
    public void onBlockDamage(BlockDamageEvent event) {
        // Ignore if event isn't involving a sign
        if (!(event.getBlock().getState() instanceof Sign)) return;
        Sign sign = (Sign) event.getBlock().getState();
        
        // Get sign record of the sign
        SignRecord signRecord = SignRecord.load(sign);
        if (signRecord == null) return;
        
        // Get jump record of the sign record
        JumpRecord jumpRecord = signRecord.getJumpRecord();
        if (jumpRecord == null) return;     // TODO: This should never happen
        
        // Check permissions
        Player player = event.getPlayer();
        if (jumpRecord.isPublic()) {
            if (!player.hasPermission("wormhole.use.public")) return;
        }
        else if (jumpRecord.belongsTo(player)) {
            if (!player.hasPermission("wormhole.use.private")) return;
        }
        else {
            if (!player.hasPermission("wormhole.use.other")) return;
        }
        
        // Display jump
        player.sendMessage(ChatColor.DARK_PURPLE + "Sign is set" + ChatColor.RESET +
                " to jump " + jumpRecord.getDescription(player));
    }

    /** Called when player interacts with something. */
    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Block block   = event.getClickedBlock();
        
        // Right-clicked on a sign block?
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && block.getState() instanceof Sign) {
            // Handle sign click
            handleSignClick(event, player, (Sign) block.getState());
        }
    }
    
    /** Called when a player logs into the server. */
    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onPlayerLogin(PlayerLoginEvent event) {
        Player player   = event.getPlayer();
        PlayerRecord pr = PlayerRecord.load(player.getUniqueId());
        
        // Player already exists in database?
        if (pr != null) {
            // Player usernames do not match (player changed their username)?
            if (!pr.username.equals(player.getName())) {
                // Update username and save
                pr.username = player.getName();
                if (!pr.save()) {
                    wormhole.getLogger().warning("Failed to save player '" + player.getName() +
                            "' to the database");
                }
            }
        }
        // Player record does not exist?
        else {
            // Create new player record and save
            pr = new PlayerRecord(player);
            if (!pr.save()) {
                wormhole.getLogger().warning("Failed to save player '" + player.getName() +
                        "' to the database");
            }
        }
    }
}