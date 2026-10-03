package io.github.underconnor.overworld.lobby;

import java.util.EnumMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.weather.ThunderChangeEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.event.world.ClockTimeSkipEvent;
import org.bukkit.event.world.TimeSkipEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WorldControllerTest {
    private final YamlConfiguration config = new YamlConfiguration();
    private final World world = mock(World.class);
    private final JavaPlugin plugin = mock(JavaPlugin.class);
    private final Server server = mock(Server.class);
    private final AtomicLong time = new AtomicLong(61_000);
    private final AtomicBoolean storm = new AtomicBoolean(true);
    private final AtomicBoolean thunder = new AtomicBoolean(true);
    private final AtomicInteger rainDuration = new AtomicInteger(123);
    private final AtomicInteger thunderDuration = new AtomicInteger(456);
    private final AtomicInteger clearDuration = new AtomicInteger(789);
    private final EnumMap<WorldController.ManagedRule, Boolean> rules = new EnumMap<>(WorldController.ManagedRule.class);
    private WorldController controller;

    @BeforeEach void setup() {
        config.set("worlds", List.of("lobby"));
        when(world.getUID()).thenReturn(UUID.randomUUID());
        when(world.getName()).thenReturn("lobby");
        when(plugin.getServer()).thenReturn(server);
        when(server.getWorlds()).thenReturn(List.of(world));
        when(world.getTime()).thenAnswer(call -> Math.floorMod(time.get(), 24_000L));
        when(world.getFullTime()).thenAnswer(call -> time.get());
        doAnswer(call -> { time.set(time.get() - Math.floorMod(time.get(), 24_000) + call.<Long>getArgument(0)); return null; }).when(world).setTime(anyLong());
        doAnswer(call -> { time.set(call.getArgument(0)); return null; }).when(world).setFullTime(anyLong());
        when(world.hasStorm()).thenAnswer(call -> storm.get());
        when(world.isThundering()).thenAnswer(call -> thunder.get());
        doAnswer(call -> { storm.set(call.getArgument(0)); return null; }).when(world).setStorm(anyBoolean());
        doAnswer(call -> { thunder.set(call.getArgument(0)); return null; }).when(world).setThundering(anyBoolean());
        when(world.getWeatherDuration()).thenAnswer(call -> rainDuration.get());
        when(world.getThunderDuration()).thenAnswer(call -> thunderDuration.get());
        when(world.getClearWeatherDuration()).thenAnswer(call -> clearDuration.get());
        doAnswer(call -> { rainDuration.set(call.getArgument(0)); return null; }).when(world).setWeatherDuration(anyInt());
        doAnswer(call -> { thunderDuration.set(call.getArgument(0)); return null; }).when(world).setThunderDuration(anyInt());
        doAnswer(call -> { clearDuration.set(call.getArgument(0)); return null; }).when(world).setClearWeatherDuration(anyInt());
        rules.put(WorldController.ManagedRule.TIME, true);
        rules.put(WorldController.ManagedRule.WEATHER, false);
        rules.put(WorldController.ManagedRule.SPAWNS, true);
        controller = new WorldController(plugin, () -> Settings.load(config), new WorldController.WorldRules() {
            @Override public Boolean read(World current, WorldController.ManagedRule rule) { return rules.get(rule); }
            @Override public void write(World current, WorldController.ManagedRule rule, boolean value) { rules.put(rule, value); }
        });
    }

    @Test void locksDefaultTimeWeatherAndSpawningThenRestoresExactPriorValues() {
        controller.refresh();
        assertEquals(6000, world.getTime());
        assertFalse(storm.get());
        assertFalse(thunder.get());
        assertFalse(rules.get(WorldController.ManagedRule.TIME));
        assertFalse(rules.get(WorldController.ManagedRule.SPAWNS));
        controller.refresh();
        controller.close();
        assertEquals(61_000, time.get());
        assertTrue(storm.get());
        assertTrue(thunder.get());
        assertEquals(123, rainDuration.get());
        assertEquals(456, thunderDuration.get());
        assertEquals(789, clearDuration.get());
        assertTrue(rules.get(WorldController.ManagedRule.TIME));
        assertFalse(rules.get(WorldController.ManagedRule.WEATHER));
        assertTrue(rules.get(WorldController.ManagedRule.SPAWNS));
    }

    @Test void disablingOneFeatureRestoresOnlyThatFeatureAndCapturesFreshBaselineWhenReenabled() {
        controller.refresh();
        config.set("time.enabled", false);
        controller.refresh();
        assertEquals(61_000, time.get());
        assertTrue(rules.get(WorldController.ManagedRule.TIME));
        assertFalse(storm.get());
        time.set(88_000);
        config.set("time.enabled", true);
        config.set("time.ticks", 18_000);
        controller.refresh();
        assertEquals(18_000, world.getTime());
        controller.close();
        assertEquals(88_000, time.get());
    }

    @Test void removingWorldFromScopeRestoresStateAndLeavesUnprotectedWorldUntouched() {
        controller.refresh();
        config.set("worlds", List.of("another_world"));
        controller.refresh();
        assertEquals(61_000, time.get());
        assertTrue(storm.get());
        clearInvocations(world);
        controller.refresh();
        verify(world, never()).setTime(anyLong());
        verify(world, never()).setStorm(anyBoolean());
    }

    @Test void disabledPluginDoesNotCaptureOrChangeWorldSettings() {
        config.set("enabled", false);
        controller.refresh();
        controller.close();
        verify(world, never()).setTime(anyLong());
        verify(world, never()).setFullTime(anyLong());
        verify(world, never()).setStorm(anyBoolean());
    }

    @Test void acceptsConfiguredRainAndThunderAndRejectsConflictingWeatherEvents() {
        config.set("weather.kind", "RAIN");
        controller.refresh();
        assertTrue(storm.get());
        assertFalse(thunder.get());
        WeatherChangeEvent clear = new WeatherChangeEvent(world, false);
        controller.onWeatherChange(clear);
        assertTrue(clear.isCancelled());
        WeatherChangeEvent rain = new WeatherChangeEvent(world, true);
        controller.onWeatherChange(rain);
        assertFalse(rain.isCancelled());
        ThunderChangeEvent thunderEvent = new ThunderChangeEvent(world, true);
        controller.onThunderChange(thunderEvent);
        assertTrue(thunderEvent.isCancelled());
        config.set("weather.kind", "THUNDER");
        controller.refresh();
        assertTrue(thunder.get());
        ThunderChangeEvent allowed = new ThunderChangeEvent(world, true);
        controller.onThunderChange(allowed);
        assertFalse(allowed.isCancelled());
    }

    @Test void timeSkipIsBlockedOnlyInWorldsWithTimeLock() {
        TimeSkipEvent locked = new TimeSkipEvent(world, ClockTimeSkipEvent.SkipReason.CUSTOM, 100);
        controller.onTimeSkip(locked);
        assertTrue(locked.isCancelled());
        config.set("time.enabled", false);
        TimeSkipEvent unlocked = new TimeSkipEvent(world, ClockTimeSkipEvent.SkipReason.CUSTOM, 100);
        controller.onTimeSkip(unlocked);
        assertFalse(unlocked.isCancelled());
    }

    @Test void newlyLoadedProtectedWorldReceivesLocksImmediately() {
        controller.onWorldLoad(new WorldLoadEvent(world));
        assertEquals(6000, world.getTime());
        assertFalse(storm.get());
        controller.close();
        assertEquals(61_000, time.get());
    }

    @Test void fixedTimeDimensionsKeepProtectionWithoutAnyUnsupportedTimeWrites() {
        when(world.isFixedTime()).thenReturn(true);
        controller.refresh();
        assertTrue(rules.get(WorldController.ManagedRule.TIME));
        assertFalse(rules.get(WorldController.ManagedRule.SPAWNS));
        assertFalse(storm.get());
        controller.close();
        verify(world, never()).setTime(anyLong());
        verify(world, never()).setFullTime(anyLong());
        assertTrue(storm.get());
    }

    @Test void customClocklessDimensionsCannotAbortWorldOrPlayerProtection() {
        doThrow(new IllegalArgumentException("Cannot set time in a world without a world clock")).when(world).setTime(anyLong());
        doThrow(new IllegalArgumentException("Cannot set time in a world without a world clock")).when(world).setFullTime(anyLong());
        assertDoesNotThrow(controller::refresh);
        assertFalse(storm.get());
        assertFalse(rules.get(WorldController.ManagedRule.SPAWNS));
        assertTrue(rules.get(WorldController.ManagedRule.TIME));
        assertDoesNotThrow(controller::refresh);
        verify(world, times(1)).setTime(anyLong());
        assertDoesNotThrow(controller::close);
        assertTrue(rules.get(WorldController.ManagedRule.SPAWNS));
    }
}
