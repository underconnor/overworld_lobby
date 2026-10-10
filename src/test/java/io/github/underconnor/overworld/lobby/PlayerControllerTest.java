package io.github.underconnor.overworld.lobby;

import java.util.HashSet;
import java.util.ArrayList;
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
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlayerControllerTest {
    private final YamlConfiguration config = new YamlConfiguration();
    private final JavaPlugin plugin = mock(JavaPlugin.class);
    private final Server server = mock(Server.class);
    private final BukkitScheduler scheduler = mock(BukkitScheduler.class);
    private final List<Runnable> pendingDefaults = new ArrayList<>();
    private final List<Runnable> pendingRechecks = new ArrayList<>();
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
        doAnswer(call -> {
            GameMode target = call.getArgument(0);
            mode.set(target);
            // Vanilla applies these flags after PlayerGameModeChangeEvent has completed.
            allowFlight.set(target == GameMode.CREATIVE || target == GameMode.SPECTATOR);
            if (target == GameMode.SURVIVAL || target == GameMode.ADVENTURE) flying.set(false);
            if (target == GameMode.SPECTATOR) flying.set(true);
            return null;
        }).when(player).setGameMode(any());
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
        when(server.getScheduler()).thenReturn(scheduler);
        when(player.isOnline()).thenReturn(true);
        when(scheduler.runTaskLater(eq(plugin), any(Runnable.class), eq(2L))).thenAnswer(call -> {
            pendingDefaults.add(call.getArgument(1));
            return mock(BukkitTask.class);
        });
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(call -> {
            pendingRechecks.add(call.getArgument(1));
            return mock(BukkitTask.class);
        });
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

    @Test void modeAndGlobalBypassLeaveModeAndFlightUnrestricted() {
        mode.set(GameMode.CREATIVE);
        allowFlight.set(true);
        flying.set(true);
        permissions.add("overworld.lobby.bypass.mode");
        controller.refresh();
        verify(player, never()).setGameMode(any());
        assertTrue(allowFlight.get());
        verify(player, never()).setFlying(anyBoolean());
        assertFalse(controller.toggleFlight(player, false));
        permissions.clear();
        permissions.add("overworld.lobby.bypass");
        controller.refresh();
        assertEquals(GameMode.CREATIVE, mode.get());
        assertTrue(allowFlight.get());
        assertTrue(flying.get());
        assertEquals(20, food.get());
        assertEquals(20f, saturation.get());
        assertEquals(0f, exhaustion.get());
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

    @Test void disablingFoodFillRestoresFoodButLegacyHungerPermissionDoesNotExempt() {
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
        assertEquals(20, food.get());
        assertEquals(20f, saturation.get());
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

    @Test void modeHungerAndGlobalBypassAllKeepFoodProtection() {
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
        assertEquals(20, food.get());
        assertEquals(20f, saturation.get());
        permissions.remove("overworld.lobby.bypass.hunger");
        controller.refresh();
        assertEquals(20, food.get());
        permissions.add("overworld.lobby.bypass");
        controller.refresh();
        assertEquals(20, food.get());
        assertEquals(20f, saturation.get());
        assertEquals(0f, exhaustion.get());
    }

    @Test void operatorWithDeniedLuckPermsNodeDefaultsToCreativeOnceAfterJoin() {
        when(player.isOp()).thenReturn(true);
        mode.set(GameMode.SURVIVAL);
        PlayerJoinEvent join = mock(PlayerJoinEvent.class);
        when(join.getPlayer()).thenReturn(player);
        controller.onJoin(join);
        assertEquals(GameMode.SURVIVAL, mode.get());
        assertEquals(1, pendingDefaults.size());
        assertTrue(allowFlight.get());
        verify(player).setFoodLevel(20);
        pendingDefaults.getFirst().run();
        assertEquals(GameMode.CREATIVE, mode.get());
        PlayerGameModeChangeEvent manual = new PlayerGameModeChangeEvent(player, GameMode.ADVENTURE);
        controller.onGameModeChange(manual);
        assertFalse(manual.isCancelled());
        mode.set(GameMode.ADVENTURE);
        controller.refresh();
        assertEquals(GameMode.ADVENTURE, mode.get());
        assertEquals(1, pendingDefaults.size());
        assertEquals(20, food.get());
        assertEquals(20f, saturation.get());
        assertEquals(0f, exhaustion.get());
        assertTrue(controller.toggleFlight(player, true));
    }

    @Test void globalAndOperatorFlightCommandsBypassDisabledConfigAndPermissionWithoutRefreshRevertingChoice() {
        config.set("players.allow-flight", false);
        permissions.add("overworld.lobby.bypass");
        controller.refresh();
        pendingDefaults.getFirst().run();
        for (boolean operator : new boolean[]{false, true}) {
            when(player.isOp()).thenReturn(operator);
            if (operator) permissions.clear();
            mode.set(GameMode.SURVIVAL);
            assertTrue(controller.toggleFlight(player, true));
            assertTrue(allowFlight.get());
            assertTrue(flying.get());
            controller.refresh();
            assertEquals(GameMode.SURVIVAL, mode.get());
            assertTrue(allowFlight.get());
            assertTrue(flying.get());
            assertTrue(controller.toggleFlight(player, false));
            assertFalse(allowFlight.get());
            assertFalse(flying.get());
            controller.refresh();
            assertFalse(allowFlight.get());
            assertFalse(flying.get());
        }
        mode.set(GameMode.CREATIVE);
        assertTrue(controller.toggleFlight(player, false));
        assertTrue(allowFlight.get());
        assertFalse(flying.get());
        verify(player, atLeastOnce()).setFallDistance(0);
    }

    @Test void newlyGrantedGlobalBypassRestoresStateBeforeCreativeAndRevocationReappliesNormalRules() {
        permissions.add("overworld.lobby.fly");
        controller.refresh();
        assertEquals(GameMode.SURVIVAL, mode.get());
        assertEquals(20, food.get());
        permissions.add("overworld.lobby.bypass");
        controller.refresh();
        assertEquals(GameMode.ADVENTURE, mode.get());
        assertTrue(allowFlight.get());
        assertEquals(20, food.get());
        assertEquals(20f, saturation.get());
        assertEquals(0.25f, speed.get());
        pendingDefaults.getFirst().run();
        assertEquals(GameMode.CREATIVE, mode.get());
        mode.set(GameMode.ADVENTURE);
        controller.refresh();
        assertEquals(GameMode.ADVENTURE, mode.get());
        assertEquals(1, pendingDefaults.size());
        permissions.remove("overworld.lobby.bypass");
        controller.refresh();
        assertEquals(GameMode.SURVIVAL, mode.get());
        assertTrue(allowFlight.get());
        assertEquals(20, food.get());
        assertEquals(20f, saturation.get());
    }

    @Test void revokedPermissionOrShutdownCancelsPendingCreativeDefault() {
        permissions.add("overworld.lobby.bypass");
        controller.refresh();
        permissions.remove("overworld.lobby.bypass");
        pendingDefaults.getFirst().run();
        assertEquals(GameMode.ADVENTURE, mode.get());
        permissions.add("overworld.lobby.bypass");
        controller.close();
        pendingDefaults.getFirst().run();
        assertEquals(GameMode.ADVENTURE, mode.get());
    }

    @Test void shutdownRestoresOriginalFoodRatherThanExportingLobbySaturation() {
        controller.refresh();
        assertEquals(20, food.get());
        controller.close();
        assertEquals(7, food.get());
        assertEquals(2.5f, saturation.get());
        assertEquals(3f, exhaustion.get());
    }

    @Test void operatorSurvivalStillRefillsFoodAndConfigOrWorldExitRestoresOriginalFood() {
        when(player.isOp()).thenReturn(true);
        controller.refresh();
        pendingDefaults.getFirst().run();
        mode.set(GameMode.SURVIVAL);
        food.set(6);
        saturation.set(0f);
        exhaustion.set(4f);
        controller.refresh();
        assertEquals(GameMode.SURVIVAL, mode.get());
        assertEquals(20, food.get());
        assertEquals(20f, saturation.get());
        assertEquals(0f, exhaustion.get());
        config.set("players.keep-food-full", false);
        controller.refresh();
        assertEquals(7, food.get());
        assertEquals(2.5f, saturation.get());
        assertEquals(3f, exhaustion.get());
        assertEquals(GameMode.SURVIVAL, mode.get());
        config.set("players.keep-food-full", true);
        controller.refresh();
        assertEquals(20, food.get());
        when(player.getWorld()).thenReturn(outside);
        controller.refresh();
        assertEquals(7, food.get());
        assertEquals(2.5f, saturation.get());
        assertEquals(3f, exhaustion.get());
        assertEquals(GameMode.SURVIVAL, mode.get());
    }

    @ParameterizedTest @CsvSource({"OP, SURVIVAL", "OP, ADVENTURE", "GLOBAL, SURVIVAL", "GLOBAL, ADVENTURE"})
    void permittedNativeGameModeChangesRecoverFlightNextTickWithoutChangingModeOrSpeed(String source, GameMode requested) {
        bypass(source);
        controller.refresh();
        pendingDefaults.getFirst().run();
        assertEquals(GameMode.CREATIVE, mode.get());
        nativeModeChange(requested);
        assertFalse(allowFlight.get());
        runRechecks();
        assertEquals(requested, mode.get());
        assertTrue(allowFlight.get());
        assertFalse(flying.get());
        speed.set(0.4f);
        controller.refresh();
        assertEquals(0.4f, speed.get());
        assertEquals(1, pendingDefaults.size());
    }

    @ParameterizedTest @ValueSource(strings = {"OP", "GLOBAL"})
    void staffFlightRepairsExternalRespawnAndWorldChangeResetsWithoutForcingFlying(String source) {
        bypass(source);
        controller.refresh();
        pendingDefaults.getFirst().run();
        nativeModeChange(GameMode.SURVIVAL);
        runRechecks();
        controller.onRespawn(respawn());
        allowFlight.set(false);
        flying.set(false);
        runRechecks();
        assertTrue(allowFlight.get());
        assertFalse(flying.get());
        controller.onWorldChange(new PlayerChangedWorldEvent(player, lobby));
        allowFlight.set(false);
        runRechecks();
        assertTrue(allowFlight.get());
        allowFlight.set(false);
        controller.refresh();
        assertTrue(allowFlight.get());
        assertFalse(flying.get());
        assertEquals(GameMode.SURVIVAL, mode.get());
    }

    @ParameterizedTest @ValueSource(strings = {"OP", "GLOBAL", "MODE"})
    void explicitFlightOffSurvivesModeRespawnWorldChangeAndPeriodicRechecks(String source) {
        bypass(source);
        controller.refresh();
        if (!pendingDefaults.isEmpty()) pendingDefaults.getFirst().run();
        nativeModeChange(GameMode.SURVIVAL);
        runRechecks();
        assertTrue(controller.toggleFlight(player, true));
        assertTrue(controller.toggleFlight(player, false));
        nativeModeChange(GameMode.ADVENTURE);
        controller.onRespawn(respawn());
        controller.onWorldChange(new PlayerChangedWorldEvent(player, lobby));
        allowFlight.set(true); // A later plugin must not erase the explicit OFF choice either.
        runRechecks();
        controller.refresh();
        assertEquals(GameMode.ADVENTURE, mode.get());
        assertFalse(allowFlight.get());
        assertFalse(flying.get());
        PlayerToggleFlightEvent forgedStart = new PlayerToggleFlightEvent(player, true);
        controller.onToggleFlight(forgedStart);
        assertTrue(forgedStart.isCancelled());
    }

    @Test void modeOnlyBypassGetsPermissionBasedFlightWithoutModeOrSpeedEnforcement() {
        permissions.add("overworld.lobby.bypass.mode");
        controller.refresh();
        assertEquals(GameMode.ADVENTURE, mode.get());
        assertFalse(allowFlight.get());
        assertFalse(controller.toggleFlight(player, true));
        permissions.add("overworld.lobby.fly");
        controller.refresh();
        assertTrue(allowFlight.get());
        assertTrue(controller.toggleFlight(player, true));
        assertTrue(flying.get());
        assertEquals(0.25f, speed.get());
        verify(player, never()).setGameMode(any());
        verify(player, never()).setFlySpeed(anyFloat());
        permissions.remove("overworld.lobby.fly");
        controller.refresh();
        assertFalse(allowFlight.get());
        assertFalse(flying.get());
        permissions.add("overworld.lobby.fly");
        controller.refresh();
        assertTrue(allowFlight.get());
        assertFalse(flying.get());
        config.set("players.allow-flight", false);
        controller.refresh();
        assertFalse(allowFlight.get());
        assertFalse(flying.get());
        assertFalse(controller.toggleFlight(player, true));
        nativeModeChange(GameMode.CREATIVE);
        runRechecks();
        assertTrue(allowFlight.get());
        permissions.remove("overworld.lobby.fly");
        controller.refresh();
        assertTrue(allowFlight.get());
    }

    @ParameterizedTest @ValueSource(strings = {"OP", "GLOBAL", "MODE"})
    void leavingProtectionRestoresFlightSnapshotAndReentryStartsAFreshChoice(String source) {
        bypass(source);
        controller.refresh();
        if (!pendingDefaults.isEmpty()) pendingDefaults.getFirst().run();
        nativeModeChange(GameMode.SURVIVAL);
        runRechecks();
        assertTrue(controller.toggleFlight(player, false));
        when(player.getWorld()).thenReturn(outside);
        controller.onWorldChange(new PlayerChangedWorldEvent(player, lobby));
        runRechecks();
        assertEquals(GameMode.SURVIVAL, mode.get());
        assertFalse(allowFlight.get());
        assertEquals(0.25f, speed.get());
        when(player.getWorld()).thenReturn(lobby);
        controller.onWorldChange(new PlayerChangedWorldEvent(player, outside));
        assertTrue(allowFlight.get());
        assertFalse(flying.get());
    }

    @Test void permissionHandoverRestoresAndDropsOldFlightChoicesBeforeApplyingNewEligibility() {
        permissions.add("overworld.lobby.bypass");
        controller.refresh();
        assertTrue(controller.toggleFlight(player, true));
        permissions.remove("overworld.lobby.bypass");
        permissions.add("overworld.lobby.bypass.mode");
        controller.refresh();
        assertEquals(GameMode.ADVENTURE, mode.get());
        assertFalse(allowFlight.get());
        assertFalse(flying.get());
        permissions.add("overworld.lobby.fly");
        controller.refresh();
        assertTrue(allowFlight.get());
        assertTrue(controller.toggleFlight(player, false));
        permissions.remove("overworld.lobby.bypass.mode");
        controller.refresh();
        assertEquals(GameMode.SURVIVAL, mode.get());
        assertTrue(allowFlight.get());
        assertFalse(flying.get());
    }

    @Test void shutdownRestoresStaffFlightAndQueuedRespawnCannotReenableIt() {
        when(player.isOp()).thenReturn(true);
        controller.refresh();
        pendingDefaults.getFirst().run();
        nativeModeChange(GameMode.SURVIVAL);
        runRechecks();
        assertTrue(controller.toggleFlight(player, true));
        controller.onRespawn(respawn());
        controller.close();
        assertFalse(allowFlight.get());
        assertFalse(flying.get());
        assertEquals(GameMode.SURVIVAL, mode.get());
        runRechecks();
        assertFalse(allowFlight.get());
        assertFalse(flying.get());
    }

    @Test void quitRestoresCustomFlightAndClearsOffChoiceBeforeNextJoin() {
        bypass("MODE");
        allowFlight.set(true);
        flying.set(true);
        controller.refresh();
        assertTrue(controller.toggleFlight(player, false));
        PlayerQuitEvent quit = mock(PlayerQuitEvent.class);
        when(quit.getPlayer()).thenReturn(player);
        controller.onQuit(quit);
        assertTrue(allowFlight.get());
        assertTrue(flying.get());
        assertEquals(7, food.get());
        allowFlight.set(false);
        flying.set(false);
        PlayerJoinEvent join = mock(PlayerJoinEvent.class);
        when(join.getPlayer()).thenReturn(player);
        controller.onJoin(join);
        assertTrue(allowFlight.get());
        assertFalse(flying.get());
    }

    @Test void creativeEntryDoesNotExportVanillaFlightToChosenSurvivalOutsideProtection() {
        bypass("MODE");
        mode.set(GameMode.CREATIVE);
        allowFlight.set(true);
        flying.set(true);
        controller.refresh();
        nativeModeChange(GameMode.SURVIVAL);
        runRechecks();
        assertTrue(allowFlight.get());
        when(player.getWorld()).thenReturn(outside);
        controller.refresh();
        assertEquals(GameMode.SURVIVAL, mode.get());
        assertFalse(allowFlight.get());
        assertFalse(flying.get());
    }

    private void bypass(String source) {
        if (source.equals("OP")) when(player.isOp()).thenReturn(true);
        else if (source.equals("GLOBAL")) permissions.add("overworld.lobby.bypass");
        else {
            permissions.add("overworld.lobby.bypass.mode");
            permissions.add("overworld.lobby.fly");
        }
    }

    private void nativeModeChange(GameMode requested) {
        PlayerGameModeChangeEvent event = new PlayerGameModeChangeEvent(player, requested);
        controller.onGameModeChange(event);
        assertFalse(event.isCancelled());
        player.setGameMode(requested);
    }

    private PlayerRespawnEvent respawn() {
        PlayerRespawnEvent event = mock(PlayerRespawnEvent.class);
        when(event.getPlayer()).thenReturn(player);
        return event;
    }

    private void runRechecks() {
        List<Runnable> work = List.copyOf(pendingRechecks);
        pendingRechecks.clear();
        work.forEach(Runnable::run);
    }
}
