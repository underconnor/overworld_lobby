package io.github.underconnor.overworld.lobby;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

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

    @ParameterizedTest @EnumSource(Action.class)
    void operatorActivityBypassKeepsFoodAndEnvironmentalDamageProtection(Action action) {
        var policy = new ProtectionPolicy(() -> Settings.load(new YamlConfiguration()));
        Player operator = mock(Player.class);
        when(operator.hasPermission("overworld.lobby.bypass")).thenReturn(true);
        boolean keepsProtection = action == Action.HUNGER || action == Action.PLAYER_DAMAGE;
        assertEquals(!keepsProtection, policy.bypasses(operator, action));
        if (keepsProtection) {
            when(operator.hasPermission(action.permission())).thenReturn(true);
            assertTrue(policy.bypasses(operator, action));
        }
    }

    @Test void explicitPermissionFalseCanDenyOperatorWithoutHardcodedOpBypass() {
        var policy = new ProtectionPolicy(() -> Settings.load(new YamlConfiguration()));
        Player operator = mock(Player.class);
        when(operator.isOp()).thenReturn(true);
        when(operator.hasPermission("overworld.lobby.bypass")).thenReturn(false);
        assertTrue(policy.blocks(Action.BLOCK_BREAK, operator, mock(World.class)));
        when(operator.hasPermission(Action.BLOCK_BREAK.permission())).thenReturn(true);
        assertFalse(policy.blocks(Action.BLOCK_BREAK, operator, mock(World.class)));
        verify(operator, never()).isOp();
    }

    @Test void pluginDefaultsGiveOperatorsActivityBypassAndKeepSeparateExceptionsExplicit() throws Exception {
        var metadata = new YamlConfiguration();
        metadata.options().pathSeparator('/');
        try (var source = new InputStreamReader(getClass().getResourceAsStream("/plugin.yml"), StandardCharsets.UTF_8)) {
            metadata.load(source);
        }
        assertEquals("op", metadata.get("permissions/overworld.lobby.bypass/default"));
        for (String exception : new String[]{"mode", "spawn", "hunger", "player-damage"}) {
            assertEquals(false, metadata.get("permissions/overworld.lobby.bypass." + exception + "/default"));
        }
    }
}
