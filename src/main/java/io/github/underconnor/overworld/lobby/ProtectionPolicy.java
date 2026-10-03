package io.github.underconnor.overworld.lobby;

import java.util.function.Supplier;
import org.bukkit.World;
import org.bukkit.entity.Player;

public final class ProtectionPolicy {
    private final Supplier<Settings> settings;
    public ProtectionPolicy(Supplier<Settings> settings) { this.settings = settings; }
    public Settings settings() { return settings.get(); }
    public boolean protects(World world) { return settings().protects(world); }
    public boolean blocks(Action action, Player player, World world) {
        return protects(world) && settings().protections().contains(action)
            && !bypasses(player, action);
    }
    public boolean bypasses(Player player, Action action) {
        if (player == null) return false;
        return hasGlobalBypass(player) || player.hasPermission(action.permission());
    }
    /** OP is an explicit override even when a permission provider returns false for this node. */
    public boolean hasGlobalBypass(Player player) {
        return player != null && (player.isOp() || player.hasPermission("overworld.lobby.bypass"));
    }
    public boolean blocks(EnvironmentRule rule, World world) {
        return protects(world) && settings().environmentRules().contains(rule);
    }
}
