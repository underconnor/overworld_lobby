package io.github.underconnor.overworld.lobby;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class WorldSettingsCommandsTest {
    @TempDir Path directory;
    private final JavaPlugin plugin = mock(JavaPlugin.class);
    private final Server server = mock(Server.class);
    private final Player player = mock(Player.class);
    private final World lobby = mock(World.class), other = mock(World.class), outside = mock(World.class), nether = mock(World.class);
    private final AtomicReference<Settings> active = new AtomicReference<>();
    private final AtomicInteger reloads = new AtomicInteger();
    private final List<String> replies = new ArrayList<>();
    private Path file;
    private WorldSettingsCommands commands;

    @BeforeEach void setup() throws Exception {
        file = directory.resolve("config.yml");
        Files.writeString(file, "# 사용자 설정 보존\nworlds: [lobby, lobby.second, nether]\nplayers: {game-mode: SURVIVAL}\n"
            + "time: {enabled: true, ticks: 6000}\nweather: {enabled: true, kind: CLEAR}\n"
            + "messages: {prefix: '&c', cooldown-ms: 1000}\ncustom-option: '그대로 유지'\n"
            + "world-settings:\n  lobby.second:\n    time: {enabled: true, ticks: 18000}\n    weather: {enabled: true, kind: RAIN}\n", StandardCharsets.UTF_8);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getLogger()).thenReturn(mock(Logger.class));
        world(lobby, "lobby", World.Environment.NORMAL);
        world(other, "lobby.second", World.Environment.NORMAL);
        world(outside, "wild", World.Environment.NORMAL);
        world(nether, "nether", World.Environment.NETHER);
        when(nether.isFixedTime()).thenReturn(true);
        when(server.getWorlds()).thenReturn(List.of(lobby, other, outside, nether));
        when(player.getWorld()).thenReturn(lobby);
        when(player.isOp()).thenReturn(true);
        doAnswer(call -> { replies.add(call.getArgument(0)); return null; }).when(player).sendMessage(anyString());
        active.set(Settings.load(read()));
        commands = new WorldSettingsCommands(plugin, active::get, this::reload);
    }
    private void world(World world, String name, World.Environment environment) {
        when(world.getName()).thenReturn(name);
        when(world.getEnvironment()).thenReturn(environment);
        when(server.getWorld(name)).thenReturn(world);
    }
    private void reload() throws Exception { active.set(Settings.load(read())); reloads.incrementAndGet(); }
    private YamlConfiguration read() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.options().parseComments(true).pathSeparator('\u0000');
        config.loadFromString(Files.readString(file, StandardCharsets.UTF_8));
        config.options().pathSeparator('.');
        return config;
    }
    private ConfigurationSection override(String name) throws Exception {
        return (ConfigurationSection) read().getConfigurationSection("world-settings").getValues(false).get(name);
    }
    private void execute(String... args) { assertTrue(commands.handle(player, "lobby", args)); }
    private void unchanged(byte[] before, Settings old) throws Exception {
        assertArrayEquals(before, Files.readAllBytes(file)); assertSame(old, active.get()); assertEquals(0, reloads.get());
    }

    @ParameterizedTest @CsvSource({"day,1000", "night,13000", "noon,6000", "midnight,18000", "0,0", "23999,23999", "00042,42", "DAY,1000"})
    void aliasesAndNumericTimeApplyOnlyCurrentWorld(String value, long ticks) throws Exception {
        execute("time", value);
        assertTrue(override("lobby").getBoolean("time.enabled"));
        assertEquals(ticks, override("lobby").getLong("time.ticks"));
        assertEquals(18000, override("lobby.second").getLong("time.ticks"));
        assertEquals("RAIN", override("lobby.second").getString("weather.kind"));
        assertEquals(6000, read().getLong("time.ticks"));
        assertEquals("그대로 유지", read().getString("custom-option"));
        assertEquals(1, reloads.get());
        assertTrue(replies.getLast().startsWith("§a"));
    }

    @ParameterizedTest @ValueSource(strings = {"clear", "rain", "thunder", "THUNDER"})
    void weatherChangesOnlyNamedWorldAndPreservesItsTime(String value) throws Exception {
        execute("weather", value, "lobby.second");
        assertEquals(value.toUpperCase(java.util.Locale.ROOT), override("lobby.second").getString("weather.kind"));
        assertTrue(override("lobby.second").getBoolean("weather.enabled"));
        assertEquals(18000, override("lobby.second").getLong("time.ticks"));
        assertEquals("CLEAR", read().getString("weather.kind"));
        assertNull(read().getConfigurationSection("world-settings").getValues(false).get("lobby"));
        assertEquals(List.of("lobby.second"), new ArrayList<>(read().getConfigurationSection("world-settings").getKeys(false)));
    }

    @Test void offAndDefaultHaveIndependentWorldAndSettingScope() throws Exception {
        execute("time", "off", "lobby.second");
        assertFalse(override("lobby.second").getBoolean("time.enabled"));
        assertEquals("RAIN", override("lobby.second").getString("weather.kind"));
        execute("time", "default", "lobby.second");
        assertFalse(override("lobby.second").contains("time"));
        assertEquals("RAIN", override("lobby.second").getString("weather.kind"));
        execute("weather", "off", "lobby.second");
        assertFalse(override("lobby.second").getBoolean("weather.enabled"));
        execute("weather", "default", "lobby.second");
        assertNull(read().getConfigurationSection("world-settings"));
        assertEquals(6000, read().getLong("time.ticks")); assertEquals("CLEAR", read().getString("weather.kind"));
    }

    @Test void dottedLiteralWorldPersistsReloadsAndDefaultRemovalLeavesOtherWorld() throws Exception {
        execute("time", "1234", "lobby.second"); execute("time", "noon", "lobby");
        assertEquals(1234, override("lobby.second").getLong("time.ticks"));
        assertEquals(6000, override("lobby").getLong("time.ticks"));
        assertFalse(read().getConfigurationSection("world-settings").getValues(false).containsKey("lobby.second.time"));
        execute("weather", "default", "lobby.second"); execute("time", "default", "lobby.second");
        assertEquals(SetHolder.LOBBY_ONLY, read().getConfigurationSection("world-settings").getKeys(false));
        assertEquals(6000, override("lobby").getLong("time.ticks"));
    }
    private static final class SetHolder { static final java.util.Set<String> LOBBY_ONLY = java.util.Set.of("lobby"); }

    @Test void consoleNeedsExplicitWorldButAdminAndOperatorAreAccepted() throws Exception {
        CommandSender console = mock(CommandSender.class); when(console.hasPermission("overworld.lobby.admin")).thenReturn(true);
        byte[] before = Files.readAllBytes(file);
        assertTrue(commands.handle(console, "lobby", new String[]{"time", "night"}));
        assertArrayEquals(before, Files.readAllBytes(file)); assertEquals(0, reloads.get());
        assertTrue(commands.handle(console, "lobby", new String[]{"time", "night", "lobby"}));
        assertEquals(13000, override("lobby").getLong("time.ticks"));
        verify(console, never()).isPermissionSet(anyString());
        when(player.isOp()).thenReturn(false); when(player.hasPermission("overworld.lobby.admin")).thenReturn(true);
        execute("weather", "rain"); assertEquals("RAIN", override("lobby").getString("weather.kind"));
    }

    @ParameterizedTest @ValueSource(strings = {"-1", "24000", "1.5", "+1", "sunrise", "9999999999999999999999", ""})
    void invalidTimeHasNoFileOrRuntimeEffect(String value) throws Exception {
        byte[] before = Files.readAllBytes(file); Settings old = active.get();
        execute("time", value); unchanged(before, old);
    }
    @ParameterizedTest @ValueSource(strings = {"snow", "0", ""})
    void invalidWeatherHasNoFileOrRuntimeEffect(String value) throws Exception {
        byte[] before = Files.readAllBytes(file); Settings old = active.get();
        execute("weather", value); unchanged(before, old);
    }
    @Test void deniedPermissionMissingWorldUnloadedAndUnprotectedTargetsHaveNoEffect() throws Exception {
        byte[] before = Files.readAllBytes(file); Settings old = active.get();
        when(player.isOp()).thenReturn(false); execute("time", "day"); unchanged(before, old);
        when(player.isOp()).thenReturn(true);
        execute("time"); execute("weather", "rain", "missing"); execute("time", "day", "wild");
        execute("time", "day", "lobby", "extra"); unchanged(before, old);
        when(server.getWorlds()).thenReturn(List.of(other, nether)); execute("time", "day", "lobby"); unchanged(before, old);
    }

    @Test void enabledTimeAndWeatherOnClocklessDimensionsAreRejectedButOffAndDefaultWork() throws Exception {
        byte[] before = Files.readAllBytes(file); Settings old = active.get();
        execute("time", "day", "nether"); execute("weather", "rain", "nether"); unchanged(before, old);
        when(nether.getEnvironment()).thenReturn(World.Environment.THE_END);
        execute("weather", "clear", "nether"); unchanged(before, old);
        execute("time", "off", "nether"); execute("weather", "off", "nether");
        assertFalse(override("nether").getBoolean("time.enabled")); assertFalse(override("nether").getBoolean("weather.enabled"));
        execute("time", "default", "nether"); execute("weather", "default", "nether");
        assertFalse(read().getConfigurationSection("world-settings").getValues(false).containsKey("nether"));
    }

    @Test void fixedTimeCustomNormalWorldStillAllowsWeather() throws Exception {
        when(lobby.isFixedTime()).thenReturn(true);
        execute("weather", "thunder"); assertEquals("THUNDER", override("lobby").getString("weather.kind"));
    }

    @Test void invalidExistingProtectionOrMessagesAreNotSilentlyRepairedOrSaved() throws Exception {
        Settings old = active.get(); Files.writeString(file, "players: {fly-speed: 2}\n");
        byte[] invalid = Files.readAllBytes(file); execute("time", "day"); unchanged(invalid, old);
        Files.writeString(file, "messages: {cooldown-ms: -1}\n"); invalid = Files.readAllBytes(file);
        execute("weather", "rain"); unchanged(invalid, old);
        Files.writeString(file, "world-settings: [wrong]\n"); invalid = Files.readAllBytes(file);
        execute("time", "day"); unchanged(invalid, old);
    }

    @Test void protectionRemovedOnDiskIsRejectedBeforeSaveOrApply() throws Exception {
        Settings old = active.get(); Files.writeString(file, "worlds: [wild]\n"); byte[] before = Files.readAllBytes(file);
        execute("time", "day"); unchanged(before, old);
    }

    @Test void writeFailureKeepsExactBytesAndActiveSettings() throws Exception {
        commands = new WorldSettingsCommands(plugin, active::get, this::reload, (target, bytes) -> { throw new IOException("write blocked"); });
        byte[] before = Files.readAllBytes(file); Settings old = active.get();
        execute("time", "day"); unchanged(before, old); assertTrue(replies.getLast().contains("기존 설정"));
    }

    @Test void failedApplyRestoresExactOriginalBytesAndReloadsOldSettings() throws Exception {
        byte[] before = Files.readAllBytes(file); Settings original = active.get(); AtomicInteger callbacks = new AtomicInteger();
        commands = new WorldSettingsCommands(plugin, active::get, () -> {
            active.set(Settings.load(read()));
            if (callbacks.incrementAndGet() == 1) throw new IOException("apply blocked after assignment");
        });
        execute("weather", "thunder");
        assertArrayEquals(before, Files.readAllBytes(file)); assertEquals(original, active.get()); assertEquals(2, callbacks.get());
        assertTrue(replies.getLast().contains("복원했습니다"));
    }

    @Test void commentsUnicodePermissionsAndNoTemporaryFilesSurviveSuccessfulSave() throws Exception {
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-r-----"));
        execute("time", "night");
        assertEquals(PosixFilePermissions.fromString("rw-r-----"), Files.getPosixFilePermissions(file));
        String content = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(Files.readAllBytes(file))).toString();
        assertTrue(content.contains("# 사용자 설정 보존")); assertTrue(content.contains("그대로 유지"));
        try (var files = Files.list(directory)) { assertEquals(List.of(file), files.toList()); }
    }

    @Test void completionsUseValuesAndProtectedLoadedLiteralWorldNamesOnly() {
        assertEquals(List.of("night", "noon"), commands.complete(player, new String[]{"time", "n"}));
        assertEquals(List.of("thunder"), commands.complete(player, new String[]{"weather", "TH"}));
        assertEquals(List.of("lobby", "lobby.second"), commands.complete(player, new String[]{"time", "day", "lob"}));
        when(player.isOp()).thenReturn(false);
        assertEquals(List.of(), commands.complete(player, new String[]{"time", "d"}));
        assertNull(commands.complete(player, new String[]{"fly", "on"}));
        assertFalse(commands.handle(player, "lobby", new String[]{"fly", "on"}));
        assertFalse(commands.handle(player, "lobby", new String[0]));
    }
}
