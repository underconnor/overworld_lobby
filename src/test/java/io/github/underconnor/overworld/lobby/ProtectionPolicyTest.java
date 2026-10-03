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
    void globalBypassAllowsEveryActionIncludingFoodAndPlayerDamage(Action action) {
        var policy = new ProtectionPolicy(() -> Settings.load(new YamlConfiguration()));
        Player operator = mock(Player.class);
        when(operator.hasPermission("overworld.lobby.bypass")).thenReturn(true);
        assertTrue(policy.bypasses(operator, action));
        assertFalse(policy.blocks(action, operator, mock(World.class)));
    }

    @ParameterizedTest @EnumSource(Action.class)
    void operatorAlwaysBypassesEvenWhenLuckPermsReturnsFalse(Action action) {
        var policy = new ProtectionPolicy(() -> Settings.load(new YamlConfiguration()));
        Player operator = mock(Player.class);
        when(operator.isOp()).thenReturn(true);
        when(operator.hasPermission("overworld.lobby.bypass")).thenReturn(false);
        assertTrue(policy.hasGlobalBypass(operator));
        assertTrue(policy.bypasses(operator, action));
        assertFalse(policy.blocks(action, operator, mock(World.class)));
        verify(operator, never()).hasPermission(action.permission());
    }

    @Test void pluginDefaultsGiveOperatorsFullBypassAndNeverGrantSpawnExemption() throws Exception {
        var metadata = new YamlConfiguration();
        metadata.options().pathSeparator('/');
        try (var source = new InputStreamReader(getClass().getResourceAsStream("/plugin.yml"), StandardCharsets.UTF_8)) {
            metadata.load(source);
        }
        assertEquals("op", metadata.get("permissions/overworld.lobby.bypass/default"));
        assertEquals(true, metadata.get("permissions/overworld.lobby.bypass/children/overworld.lobby.bypass.mode"));
        for (Action action : Action.values())
            assertEquals(true, metadata.get("permissions/overworld.lobby.bypass/children/" + action.permission()));
        assertFalse(metadata.contains("permissions/overworld.lobby.bypass.spawn"));
        assertFalse(metadata.contains("permissions/overworld.lobby.bypass/children/overworld.lobby.bypass.spawn"));
    }

    @Test void explicitIndividualDenialCannotOverrideGlobalBypass() {
        var policy = new ProtectionPolicy(() -> Settings.load(new YamlConfiguration()));
        Player player = mock(Player.class);
        when(player.hasPermission("overworld.lobby.bypass")).thenReturn(true);
        when(player.hasPermission(Action.PLAYER_DAMAGE.permission())).thenReturn(false);
        assertFalse(policy.blocks(Action.PLAYER_DAMAGE, player, mock(World.class)));
        assertFalse(policy.hasGlobalBypass(null));
    }
}
