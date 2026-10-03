package io.github.underconnor.overworld.lobby;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public record Settings(
    boolean enabled, Set<String> worlds, GameMode gameMode, boolean allowFlight,
    float flySpeed, boolean keepFoodFull, TimeSettings time, WeatherSettings weather, SpawnSettings spawn, boolean preventNaturalSpawns,
    Set<Action> protections, Set<EnvironmentRule> environmentRules, Set<Material> allowedContainers,
    Map<String, WorldSettings> worldSettings
) {
    public enum WeatherKind { CLEAR, RAIN, THUNDER }
    public record TimeSettings(boolean enabled, long ticks) { }
    public record WeatherSettings(boolean enabled, WeatherKind kind) { }
    public record WorldSettings(TimeSettings time, WeatherSettings weather) { }
    public record SpawnSettings(boolean enabled, String world, double x, double y, double z,
                                float yaw, float pitch, boolean onJoin, boolean onRespawn, boolean voidRescue) { }

    public Settings {
        worlds = Set.copyOf(worlds);
        protections = Set.copyOf(protections);
        environmentRules = Set.copyOf(environmentRules);
        allowedContainers = Set.copyOf(allowedContainers);
        worldSettings = Map.copyOf(worldSettings);
    }

    public Settings(boolean enabled, Set<String> worlds, GameMode gameMode, boolean allowFlight,
                    float flySpeed, boolean keepFoodFull, TimeSettings time, WeatherSettings weather,
                    SpawnSettings spawn, boolean preventNaturalSpawns, Set<Action> protections,
                    Set<EnvironmentRule> environmentRules, Set<Material> allowedContainers) {
        this(enabled, worlds, gameMode, allowFlight, flySpeed, keepFoodFull, time, weather, spawn,
            preventNaturalSpawns, protections, environmentRules, allowedContainers, Map.of());
    }

    public boolean protects(World world) {
        return enabled && world != null && (worlds.isEmpty() || worlds.contains(world.getName()));
    }

    public TimeSettings timeFor(World world) {
        WorldSettings override = world == null ? null : worldSettings.get(world.getName());
        return override == null || override.time() == null ? time : override.time();
    }

    public WeatherSettings weatherFor(World world) {
        WorldSettings override = world == null ? null : worldSettings.get(world.getName());
        return override == null || override.weather() == null ? weather : override.weather();
    }

    public static Settings load(FileConfiguration config) {
        for (String section : java.util.List.of("players", "time", "weather", "spawn", "protection", "environment", "world-settings"))
            if (config.contains(section) && !config.isConfigurationSection(section))
                throw invalid(section, "설정 항목을 담는 YAML 구역이어야 합니다.");
        Set<String> worlds = new LinkedHashSet<>();
        if (config.contains("worlds") && !config.isList("worlds"))
            throw invalid("worlds", "월드 이름 목록이어야 합니다.");
        for (Object value : config.getList("worlds", java.util.List.of())) {
            if (!(value instanceof String name) || name.isBlank())
                throw invalid("worlds", "빈 이름 없이 월드 이름을 문자열로 입력하세요.");
            worlds.add(name);
        }
        GameMode mode;
        try { mode = GameMode.valueOf(string(config, "players.game-mode", "SURVIVAL").toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ex) { throw invalid("players.game-mode", "SURVIVAL, ADVENTURE, CREATIVE, SPECTATOR 중 하나여야 합니다."); }
        double speed = number(config, "players.fly-speed", 0.1);
        if (speed < 0 || speed > 1) throw invalid("players.fly-speed", "0부터 1 사이여야 합니다.");
        double ticks = number(config, "time.ticks", 6000);
        if (ticks != Math.floor(ticks) || ticks < 0 || ticks > 23999)
            throw invalid("time.ticks", "0부터 23999 사이의 정수여야 합니다.");
        WeatherKind weather;
        try { weather = WeatherKind.valueOf(string(config, "weather.kind", "CLEAR").toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ex) { throw invalid("weather.kind", "CLEAR, RAIN, THUNDER 중 하나여야 합니다."); }
        Object configuredSpawnWorld = config.get("spawn.world", "");
        if (!(configuredSpawnWorld instanceof String spawnWorld))
            throw invalid("spawn.world", "월드 이름 또는 빈 문자열이어야 합니다.");
        double yaw = number(config, "spawn.yaw", 180), pitch = number(config, "spawn.pitch", 0);
        if (yaw < -360 || yaw > 360) throw invalid("spawn.yaw", "-360부터 360 사이여야 합니다.");
        if (pitch < -90 || pitch > 90) throw invalid("spawn.pitch", "-90부터 90 사이여야 합니다.");
        SpawnSettings spawn = new SpawnSettings(bool(config, "spawn.enabled", true), spawnWorld,
            number(config, "spawn.x", -4.5), number(config, "spawn.y", 63.5), number(config, "spawn.z", -1.5),
            (float) yaw, (float) pitch, bool(config, "spawn.on-join", true), bool(config, "spawn.on-respawn", true),
            bool(config, "spawn.void-rescue", true));
        Set<Action> protections = EnumSet.noneOf(Action.class);
        for (Action action : Action.values())
            if (bool(config, "protection." + action.key(), action != Action.CONTAINERS)) protections.add(action);
        Set<EnvironmentRule> rules = EnumSet.noneOf(EnvironmentRule.class);
        for (EnvironmentRule rule : EnvironmentRule.values())
            if (bool(config, "environment." + rule.key(), true)) rules.add(rule);
        Set<Material> containers = EnumSet.noneOf(Material.class);
        if (config.contains("allowed-containers") && !config.isList("allowed-containers"))
            throw invalid("allowed-containers", "블록 이름 목록이어야 합니다.");
        java.util.List<?> configuredContainers = config.getList("allowed-containers");
        if (configuredContainers == null) {
            containers.addAll(Set.of(Material.CHEST, Material.TRAPPED_CHEST, Material.ENDER_CHEST, Material.BARREL));
            Arrays.stream(Material.values()).filter(Settings::isStorage).forEach(containers::add);
        } else for (Object value : configuredContainers) {
            if (!(value instanceof String name)) throw invalid("allowed-containers", "블록 이름을 문자열로 입력하세요.");
            Material material = Material.matchMaterial(name);
            if (material == null || !isStorage(material))
                throw invalid("allowed-containers", "지원하는 상자 블록이 아닙니다: " + name);
            containers.add(material);
        }
        TimeSettings globalTime = new TimeSettings(bool(config, "time.enabled", true), (long) ticks);
        WeatherSettings globalWeather = new WeatherSettings(bool(config, "weather.enabled", true), weather);
        Map<String, WorldSettings> worldSettings = new LinkedHashMap<>();
        ConfigurationSection overrides = config.getConfigurationSection("world-settings");
        if (overrides != null) for (var entry : overrides.getValues(false).entrySet()) {
            String worldName = entry.getKey();
            if (worldName.isBlank() || !(entry.getValue() instanceof ConfigurationSection worldConfig))
                throw invalid("world-settings." + worldName, "월드 이름 아래에 시간·날씨 설정 구역을 입력하세요.");
            for (String section : java.util.List.of("time", "weather"))
                if (worldConfig.contains(section) && !worldConfig.isConfigurationSection(section))
                    throw invalid("world-settings." + worldName + "." + section, "설정 항목을 담는 YAML 구역이어야 합니다.");
            TimeSettings worldTime = null;
            ConfigurationSection timeConfig = worldConfig.getConfigurationSection("time");
            if (timeConfig != null) {
                double worldTicks = number(timeConfig, "ticks", globalTime.ticks());
                if (worldTicks != Math.floor(worldTicks) || worldTicks < 0 || worldTicks > 23999)
                    throw invalid("world-settings." + worldName + ".time.ticks", "0부터 23999 사이의 정수여야 합니다.");
                worldTime = new TimeSettings(bool(timeConfig, "enabled", globalTime.enabled()), (long) worldTicks);
            }
            WeatherSettings worldWeather = null;
            ConfigurationSection weatherConfig = worldConfig.getConfigurationSection("weather");
            if (weatherConfig != null) {
                WeatherKind worldKind;
                try { worldKind = WeatherKind.valueOf(string(weatherConfig, "kind", globalWeather.kind().name()).toUpperCase(Locale.ROOT)); }
                catch (IllegalArgumentException error) { throw invalid("world-settings." + worldName + ".weather.kind", "CLEAR, RAIN, THUNDER 중 하나여야 합니다."); }
                worldWeather = new WeatherSettings(bool(weatherConfig, "enabled", globalWeather.enabled()), worldKind);
            }
            worldSettings.put(worldName, new WorldSettings(worldTime, worldWeather));
        }
        return new Settings(bool(config, "enabled", true), worlds, mode,
            bool(config, "players.allow-flight", true), (float) speed, bool(config, "players.keep-food-full", true),
            globalTime,
            globalWeather,
            spawn,
            bool(config, "prevent-natural-spawns", true), protections, rules, containers, worldSettings);
    }

    private static boolean isStorage(Material material) {
        return material == Material.CHEST || material == Material.TRAPPED_CHEST
            || material == Material.ENDER_CHEST || material == Material.BARREL
            || (!material.name().startsWith("LEGACY_") && material.name().endsWith("SHULKER_BOX"));
    }
    private static boolean bool(ConfigurationSection config, String key, boolean fallback) {
        Object value = config.get(key);
        if (value == null) return fallback;
        if (!(value instanceof Boolean result)) throw invalid(key, "true 또는 false여야 합니다.");
        return result;
    }
    private static String string(ConfigurationSection config, String key, String fallback) {
        Object value = config.get(key);
        if (value == null) return fallback;
        if (!(value instanceof String result) || result.isBlank()) throw invalid(key, "문자열이어야 합니다.");
        return result.trim();
    }
    private static double number(ConfigurationSection config, String key, double fallback) {
        Object value = config.get(key);
        if (value == null) return fallback;
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue()))
            throw invalid(key, "유한한 숫자여야 합니다.");
        return number.doubleValue();
    }
    private static IllegalArgumentException invalid(String key, String message) {
        return new IllegalArgumentException(key + ": " + message);
    }
}
