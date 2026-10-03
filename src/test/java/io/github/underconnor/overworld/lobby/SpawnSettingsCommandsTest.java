package io.github.underconnor.overworld.lobby;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class SpawnSettingsCommandsTest {
    @TempDir Path directory;
    private final JavaPlugin plugin = mock(JavaPlugin.class);
    private final Server server = mock(Server.class);
    private final Player player = mock(Player.class);
    private final World lobby = mock(World.class), second = mock(World.class), outside = mock(World.class);
    private final AtomicReference<Settings> active = new AtomicReference<>();
    private final AtomicInteger reloads = new AtomicInteger();
    private final List<String> replies = new ArrayList<>();
    private Path file;
    private SpawnSettingsCommands commands;

    @BeforeEach void setup() throws Exception {
        file = directory.resolve("config.yml");
        Files.writeString(file, "# 기존 스폰 옵션 유지\nworlds: [lobby, lobby.second]\nplayers: {game-mode: SURVIVAL, keep-food-full: true}\n"
            + "spawn: {enabled: false, world: lobby, x: -4.5, y: 63.5, z: -1.5, yaw: 180, pitch: 0, on-join: false, on-respawn: false, void-rescue: false}\n"
            + "messages: {prefix: '&c', cooldown-ms: 1000, denied: {block-break: '허용된 안내'}}\n"
            + "world-settings:\n  lobby.second:\n    time: {enabled: true, ticks: 18000}\n    weather: {enabled: true, kind: RAIN}\n"
            + "custom.setting: '사용자 값'\n", StandardCharsets.UTF_8);
        when(plugin.getDataFolder()).thenReturn(directory.toFile()); when(plugin.getServer()).thenReturn(server);
        when(plugin.getLogger()).thenReturn(mock(Logger.class));
        world(lobby, "lobby", -64, 320); world(second, "lobby.second", 0, 256); world(outside, "wild", -64, 320);
        when(server.getWorlds()).thenReturn(List.of(lobby, second, outside));
        when(player.isOp()).thenReturn(true); when(player.getWorld()).thenReturn(lobby);
        when(player.getLocation()).thenAnswer(call -> new Location(lobby, -12.75, 63.5, 9.25, 135.25f, -22.5f));
        doAnswer(call -> { replies.add(call.getArgument(0)); return null; }).when(player).sendMessage(anyString());
        active.set(Settings.load(read())); commands = new SpawnSettingsCommands(plugin, active::get, this::reload);
    }
    private void world(World world, String name, int minimum, int maximum) {
        when(world.getName()).thenReturn(name); when(world.getMinHeight()).thenReturn(minimum); when(world.getMaxHeight()).thenReturn(maximum);
        when(server.getWorld(name)).thenReturn(world);
    }
    private YamlConfiguration read() throws Exception { return WorldSettingsCommands.read(Files.readAllBytes(file)); }
    private void reload() throws Exception { active.set(Settings.load(read())); reloads.incrementAndGet(); }
    private void execute(String... args) { assertTrue(commands.handle(player, "lobby", args)); }
    private void unchanged(byte[] original, Settings old) throws Exception {
        assertArrayEquals(original, Files.readAllBytes(file)); assertSame(old, active.get()); assertEquals(0, reloads.get());
    }
    private void assertSpawn(String world, double x, double y, double z, float yaw, float pitch) {
        Settings.SpawnSettings spawn = active.get().spawn(); assertTrue(spawn.enabled()); assertEquals(world, spawn.world());
        assertEquals(x, spawn.x()); assertEquals(y, spawn.y()); assertEquals(z, spawn.z()); assertEquals(yaw, spawn.yaw()); assertEquals(pitch, spawn.pitch());
        assertFalse(spawn.onJoin()); assertFalse(spawn.onRespawn()); assertFalse(spawn.voidRescue());
    }

    @Test void currentPlayerLocationPreservesExactCoordinatesLookAndAllOtherSettings() throws Exception {
        Settings old = active.get(); execute("setspawn");
        assertSpawn("lobby", -12.75, 63.5, 9.25, 135.25f, -22.5f); assertEquals(1, reloads.get());
        assertEquals(old.worldSettings(), active.get().worldSettings()); assertEquals(old.protections(), active.get().protections());
        assertEquals(old.time(), active.get().time()); assertEquals(old.weather(), active.get().weather());
        assertEquals("허용된 안내", read().getString("messages.denied.block-break"));
        assertEquals("사용자 값", read().getValues(false).get("custom.setting"));
        assertTrue(replies.getLast().startsWith("§a"));
        verify(lobby, never()).setSpawnLocation(any(Location.class)); verify(second, never()).setSpawnLocation(any(Location.class));
        verify(server, never()).broadcastMessage(anyString()); verify(player, never()).teleport(any(Location.class));
    }

    @Test void explicitPlayerCoordinatesKeepCurrentLookWhenOmitted() throws Exception {
        execute("setspawn", "lobby.second", "4.125", "70.75", "-2.5");
        assertSpawn("lobby.second", 4.125, 70.75, -2.5, 135.25f, -22.5f);
        assertTrue(read().getConfigurationSection("world-settings").getValues(false).containsKey("lobby.second"));
    }
    @Test void explicitPlayerYawOnlyPreservesCurrentPitch() {
        execute("setspawn", "lobby", "1", "64", "2", "-90.5"); assertSpawn("lobby", 1, 64, 2, -90.5f, -22.5f);
    }
    @Test void consoleExplicitCoordinatesDefaultToZeroLookAndMayOverrideBoth() {
        CommandSender console = mock(CommandSender.class); when(console.hasPermission("overworld.lobby.admin")).thenReturn(true);
        assertTrue(commands.handle(console, "lobby", new String[]{"setspawn", "lobby", "1.5", "64.5", "-2.5"}));
        assertSpawn("lobby", 1.5, 64.5, -2.5, 0, 0);
        assertTrue(commands.handle(console, "setspawn", new String[]{"setspawn", "lobby.second", "1.25", "10.5", "2.75", "180", "45.5"}));
        assertSpawn("lobby.second", 1.25, 10.5, 2.75, 180, 45.5f);
    }
    @Test void consoleWithoutCoordinatesDoesNotMutate() throws Exception {
        CommandSender console = mock(CommandSender.class); when(console.isOp()).thenReturn(true);
        byte[] before = Files.readAllBytes(file); Settings old = active.get();
        assertTrue(commands.handle(console, "setspawn", new String[]{"setspawn"})); unchanged(before, old);
        verify(console).sendMessage(contains("콘솔"));
    }

    @ParameterizedTest @CsvSource({"540,180", "-540,-180", "720,0", "360,360", "-360,-360", "270.5,270.5"})
    void implicitPlayerYawNormalizesOnlyOutOfRangeEquivalentDirection(float provided, float expected) {
        when(player.getLocation()).thenReturn(new Location(lobby, 1, 64, 2, provided, 10)); execute("setspawn");
        assertSpawn("lobby", 1, 64, 2, expected, 10);
    }
    @ParameterizedTest @CsvSource({"-64", "319.999"})
    void inclusiveMinimumAndExclusiveMaximumWorldHeightAllowsValidEdges(String y) {
        execute("setspawn", "lobby", "1", y, "2", "-360", "-90");
        assertSpawn("lobby", 1, Double.parseDouble(y), 2, -360, -90);
    }
    @ParameterizedTest @ValueSource(strings = {"-64.01", "320", "400"})
    void invalidWorldHeightDoesNotWriteOrApply(String y) throws Exception {
        byte[] before = Files.readAllBytes(file); Settings old = active.get();
        execute("setspawn", "lobby", "1", y, "2"); unchanged(before, old);
    }
    @Test void targetWorldHeightBoundsApplyIndependentlyOfPlayersCurrentWorld() throws Exception {
        byte[] before = Files.readAllBytes(file); Settings old = active.get();
        execute("setspawn", "lobby.second", "1", "-1", "2"); unchanged(before, old);
        execute("setspawn", "lobby.second", "1", "0", "2"); assertSpawn("lobby.second", 1, 0, 2, 135.25f, -22.5f);
    }

    @ParameterizedTest @CsvSource({"2,NaN", "2,Infinity", "2,-Infinity", "2,abc", "3,NaN", "4,Infinity", "5,361", "5,-361", "5,NaN", "6,91", "6,-91", "6,Infinity"})
    void invalidCoordinatesAndExplicitLookRejectBeforePersistence(int index, String value) throws Exception {
        byte[] before = Files.readAllBytes(file); Settings old = active.get();
        String[] args = {"setspawn", "lobby", "1", "64", "2", "180", "0"}; args[index] = value;
        execute(args); unchanged(before, old);
    }
    @Test void invalidCurrentPlayerLocationIsRejected() throws Exception {
        byte[] before = Files.readAllBytes(file); Settings old = active.get();
        when(player.getLocation()).thenReturn(new Location(lobby, 1, 64, 2, Float.NaN, 0)); execute("setspawn"); unchanged(before, old);
        when(player.getLocation()).thenReturn(new Location(lobby, Double.POSITIVE_INFINITY, 64, 2)); execute("setspawn"); unchanged(before, old);
    }

    @Test void operatorFallbackAdminPermissionAndGlobalOnlyDenialFollowAuthorization() throws Exception {
        when(player.hasPermission("overworld.lobby.admin")).thenReturn(false); execute("setspawn"); assertEquals(1, reloads.get());
        when(player.isOp()).thenReturn(false); when(player.hasPermission("overworld.lobby.admin")).thenReturn(true);
        execute("setspawn", "lobby", "1", "64", "2"); assertEquals(2, reloads.get());
        byte[] before = Files.readAllBytes(file); Settings old = active.get();
        when(player.hasPermission("overworld.lobby.admin")).thenReturn(false); when(player.hasPermission("overworld.lobby.bypass")).thenReturn(true);
        execute("setspawn"); assertArrayEquals(before, Files.readAllBytes(file)); assertSame(old, active.get()); assertEquals(2, reloads.get());
    }
    @Test void missingUnprotectedAndUnloadedWorldsAndMalformedArgumentLengthsHaveNoEffect() throws Exception {
        byte[] before = Files.readAllBytes(file); Settings old = active.get();
        execute("setspawn", "missing", "1", "64", "2"); execute("setspawn", "wild", "1", "64", "2");
        execute("setspawn", "lobby"); execute("setspawn", "lobby", "1", "64");
        execute("setspawn", "lobby", "1", "64", "2", "0", "0", "extra");
        when(server.getWorlds()).thenReturn(List.of(second, outside)); execute("setspawn"); unchanged(before, old);
    }
    @Test void targetProtectionRemovedOnDiskIsRejectedBeforeSave() throws Exception {
        Settings old = active.get(); Files.writeString(file, "worlds: [wild]\n"); byte[] before = Files.readAllBytes(file);
        execute("setspawn"); unchanged(before, old);
    }
    @Test void invalidExistingSettingsOrMessagesArePreservedWithoutPartialRepair() throws Exception {
        Settings old = active.get(); Files.writeString(file, "messages: {cooldown-ms: -1}\n"); byte[] before = Files.readAllBytes(file);
        execute("setspawn"); unchanged(before, old);
        Files.writeString(file, "world-settings: [invalid]\n"); before = Files.readAllBytes(file);
        execute("setspawn"); unchanged(before, old);
    }

    @Test void failedWriteKeepsExactOriginalBytesAndActiveSpawn() throws Exception {
        commands = new SpawnSettingsCommands(plugin, active::get, this::reload, (target, bytes) -> { throw new IOException("blocked"); });
        byte[] before = Files.readAllBytes(file); Settings old = active.get(); execute("setspawn"); unchanged(before, old);
        assertTrue(replies.getLast().contains("기존 스폰"));
    }
    @Test void failedApplyRestoresExactOriginalBytesAndReloadsOldSpawn() throws Exception {
        byte[] before = Files.readAllBytes(file); Settings old = active.get(); AtomicInteger callbacks = new AtomicInteger();
        commands = new SpawnSettingsCommands(plugin, active::get, () -> {
            active.set(Settings.load(read())); if (callbacks.incrementAndGet() == 1) throw new IOException("apply failed");
        });
        execute("setspawn"); assertArrayEquals(before, Files.readAllBytes(file)); assertEquals(old, active.get()); assertEquals(2, callbacks.get());
        assertTrue(replies.getLast().contains("복원했습니다"));
    }
    @Test void commentsUtf8FilePermissionsAndTemporaryCleanupArePreserved() throws Exception {
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-r-----")); execute("setspawn");
        assertEquals(PosixFilePermissions.fromString("rw-r-----"), Files.getPosixFilePermissions(file));
        assertTrue(Files.readString(file, StandardCharsets.UTF_8).contains("# 기존 스폰 옵션 유지"));
        try (var paths = Files.list(directory)) { assertEquals(List.of(file), paths.toList()); }
    }
    @Test void completionsOfferProtectedWorldsAndPlayersExactPositionOnlyToAuthorizedSenders() {
        assertEquals(List.of("lobby", "lobby.second"), commands.complete(player, new String[]{"setspawn", "lob"}));
        assertEquals(List.of("-12.75"), commands.complete(player, new String[]{"setspawn", "lobby", ""}));
        assertEquals(List.of("63.5"), commands.complete(player, new String[]{"setspawn", "lobby", "1", ""}));
        assertEquals(List.of("9.25"), commands.complete(player, new String[]{"setspawn", "lobby", "1", "64", ""}));
        assertEquals(List.of("135.25"), commands.complete(player, new String[]{"setspawn", "lobby", "1", "64", "2", ""}));
        assertEquals(List.of("-22.5"), commands.complete(player, new String[]{"setspawn", "lobby", "1", "64", "2", "0", ""}));
        when(player.isOp()).thenReturn(false); assertEquals(List.of(), commands.complete(player, new String[]{"setspawn", ""}));
        assertNull(commands.complete(player, new String[]{"time", "day"})); assertFalse(commands.handle(player, "lobby", new String[]{"spawn"}));
    }
}
