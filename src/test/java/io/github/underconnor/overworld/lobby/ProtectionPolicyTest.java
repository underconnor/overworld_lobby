package io.github.underconnor.overworld.lobby;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class ProtectionPolicyTest {
    @Test void livePermissionsAllowOnlyGrantedAction() {
        var config = new YamlConfiguration();
        var current = new AtomicReference<>(Settings.load(config));
        var policy = new ProtectionPolicy(current::get);
        Player player = mock(Player.class);
        World world = mock(World.class);
        assertTrue(policy.blocks(Action.BLOCK_BREAK, player, world));
        when(player.hasPermission(Action.BLOCK_BREAK.permission())).thenReturn(true);
        assertFalse(policy.blocks(Action.BLOCK_BREAK, player, world));
        assertTrue(policy.blocks(Action.BLOCK_PLACE, player, world));
        when(player.hasPermission(Action.BLOCK_BREAK.permission())).thenReturn(false);
        assertTrue(policy.blocks(Action.BLOCK_BREAK, player, world));
        when(player.hasPermission("overworld.lobby.bypass")).thenReturn(true);
        assertFalse(policy.blocks(Action.BLOCK_PLACE, player, world));
        config.set("enabled", false);
        current.set(Settings.load(config));
        assertFalse(policy.blocks(EnvironmentRule.EXPLOSIONS, world));
    }

    @Test void environmentalChangesHaveNoPlayerPermissionOverride() {
        var policy = new ProtectionPolicy(() -> Settings.load(new YamlConfiguration()));
        assertTrue(policy.blocks(EnvironmentRule.FIRE, mock(World.class)));
        assertTrue(policy.blocks(Action.BLOCK_BREAK, null, mock(World.class)));
    }
}
