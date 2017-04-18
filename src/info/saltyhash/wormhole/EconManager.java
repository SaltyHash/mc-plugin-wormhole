package info.saltyhash.wormhole;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;

/** Economy manager for Wormhole. */
@SuppressWarnings("WeakerAccess")
final class EconManager {
    private static Wormhole wormhole;
    static Economy econ;
    
    private EconManager() {}
    
    static void setup(Wormhole wormhole) {
        EconManager.wormhole = wormhole;
        
        // Set up economy integration
        EconManager.econ = null;
        // Vault not found?
        if (Bukkit.getPluginManager().getPlugin("Vault") == null)
            wormhole.getLogger().info(
                "Economy integration disabled; dependency 'Vault' not found");
        // Vault found?
        else {
            RegisteredServiceProvider<Economy> rsp =
                Bukkit.getServicesManager().getRegistration(Economy.class);
            // No economy plugin found?
            if (rsp == null)
                wormhole.getLogger().info(
                    "Economy integration disabled; economy plugin not found");
            // Economy plugin found?
            else {
                EconManager.econ = rsp.getProvider();
                if (EconManager.econ == null)
                    wormhole.getLogger().warning("Economy integration disabled; unknown reason");
            }
        }
    }
    
    /**
     * Charges player for specified action.
     * @param  action add, back, del, jump, rename, replace, set, unset, use.
     * @return 0: Success; 1: Insufficient funds; 2: Failed to create account; 3: No economy support.
     */
    @SuppressWarnings("UnusedReturnValue")
    static int charge(Player player, String action) {
        return charge(player, wormhole.getConfig().getDouble("cost."+action.toLowerCase()));
    }

    /**
     * Charges player for specified amount.
     * @return 0: Success; 1: Insufficient funds; 2: Failed to create account; 3: No economy support.
     */
    static int charge(Player player, double amount) {
        if (!isEnabled()) return 3;

        // Make sure player has an account
        if (!econ.hasAccount(player))
            if (!econ.createPlayerAccount(player)) return 2;
        
        // Withdraw from account?
        if (amount > 0.0) {
            EconomyResponse result = econ.withdrawPlayer(player, amount);
            // Insufficient funds?
            if (!result.transactionSuccess()) return 1;
            // Message player
            player.sendMessage(ChatColor.RED + "Charged " + ChatColor.RESET +
                    econ.format(amount));
        }
        // Deposit to account?
        else if (amount < 0.0) {
            amount = -amount;
            econ.depositPlayer(player, amount);
            // Message player
            player.sendMessage(ChatColor.DARK_GREEN + "Paid " + ChatColor.RESET +
                    econ.format(amount));
        }
        return 0;
    }

    /**
     * @param action add, back, del, jump, replace, set, unset, use.
     * @return True if the player can afford the specified action.
     */
    static boolean hasBalance(Player player, String action) {
        return hasBalance(player, wormhole.getConfig().getDouble("cost."+action.toLowerCase()));
    }

    /**
     * @return True if the player has given amount in their balance.
     */
    static boolean hasBalance(Player player, double amount) {
        // This is kind of hackish; this aught to return false if not enabled,
        // but for simplicity outside the scope of this class, it returns true.
        return !isEnabled() || econ.has(player, amount);
    }

    /**
     * @return True if economy integration is enabled.
     */
    static boolean isEnabled() {
        return (econ != null);
    }
}