package io.github.underconnor.overworld.lobby;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SettingsTest {
    @Test void defaultsMatchBundledConfig() throws Exception {
        Settings empty = Settings.load(new YamlConfiguration());
        try (var source = new InputStreamReader(getClass().getResourceAsStream("/config.yml"), StandardCharsets.UTF_8)) {
            assertEquals(empty, Settings.load(YamlConfiguration.loadConfiguration(source)));
        }
        assertEquals(GameMode.SURVIVAL, empty.gameMode());
        assertTrue(empty.allowFlight());
        assertTrue(empty.keepFoodFull());
        assertEquals(6000, empty.time().ticks());
        assertEquals(Settings.WeatherKind.CLEAR, empty.weather().kind());
        assertEquals(-4.5, empty.spawn().x());
        assertEquals(63.5, empty.spawn().y());
        assertEquals(-1.5, empty.spawn().z());
        assertEquals(180f, empty.spawn().yaw());
        assertEquals(0f, empty.spawn().pitch());
        assertTrue(empty.preventNaturalSpawns());
        assertFalse(empty.protections().contains(Action.CONTAINERS));
        assertTrue(empty.allowedContainers().contains(Material.CHEST));
        assertTrue(empty.allowedContainers().contains(Material.BLACK_SHULKER_BOX));
    }

    @Test void worldSelectionAndDisabledPlugin() throws Exception {
        var config = new YamlConfiguration();
        config.loadFromString("worlds: [lobby]\n");
        World lobby = mock(World.class), survival = mock(World.class);
        when(lobby.getName()).thenReturn("lobby");
        when(survival.getName()).thenReturn("survival");
        Settings settings = Settings.load(config);
        assertTrue(settings.protects(lobby));
        assertFalse(settings.protects(survival));
        assertFalse(settings.protects(null));
        config.set("enabled", false);
        assertFalse(Settings.load(config).protects(lobby));
    }

    @Test void configurableBehaviorAndImmutableCollections() throws Exception {
        var config = new YamlConfiguration();
        config.loadFromString("players: {game-mode: adventure, allow-flight: false, fly-speed: 0.3}\ntime: {enabled: false, ticks: 18000}\nweather: {enabled: true, kind: thunder}\nprotection: {block-break: false, containers: true}\nenvironment: {redstone: false}\nallowed-containers: [CHEST]\n");
        Settings settings = Settings.load(config);
        assertEquals(GameMode.ADVENTURE, settings.gameMode());
        assertFalse(settings.allowFlight());
        assertEquals(0.3f, settings.flySpeed());
        assertFalse(settings.time().enabled());
        assertEquals(Settings.WeatherKind.THUNDER, settings.weather().kind());
        assertFalse(settings.protections().contains(Action.BLOCK_BREAK));
        assertTrue(settings.protections().contains(Action.CONTAINERS));
        assertFalse(settings.environmentRules().contains(EnvironmentRule.REDSTONE));
        assertEquals(java.util.Set.of(Material.CHEST), settings.allowedContainers());
        assertThrows(UnsupportedOperationException.class, () -> settings.protections().clear());
        assertThrows(UnsupportedOperationException.class, () -> settings.worlds().add("extra"));
    }

    @ParameterizedTest @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
        "players.game-mode | INVALID", "players.fly-speed | -0.1", "players.fly-speed | 1.01",
        "time.ticks | -1", "time.ticks | 24000", "time.ticks | 1.5", "weather.kind | snow",
        "players.allow-flight | 'true'", "protection.block-break | 'yes'", "enabled | 1",
        "worlds | lobby", "worlds | ['']", "worlds | [42]",
        "allowed-containers | [STONE]", "allowed-containers | [NO_SUCH_BLOCK]", "allowed-containers | [42]",
        "players | false", "protection | false", "time | 6000", "weather | CLEAR", "environment | false",
        "spawn | false", "spawn.world | 42", "spawn.x | .inf", "spawn.y | .nan", "spawn.yaw | 361", "spawn.pitch | 91"
    }) void rejectsInvalidValues(String key, String yamlValue) throws Exception {
        var value = new YamlConfiguration();
        value.loadFromString("value: " + yamlValue);
        var config = new YamlConfiguration();
        config.set(key, value.get("value"));
        assertThrows(IllegalArgumentException.class, () -> Settings.load(config));
    }

    @Test void emptyContainerListAllowsNoStorageException() {
        var config = new YamlConfiguration();
        config.set("allowed-containers", java.util.List.of());
        assertTrue(Settings.load(config).allowedContainers().isEmpty());
    }

    @Test void legacyConfigurationsUseGlobalTimeWeatherForEveryWorld() {
        Settings settings = Settings.load(new YamlConfiguration());
        World world = world("lobby");
        assertTrue(settings.worldSettings().isEmpty());
        assertSame(settings.time(), settings.timeFor(world));
        assertSame(settings.weather(), settings.weatherFor(world));
        assertSame(settings.time(), settings.timeFor(null));
        assertSame(settings.weather(), settings.weatherFor(null));
    }

    @Test void worldOverridesInheritMissingGlobalFieldsAndNeverExpandProtectionScope() throws Exception {
        var config = new YamlConfiguration();
        config.loadFromString("""
            worlds: [lobby]
            time: {enabled: false, ticks: 2000}
            weather: {enabled: true, kind: CLEAR}
            world-settings:
              lobby:
                time: {ticks: 18000}
                weather: {kind: THUNDER}
              other:
                time: {enabled: true}
                weather: {enabled: false}
            """);
        Settings settings = Settings.load(config);
        assertEquals(new Settings.TimeSettings(false, 18000), settings.timeFor(world("lobby")));
        assertEquals(new Settings.WeatherSettings(true, Settings.WeatherKind.THUNDER), settings.weatherFor(world("lobby")));
        assertEquals(new Settings.TimeSettings(true, 2000), settings.timeFor(world("other")));
        assertEquals(new Settings.WeatherSettings(false, Settings.WeatherKind.CLEAR), settings.weatherFor(world("other")));
        assertFalse(settings.protects(world("other")));
        assertEquals(settings.time(), settings.timeFor(world("unconfigured")));
        assertThrows(UnsupportedOperationException.class, () -> settings.worldSettings().clear());
    }

    @Test void eachFeatureCanResetToGlobalDefaultsIndependently() {
        var config = new YamlConfiguration();
        config.set("world-settings.lobby.time.ticks", 18000);
        config.set("world-settings.lobby.weather.kind", "RAIN");
        Settings before = Settings.load(config);
        assertEquals(18000, before.timeFor(world("lobby")).ticks());
        config.set("world-settings.lobby.time", null);
        Settings reset = Settings.load(config);
        assertSame(reset.time(), reset.timeFor(world("lobby")));
        assertEquals(Settings.WeatherKind.RAIN, reset.weatherFor(world("lobby")).kind());
        config.set("world-settings.lobby.weather", null);
        Settings empty = Settings.load(config);
        assertSame(empty.time(), empty.timeFor(world("lobby")));
        assertSame(empty.weather(), empty.weatherFor(world("lobby")));
    }

    @Test void partialOverridesFollowNewGlobalValuesAfterReload() {
        var config = new YamlConfiguration();
        config.set("world-settings.lobby.time.enabled", false);
        config.set("world-settings.lobby.weather.enabled", false);
        config.set("time.ticks", 9000);
        config.set("weather.kind", "RAIN");
        Settings settings = Settings.load(config);
        assertEquals(new Settings.TimeSettings(false, 9000), settings.timeFor(world("lobby")));
        assertEquals(new Settings.WeatherSettings(false, Settings.WeatherKind.RAIN), settings.weatherFor(world("lobby")));
    }

    @Test void literalDottedWorldNamesSurviveReadingAndSaveReload() throws Exception {
        YamlConfiguration config = literalYaml("""
            world-settings:
              lobby.map:
                time: {enabled: true, ticks: 7000}
                weather: {enabled: true, kind: RAIN}
            """);
        Settings settings = Settings.load(config);
        assertEquals(java.util.Set.of("lobby.map"), settings.worldSettings().keySet());
        assertEquals(7000, settings.timeFor(world("lobby.map")).ticks());
        assertEquals(Settings.WeatherKind.RAIN, settings.weatherFor(world("lobby.map")).kind());
        assertEquals(settings.time(), settings.timeFor(world("lobby")));
        assertEquals(settings, Settings.load(literalYaml(config.saveToString())));
    }

    @ParameterizedTest @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
        "world-settings | false", "world-settings | [lobby]", "world-settings.lobby | false",
        "world-settings.lobby.time | false", "world-settings.lobby.weather | CLEAR",
        "world-settings.lobby.time.enabled | 'true'", "world-settings.lobby.time.ticks | 24000",
        "world-settings.lobby.time.ticks | -1", "world-settings.lobby.time.ticks | 1.5",
        "world-settings.lobby.time.ticks | .nan", "world-settings.lobby.weather.kind | snow",
        "world-settings.lobby.weather.enabled | 'false'"
    }) void rejectsInvalidPerWorldOverrides(String key, String yamlValue) throws Exception {
        var value = new YamlConfiguration();
        value.loadFromString("value: " + yamlValue);
        var config = new YamlConfiguration();
        config.set(key, value.get("value"));
        assertThrows(IllegalArgumentException.class, () -> Settings.load(config));
    }

    private World world(String name) {
        World world = mock(World.class);
        when(world.getName()).thenReturn(name);
        return world;
    }

    private YamlConfiguration literalYaml(String source) throws Exception {
        var config = new YamlConfiguration();
        config.options().pathSeparator('\0');
        config.loadFromString(source);
        config.options().pathSeparator('.');
        return config;
    }
}
