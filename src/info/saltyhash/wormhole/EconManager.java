package info.saltyhash.wormhole;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;

/** Economy manager for Wormhole. */
class EconManager {
    private final Wormhole wormhole;
    Economy econ = null;
    
    EconManager(Wormhole wormhole) {
        this.wormhole = wormhole;
        
        // Set up economy integration
        // Vault not found
        if (wormhole.getServer().getPluginManager().getPlugin("Vault") == null)
            wormhole.getLogger().warning(
                "Economy integration disabled; dependency \"Vault\" not found");
        // Vault found
        else {
            RegisteredServiceProvider<Economy> rsp =
                wormhole.getServer().getServicesManager().getRegistration(Economy.class);
            // No economy plugin found
            if (rsp == null)
                wormhole.getLogger().warning(
                    "Economy integration disabled; economy plugin not found");
            // Economy plugin found
            else {
                this.econ = rsp.getProvider();
                if (this.econ == null)
                    wormhole.getLogger().warning(
                        "Economy integration disabled; unknown reason");
            }
        }
    }
    
    /**
     * Charges player for specified action.
     * @param action add, back, del, jump, rename, replace, set, unset, use.
     * @return 0: Success; 1: Insufficient funds; 2: Failed to create account; 3: No economy support.
     */
    int charge(Player player, String action) {
        action = action.toLowerCase();
        FileConfiguration config = this.wormhole.getConfig();
        return this.charge(player, config.getDouble("cost."+action));
    }

    /**
     * Charges player for specified action.
     * @return 0: Success; 1: Insufficient funds; 2: Failed to create account; 3: No economy support.
     */
    int charge(Player player, double amount) {
        if (!this.isEnabled()) return 3;

        // Make sure player has an account
        if (!this.econ.hasAccount(player))
            if (!this.econ.createPlayerAccount(player)) return 2;
        
        // Withdraw from account
        if (amount > 0.0) {
            EconomyResponse result = this.econ.withdrawPlayer(player, amount);
            // Insufficient funds
            if (!result.transactionSuccess()) return 1;
            // Message player
            player.sendMessage(String.format(
                "%sCharged %s%s",
                ChatColor.DARK_RED, ChatColor.RESET, this.econ.format(amount)));
        }
        // Deposit to account
        else if (amount < 0.0) {
            amount = -amount;
            this.econ.depositPlayer(player, amount);
            // Message player
            player.sendMessage(String.format(
                "%sPaid %s%s",
                ChatColor.DARK_GREEN, ChatColor.RESET, this.econ.format(amount)));
        }
        return 0;
    }

    /**
     * @param action add, back, del, jump, replace, set, unset, use.
     * @return True if the player can afford the specified action.
     */
    boolean hasBalance(Player player, String action) {
        action = action.toLowerCase();
        FileConfiguration config = this.wormhole.getConfig();
        return this.hasBalance(player, config.getDouble("cost."+action));
    }

    /**
     * @return True if the player has given amount in their balance.
     */
    boolean hasBalance(Player player, double amount) {
        // This is kind of hackish; this aught to return false if not enabled,
        // but for simplicity outside the scope of this class, it returns true.
        return !this.isEnabled() || this.econ.has(player, amount);
    }

    /**
     * @return True if economy integration is enabled.
     */
    boolean isEnabled() {
        return (this.econ != null);
    }
}