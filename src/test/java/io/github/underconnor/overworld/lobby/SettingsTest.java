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
}
