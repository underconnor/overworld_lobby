package io.github.underconnor.overworld.lobby;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.GameMode;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlayerControllerTest {
    private final YamlConfiguration config = new YamlConfiguration();
    private final JavaPlugin plugin = mock(JavaPlugin.class);
    private final Server server = mock(Server.class);
    private final Player player = mock(Player.class);
    private final World lobby = mock(World.class);
    private final World outside = mock(World.class);
    private final Set<String> permissions = new HashSet<>();
    private final AtomicReference<GameMode> mode = new AtomicReference<>(GameMode.ADVENTURE);
    private final AtomicBoolean allowFlight = new AtomicBoolean();
    private final AtomicBoolean flying = new AtomicBoolean();
    private final AtomicReference<Float> speed = new AtomicReference<>(0.25f);
    private final AtomicInteger food = new AtomicInteger(7);
    private final AtomicReference<Float> saturation = new AtomicReference<>(2.5f);
    private final AtomicReference<Float> exhaustion = new AtomicReference<>(3.0f);
    private PlayerController controller;

    @BeforeEach void setup() {
        config.set("worlds", List.of("lobby"));
        when(lobby.getName()).thenReturn("lobby");
        when(outside.getName()).thenReturn("wild");
        when(player.getWorld()).thenReturn(lobby);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.hasPermission(anyString())).thenAnswer(call -> permissions.contains(call.getArgument(0)));
        when(player.getGameMode()).thenAnswer(call -> mode.get());
        when(player.getAllowFlight()).thenAnswer(call -> allowFlight.get());
        when(player.isFlying()).thenAnswer(call -> flying.get());
        when(player.getFlySpeed()).thenAnswer(call -> speed.get());
        doAnswer(call -> { mode.set(call.getArgument(0)); return null; }).when(player).setGameMode(any());
        doAnswer(call -> { allowFlight.set(call.getArgument(0)); return null; }).when(player).setAllowFlight(anyBoolean());
        doAnswer(call -> { flying.set(call.getArgument(0)); return null; }).when(player).setFlying(anyBoolean());
        doAnswer(call -> { speed.set(call.getArgument(0)); return null; }).when(player).setFlySpeed(anyFloat());
        when(player.getFoodLevel()).thenAnswer(call -> food.get());
        when(player.getSaturation()).thenAnswer(call -> saturation.get());
        when(player.getExhaustion()).thenAnswer(call -> exhaustion.get());
        doAnswer(call -> { food.set(call.getArgument(0)); return null; }).when(player).setFoodLevel(anyInt());
        doAnswer(call -> { saturation.set(call.getArgument(0)); return null; }).when(player).setSaturation(anyFloat());
        doAnswer(call -> { exhaustion.set(call.getArgument(0)); return null; }).when(player).setExhaustion(anyFloat());
        when(plugin.getServer()).thenReturn(server);
        doReturn(List.of(player)).when(server).getOnlinePlayers();
        controller = new PlayerController(plugin, new ProtectionPolicy(() -> Settings.load(config)));
    }

    @Test void joinsAsConfiguredSurvivalWithPermissionBasedFlightAndRestoresOnWorldExit() {
        permissions.add("overworld.lobby.fly");
        controller.refresh();
        assertEquals(GameMode.SURVIVAL, mode.get());
        assertTrue(allowFlight.get());
        assertEquals(0.1f, speed.get());
        when(player.getWorld()).thenReturn(outside);
        controller.onWorldChange(new PlayerChangedWorldEvent(player, lobby));
        assertEquals(GameMode.ADVENTURE, mode.get());
        assertFalse(allowFlight.get());
        assertEquals(0.25f, speed.get());
    }

    @Test void flightPermissionRevocationStopsActiveFlightAndGrantWorksOnRefresh() {
        permissions.add("overworld.lobby.fly");
        controller.refresh();
        assertTrue(controller.toggleFlight(player, true));
        assertTrue(flying.get());
        permissions.clear();
        controller.refresh();
        assertFalse(flying.get());
        assertFalse(allowFlight.get());
        verify(player).setFallDistance(0);
        permissions.add("overworld.lobby.fly");
        controller.refresh();
        assertTrue(allowFlight.get());
        assertFalse(flying.get());
    }

    @Test void explicitFlightToggleStartsStopsAndRetainsSessionChoice() {
        permissions.add("overworld.lobby.fly");
        assertTrue(controller.toggleFlight(player, true));
        assertTrue(flying.get());
        assertTrue(controller.toggleFlight(player, false));
        assertFalse(flying.get());
        assertFalse(allowFlight.get());
        controller.refresh();
        assertFalse(allowFlight.get());
        assertTrue(controller.toggleFlight(player, true));
        assertTrue(flying.get());
    }

    @Test void configurationRevocationStopsFlightAndRejectsCommand() {
        permissions.add("overworld.lobby.fly");
        controller.toggleFlight(player, true);
        config.set("players.allow-flight", false);
        controller.refresh();
        assertFalse(allowFlight.get());
        assertFalse(flying.get());
        assertFalse(controller.toggleFlight(player, true));
    }

    @Test void onlyModeBypassLeavesInitialOperatorModeAndFlightUntouched() {
        mode.set(GameMode.CREATIVE);
        allowFlight.set(true);
        flying.set(true);
        permissions.add("overworld.lobby.bypass.mode");
        controller.refresh();
        verify(player, never()).setGameMode(any());
        verify(player, never()).setAllowFlight(anyBoolean());
        verify(player, never()).setFlying(anyBoolean());
        assertFalse(controller.toggleFlight(player, false));
        permissions.clear();
        permissions.add("overworld.lobby.bypass");
        controller.refresh();
        assertEquals(GameMode.SURVIVAL, mode.get());
        assertFalse(allowFlight.get());
        assertFalse(flying.get());
        assertEquals(20, food.get());
        assertEquals(20f, saturation.get());
    }

    @Test void shutdownRestoresPriorModeAllowFlightSpeedAndFlyingState() {
        mode.set(GameMode.CREATIVE);
        allowFlight.set(true);
        flying.set(true);
        controller.refresh();
        assertEquals(GameMode.SURVIVAL, mode.get());
        assertFalse(flying.get());
        controller.close();
        assertEquals(GameMode.CREATIVE, mode.get());
        assertTrue(allowFlight.get());
        assertTrue(flying.get());
        assertEquals(0.25f, speed.get());
    }

    @Test void modeChangesRequireModeBypassAndDeniedFlightCannotStart() {
        PlayerGameModeChangeEvent change = new PlayerGameModeChangeEvent(player, GameMode.CREATIVE);
        controller.onGameModeChange(change);
        assertTrue(change.isCancelled());
        PlayerToggleFlightEvent flight = new PlayerToggleFlightEvent(player, true);
        controller.onToggleFlight(flight);
        assertTrue(flight.isCancelled());
        permissions.add("overworld.lobby.bypass.mode");
        PlayerGameModeChangeEvent allowed = new PlayerGameModeChangeEvent(player, GameMode.CREATIVE);
        controller.onGameModeChange(allowed);
        assertFalse(allowed.isCancelled());
    }

    @Test void excludingWorldOrDisablingPluginRestoresPlayerSettings() {
        controller.refresh();
        config.set("enabled", false);
        controller.refresh();
        assertEquals(GameMode.ADVENTURE, mode.get());
        assertEquals(0.25f, speed.get());
        clearInvocations(player);
        controller.refresh();
        verify(player, never()).setGameMode(any());
    }

    @Test void creativeAndSpectatorConfigRetainVanillaFlight() {
        config.set("players.game-mode", "SPECTATOR");
        config.set("players.allow-flight", false);
        controller.refresh();
        assertEquals(GameMode.SPECTATOR, mode.get());
        assertTrue(allowFlight.get());
        assertFalse(controller.toggleFlight(player, false));
    }

    @Test void protectedPlayerReceivesFullFoodAndSaturationAndRestoresOriginalValuesOnExit() {
        controller.refresh();
        assertEquals(20, food.get());
        assertEquals(20f, saturation.get());
        assertEquals(0f, exhaustion.get());
        food.set(19);
        saturation.set(15f);
        exhaustion.set(2f);
        controller.refresh();
        assertEquals(20, food.get());
        assertEquals(20f, saturation.get());
        assertEquals(0f, exhaustion.get());
        when(player.getWorld()).thenReturn(outside);
        controller.refresh();
        assertEquals(7, food.get());
        assertEquals(2.5f, saturation.get());
        assertEquals(3f, exhaustion.get());
    }

    @Test void disablingFoodFillOrGrantingHungerBypassRestoresFoodWithoutReleasingMode() {
        controller.refresh();
        config.set("players.keep-food-full", false);
        controller.refresh();
        assertEquals(7, food.get());
        assertEquals(GameMode.SURVIVAL, mode.get());
        food.set(11);
        saturation.set(4f);
        config.set("players.keep-food-full", true);
        controller.refresh();
        assertEquals(20, food.get());
        permissions.add("overworld.lobby.bypass.hunger");
        controller.refresh();
        assertEquals(11, food.get());
        assertEquals(4f, saturation.get());
        assertEquals(GameMode.SURVIVAL, mode.get());
    }

    @Test void hungerProtectionDisabledLeavesFoodUntouchedUntilReenabled() {
        config.set("protection.hunger", false);
        controller.refresh();
        verify(player, never()).setFoodLevel(anyInt());
        verify(player, never()).setSaturation(anyFloat());
        verify(player, never()).setExhaustion(anyFloat());
        config.set("protection.hunger", true);
        permissions.add("overworld.lobby.bypass.mode");
        controller.refresh();
        assertEquals(20, food.get());
    }

    @Test void modeAndActivityBypassKeepFoodProtectionWhileHungerBypassRestoresFood() {
        mode.set(GameMode.CREATIVE);
        allowFlight.set(true);
        permissions.add("overworld.lobby.bypass.mode");
        controller.refresh();
        assertEquals(GameMode.CREATIVE, mode.get());
        assertTrue(allowFlight.get());
        verify(player, never()).setGameMode(any());
        verify(player, never()).setAllowFlight(anyBoolean());
        assertEquals(20, food.get());
        assertEquals(20f, saturation.get());
        assertEquals(0f, exhaustion.get());
        permissions.add("overworld.lobby.bypass.hunger");
        controller.refresh();
        assertEquals(7, food.get());
        assertEquals(2.5f, saturation.get());
        permissions.remove("overworld.lobby.bypass.hunger");
        controller.refresh();
        assertEquals(20, food.get());
        permissions.add("overworld.lobby.bypass");
        controller.refresh();
        assertEquals(20, food.get());
        assertEquals(20f, saturation.get());
        assertEquals(0f, exhaustion.get());
    }

    @Test void operatorActivityBypassKeepsConfiguredSurvivalFlightAndFullFood() {
        permissions.add("overworld.lobby.bypass");
        permissions.add("overworld.lobby.fly");
        mode.set(GameMode.CREATIVE);
        controller.refresh();
        assertEquals(GameMode.SURVIVAL, mode.get());
        assertTrue(allowFlight.get());
        assertEquals(20, food.get());
        assertEquals(20f, saturation.get());
        assertEquals(0f, exhaustion.get());
        assertTrue(controller.toggleFlight(player, true));
        assertTrue(flying.get());
    }

    @Test void shutdownRestoresOriginalFoodRatherThanExportingLobbySaturation() {
        controller.refresh();
        assertEquals(20, food.get());
        controller.close();
        assertEquals(7, food.get());
        assertEquals(2.5f, saturation.get());
        assertEquals(3f, exhaustion.get());
    }
}
