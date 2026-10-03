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
        if (player.hasPermission(action.permission())) return true;
        // The OP-default activity bypass lets staff edit the lobby. Keeping
        // players fed and safe remains independent of their editing authority.
        return action != Action.HUNGER && action != Action.PLAYER_DAMAGE
            && player.hasPermission("overworld.lobby.bypass");
    }
    public boolean blocks(EnvironmentRule rule, World world) {
        return protects(world) && settings().environmentRules().contains(rule);
    }
}
