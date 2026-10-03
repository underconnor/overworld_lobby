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
        return player != null && (player.hasPermission("overworld.lobby.bypass")
            || player.hasPermission(action.permission()));
    }
    public boolean blocks(EnvironmentRule rule, World world) {
        return protects(world) && settings().environmentRules().contains(rule);
    }
}
