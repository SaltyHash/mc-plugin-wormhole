package info.saltyhash.wormhole;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

class Jump implements Cloneable {
    String playerName;
    String jumpName;
    String worldName;
    double x, y, z;
    float  yaw;

    /** @return A clone of this Jump. */
    @Override
    public Jump clone() {
        Jump jump = new Jump();
        jump.playerName = this.playerName;
        jump.jumpName   = this.jumpName;
        jump.worldName  = this.worldName;
        jump.x   = this.x;
        jump.y   = this.y;
        jump.z   = this.z;
        jump.yaw = this.yaw;
        return jump;
    }

    /** @return True if the given object is equivalent to this Jump. */
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Jump)) return false;
        Jump j = (Jump)obj;
        if (!j.playerName.equals(this.playerName)) return false;
        if (!j.jumpName.equals(this.jumpName)) return false;
        if (!j.worldName.equals(this.jumpName)) return false;
        if (j.getDest().distance(this.getDest()) > 0.01) return false;
        return true;
    }

    /** @return General description of this Jump. */
    String getDescription() {
        if (this.isPublic())
            return "\""+this.jumpName+"\" (Public)";
        else
            return "\""+this.jumpName+"\" ("+this.playerName+")";
    }

    /** @return Description of this Jump for the given player. */
    String getDescriptionForPlayer(Player player) {
        if (this.isPublic())
            return "\""+this.jumpName+"\" (Public)";
        else if (this.playerName.equals(player.getName()))
            return "\""+this.jumpName+"\"";
        else
            return "\""+this.jumpName+"\" ("+this.playerName+")";
    }

    /** @return Location containing the Jump destination. */
    Location getDest() {
        World world = Bukkit.getServer().getWorld(worldName);
        return new Location(world, x, y, z, yaw, 0);
    }

    /** @return True if this is a private Jump. */
    boolean isPrivate() {
        return !playerName.isEmpty();
    }

    /** @return True if this is a public Jump (empty playerName). */
    boolean isPublic() {
        return playerName.isEmpty();
    }

    /**
     * Teleports the player to this jump.
     * @return 0: Success; 1: Jump parameters incomplete.
     */
    int jumpPlayer(Player player) {
        // Load destination chunk
        this.getDest().getWorld().getChunkAt(this.getDest()).load();
        // Teleport player
        player.teleport(this.getDest());
        return 0;
    }

    /** Sets the Jump destination with the given location. */
    void setDest(Location dest) {
        this.worldName = dest.getWorld().getName();
        this.x = dest.getX();
        this.y = dest.getY();
        this.z = dest.getZ();
        this.yaw = dest.getYaw();
    }

    /** Sets the Jump to public (by making playerName empty). */
    void setPublic() {
        this.playerName = "";
    }
}
