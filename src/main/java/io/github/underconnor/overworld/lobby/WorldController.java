package io.github.underconnor.overworld.lobby;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.bukkit.GameRule;
import org.bukkit.GameRules;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.weather.ThunderChangeEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.event.world.TimeSkipEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** Owns only the settings it changes, so removing a lock restores its previous value. */
public final class WorldController implements Listener, AutoCloseable {
    private final JavaPlugin plugin;
    private final Supplier<Settings> settings;
    private final WorldRules rules;
    private final Map<UUID, WorldState> states = new HashMap<>();
    private BukkitTask task;
    private boolean changing;

    public WorldController(JavaPlugin plugin, Supplier<Settings> settings) {
        this(plugin, settings, new WorldRules() {
            @Override public Boolean read(World world, ManagedRule rule) { return world.getGameRuleValue(bukkitRule(rule)); }
            @Override public void write(World world, ManagedRule rule, boolean value) { world.setGameRule(bukkitRule(rule), value); }
            private GameRule<Boolean> bukkitRule(ManagedRule rule) {
                return switch (rule) {
                    case TIME -> GameRules.ADVANCE_TIME;
                    case WEATHER -> GameRules.ADVANCE_WEATHER;
                    case SPAWNS -> GameRules.SPAWN_MOBS;
                };
            }
        });
    }

    WorldController(JavaPlugin plugin, Supplier<Settings> settings, WorldRules rules) {
        this.plugin = plugin;
        this.settings = settings;
        this.rules = rules;
    }

    public void start() {
        if (task != null) return;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        refresh();
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::refresh, 20L, 20L);
    }

    public void refresh() {
        Settings current = settings.get();
        states.values().removeIf(state -> {
            if (current.protects(state.world)) return false;
            restore(state);
            return true;
        });
        for (World world : plugin.getServer().getWorlds()) {
            if (current.protects(world)) apply(world, current);
        }
    }

    private void apply(World world, Settings current) {
        WorldState state = states.computeIfAbsent(world.getUID(), id -> new WorldState(world));
        changing = true;
        try {
            if (current.time().enabled() && !world.isFixedTime() && !state.timeUnsupported) {
                boolean firstLock = state.time == null;
                if (firstLock) state.time = new TimeState(world.getFullTime(), rules.read(world, ManagedRule.TIME));
                try {
                    // The first write probes custom dimensions whose missing clock has no public API predicate.
                    if (firstLock || world.getTime() != current.time().ticks()) world.setTime(current.time().ticks());
                    rules.write(world, ManagedRule.TIME, false);
                } catch (IllegalArgumentException unsupportedClock) {
                    restoreTime(state);
                    state.timeUnsupported = true;
                }
            } else restoreTime(state);

            if (current.weather().enabled()) {
                if (state.weather == null) state.weather = new WeatherState(world.hasStorm(), world.isThundering(),
                    world.getWeatherDuration(), world.getThunderDuration(), world.getClearWeatherDuration(),
                    rules.read(world, ManagedRule.WEATHER));
                rules.write(world, ManagedRule.WEATHER, false);
                boolean rain = current.weather().kind() != Settings.WeatherKind.CLEAR;
                boolean thunder = current.weather().kind() == Settings.WeatherKind.THUNDER;
                if (world.hasStorm() != rain) world.setStorm(rain);
                if (world.isThundering() != thunder) world.setThundering(thunder);
                world.setWeatherDuration(Integer.MAX_VALUE);
                world.setThunderDuration(Integer.MAX_VALUE);
                world.setClearWeatherDuration(rain ? 0 : Integer.MAX_VALUE);
            } else restoreWeather(state);

            if (current.preventNaturalSpawns()) {
                if (!state.spawnsManaged) {
                    state.mobSpawning = rules.read(world, ManagedRule.SPAWNS);
                    state.spawnsManaged = true;
                }
                rules.write(world, ManagedRule.SPAWNS, false);
            } else restoreSpawns(state);
        } finally {
            changing = false;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldLoad(WorldLoadEvent event) {
        Settings current = settings.get();
        if (current.protects(event.getWorld())) apply(event.getWorld(), current);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        WorldState state = states.remove(event.getWorld().getUID());
        if (state != null) restore(state);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onWeatherChange(WeatherChangeEvent event) {
        Settings current = settings.get();
        if (!changing && current.protects(event.getWorld()) && current.weather().enabled()
            && event.toWeatherState() != (current.weather().kind() != Settings.WeatherKind.CLEAR))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onThunderChange(ThunderChangeEvent event) {
        Settings current = settings.get();
        if (!changing && current.protects(event.getWorld()) && current.weather().enabled()
            && event.toThunderState() != (current.weather().kind() == Settings.WeatherKind.THUNDER))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTimeSkip(TimeSkipEvent event) {
        Settings current = settings.get();
        if (!changing && current.protects(event.getWorld()) && current.time().enabled()) event.setCancelled(true);
    }

    private void restore(WorldState state) {
        changing = true;
        try {
            restoreTime(state);
            restoreWeather(state);
            restoreSpawns(state);
        } finally {
            changing = false;
        }
    }

    private void restoreTime(WorldState state) {
        if (state.time == null) return;
        try {
            state.world.setFullTime(state.time.fullTime);
        } catch (IllegalArgumentException unsupportedClock) {
            // Clockless dimensions cannot accept time writes, including restoring an unchanged time.
            state.timeUnsupported = true;
        }
        if (state.time.daylightCycle != null) rules.write(state.world, ManagedRule.TIME, state.time.daylightCycle);
        state.time = null;
    }

    private void restoreWeather(WorldState state) {
        if (state.weather == null) return;
        WeatherState saved = state.weather;
        state.world.setStorm(saved.storm);
        state.world.setThundering(saved.thunder);
        state.world.setWeatherDuration(saved.weatherDuration);
        state.world.setThunderDuration(saved.thunderDuration);
        state.world.setClearWeatherDuration(saved.clearWeatherDuration);
        if (saved.weatherCycle != null) rules.write(state.world, ManagedRule.WEATHER, saved.weatherCycle);
        state.weather = null;
    }

    private void restoreSpawns(WorldState state) {
        if (!state.spawnsManaged) return;
        if (state.mobSpawning != null) rules.write(state.world, ManagedRule.SPAWNS, state.mobSpawning);
        state.spawnsManaged = false;
    }

    @Override public void close() {
        if (task != null) { task.cancel(); task = null; }
        HandlerList.unregisterAll(this);
        states.values().forEach(this::restore);
        states.clear();
    }

    private static final class WorldState {
        final World world;
        TimeState time;
        boolean timeUnsupported;
        WeatherState weather;
        Boolean mobSpawning;
        boolean spawnsManaged;
        WorldState(World world) { this.world = world; }
    }
    private record TimeState(long fullTime, Boolean daylightCycle) { }
    private record WeatherState(boolean storm, boolean thunder, int weatherDuration,
                                int thunderDuration, int clearWeatherDuration, Boolean weatherCycle) { }
    enum ManagedRule { TIME, WEATHER, SPAWNS }
    interface WorldRules {
        Boolean read(World world, ManagedRule rule);
        void write(World world, ManagedRule rule, boolean value);
    }
}
