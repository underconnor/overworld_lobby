package io.github.underconnor.overworld.lobby;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SpawnControllerTest {
    private final YamlConfiguration config = new YamlConfiguration();
    private final JavaPlugin plugin = mock(JavaPlugin.class);
    private final Server server = mock(Server.class);
    private final PluginManager pluginManager = mock(PluginManager.class);
    private final BukkitScheduler scheduler = mock(BukkitScheduler.class);
    private final Logger logger = mock(Logger.class);
    private final World lobby = mock(World.class);
    private final World anotherLobby = mock(World.class);
    private final World outside = mock(World.class);
    private final Player player = mock(Player.class);
    private final List<Runnable> pending = new ArrayList<>();
    private final Set<String> permissions = new HashSet<>();
    private SpawnController controller;

    @BeforeEach void setup() {
        config.set("worlds", List.of("lobby", "lobby2"));
        when(lobby.getName()).thenReturn("lobby");
        when(anotherLobby.getName()).thenReturn("lobby2");
        when(outside.getName()).thenReturn("wild");
        when(lobby.getMinHeight()).thenReturn(-64);
        when(player.getWorld()).thenReturn(lobby);
        when(player.isOnline()).thenReturn(true);
        when(player.hasPermission(anyString())).thenAnswer(call -> permissions.contains(call.getArgument(0)));
        when(player.teleport(any(Location.class))).thenReturn(true);
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getLogger()).thenReturn(logger);
        when(server.getPluginManager()).thenReturn(pluginManager);
        when(server.getScheduler()).thenReturn(scheduler);
        when(server.getWorlds()).thenReturn(List.of(lobby, anotherLobby, outside));
        when(server.getWorld("lobby")).thenReturn(lobby);
        when(server.getWorld("lobby2")).thenReturn(anotherLobby);
        when(server.getWorld("wild")).thenReturn(outside);
        doReturn(List.of()).when(server).getOnlinePlayers();
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(call -> {
            pending.add(call.getArgument(1));
            return mock(BukkitTask.class);
        });
        controller = new SpawnController(plugin, new ProtectionPolicy(() -> Settings.load(config)));
    }

    @Test void manualSpawnPreservesExactHalfBlockCoordinatesAndOrientation() {
        assertTrue(controller.teleport(player));
        ArgumentCaptor<Location> target = ArgumentCaptor.forClass(Location.class);
        verify(player).teleport(target.capture());
        assertExact(target.getValue(), lobby);
        verify(player).setFallDistance(0);
        verify(lobby, never()).setSpawnLocation(any(Location.class));
    }

    @Test void explicitCommandStillWorksForBypassedPlayersAndOtherSourceWorlds() {
        permissions.add("overworld.lobby.bypass");
        permissions.add("overworld.lobby.bypass.spawn");
        when(player.getWorld()).thenReturn(outside);
        assertTrue(controller.teleport(player));
        verify(player).teleport(any(Location.class));
    }

    @Test void respawnUsesExactLocationAndHonorsAutomaticBypass() {
        PlayerRespawnEvent event = respawn();
        controller.onRespawn(event);
        ArgumentCaptor<Location> target = ArgumentCaptor.forClass(Location.class);
        verify(event).setRespawnLocation(target.capture());
        assertExact(target.getValue(), lobby);
        permissions.add("overworld.lobby.bypass.spawn");
        PlayerRespawnEvent bypassed = respawn();
        controller.onRespawn(bypassed);
        verify(bypassed, never()).setRespawnLocation(any());
    }

    @Test void joinsTeleportNextTickAndUseUpdatedConfiguration() {
        controller.start();
        controller.onJoin(join());
        assertEquals(1, pending.size());
        verify(player, never()).teleport(any(Location.class));
        config.set("spawn.world", "lobby2");
        config.set("spawn.x", 9.5);
        config.set("spawn.yaw", 45);
        pending.getFirst().run();
        ArgumentCaptor<Location> target = ArgumentCaptor.forClass(Location.class);
        verify(player).teleport(target.capture());
        assertSame(anotherLobby, target.getValue().getWorld());
        assertEquals(9.5, target.getValue().getX());
        assertEquals(45, target.getValue().getYaw());
    }

    @Test void operatorActivityBypassStillUsesAutomaticJoinAndRespawnSpawn() {
        permissions.add("overworld.lobby.bypass");
        controller.start();
        controller.onJoin(join());
        assertEquals(1, pending.size());
        pending.getFirst().run();
        ArgumentCaptor<Location> target = ArgumentCaptor.forClass(Location.class);
        verify(player).teleport(target.capture());
        assertExact(target.getValue(), lobby);
        PlayerRespawnEvent event = respawn();
        controller.onRespawn(event);
        verify(event).setRespawnLocation(any(Location.class));
        permissions.add("overworld.lobby.bypass.spawn");
        PlayerRespawnEvent bypassed = respawn();
        controller.onRespawn(bypassed);
        verify(bypassed, never()).setRespawnLocation(any());
    }

    @Test void pendingJoinIsSkippedWhenSourceLeavesProtectionOrControllerCloses() {
        controller.start();
        controller.onJoin(join());
        when(player.getWorld()).thenReturn(outside);
        pending.getFirst().run();
        verify(player, never()).teleport(any(Location.class));
        when(player.getWorld()).thenReturn(lobby);
        controller.onJoin(join());
        controller.close();
        pending.getLast().run();
        verify(player, never()).teleport(any(Location.class));
    }

    @Test void voidRescueRedirectsMovementToExactSpawnBelowMinimumHeightMargin() {
        PlayerMoveEvent falling = movement(-73);
        controller.onVoidMove(falling);
        assertExact(falling.getTo(), lobby);
        verify(player).setFallDistance(0);
        PlayerMoveEvent threshold = movement(-72);
        controller.onVoidMove(threshold);
        assertEquals(-72, threshold.getTo().getY());
    }

    @Test void voidAndRespawnIgnoreUnprotectedWorldsBypassesAndDisabledOptions() {
        permissions.add("overworld.lobby.bypass.spawn");
        PlayerMoveEvent bypassed = movement(-73);
        controller.onVoidMove(bypassed);
        assertEquals(-73, bypassed.getTo().getY());
        permissions.clear();
        config.set("spawn.void-rescue", false);
        PlayerMoveEvent disabled = movement(-73);
        controller.onVoidMove(disabled);
        assertEquals(-73, disabled.getTo().getY());
        config.set("spawn.on-respawn", false);
        PlayerRespawnEvent disabledRespawn = respawn();
        controller.onRespawn(disabledRespawn);
        verify(disabledRespawn, never()).setRespawnLocation(any());
        config.set("spawn.on-respawn", true);
        when(player.getWorld()).thenReturn(outside);
        PlayerRespawnEvent unprotected = respawn();
        controller.onRespawn(unprotected);
        verify(unprotected, never()).setRespawnLocation(any());
    }

    @Test void emptyWorldUsesFirstProtectedLoadedWorldInsteadOfUnprotectedPrimary() {
        when(server.getWorlds()).thenReturn(List.of(outside, anotherLobby, lobby));
        assertTrue(controller.teleport(player));
        ArgumentCaptor<Location> target = ArgumentCaptor.forClass(Location.class);
        verify(player).teleport(target.capture());
        assertExact(target.getValue(), anotherLobby);
    }

    @Test void unloadedOrUnprotectedTargetsDoNotTeleportAndWarnOncePerProblem() {
        config.set("spawn.world", "missing");
        assertFalse(controller.teleport(player));
        controller.refresh();
        verify(logger, times(1)).warning(contains("missing"));
        config.set("spawn.world", "wild");
        assertFalse(controller.teleport(player));
        verify(logger, times(1)).warning(contains("wild"));
        verify(player, never()).teleport(any(Location.class));
        config.set("spawn.world", "lobby");
        assertTrue(controller.teleport(player));
    }

    @Test void startupAppliesJoinBehaviorOnlyToEligibleOnlinePlayers() {
        doReturn(List.of(player)).when(server).getOnlinePlayers();
        controller.start();
        assertEquals(1, pending.size());
        pending.getFirst().run();
        verify(player).teleport(any(Location.class));
        controller.close();
    }

    @Test void disabledSpawnAndUnsuccessfulTeleportDoNotResetPlayerFallDistance() {
        config.set("spawn.enabled", false);
        assertFalse(controller.teleport(player));
        verify(player, never()).teleport(any(Location.class));
        config.set("spawn.enabled", true);
        when(player.teleport(any(Location.class))).thenReturn(false);
        assertFalse(controller.teleport(player));
        verify(player, never()).setFallDistance(anyFloat());
    }

    private PlayerJoinEvent join() {
        PlayerJoinEvent event = mock(PlayerJoinEvent.class);
        when(event.getPlayer()).thenReturn(player);
        return event;
    }

    private PlayerRespawnEvent respawn() {
        PlayerRespawnEvent event = mock(PlayerRespawnEvent.class);
        when(event.getPlayer()).thenReturn(player);
        return event;
    }

    private PlayerMoveEvent movement(double y) {
        return new PlayerMoveEvent(player, new Location(lobby, 0, 0, 0), new Location(lobby, 0, y, 0));
    }

    private void assertExact(Location target, World expectedWorld) {
        assertNotNull(target);
        assertSame(expectedWorld, target.getWorld());
        assertEquals(-4.5, target.getX());
        assertEquals(63.5, target.getY());
        assertEquals(-1.5, target.getZ());
        assertEquals(180f, target.getYaw());
        assertEquals(0f, target.getPitch());
    }
}
