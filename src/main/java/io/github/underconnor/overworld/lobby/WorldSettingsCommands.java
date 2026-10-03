package io.github.underconnor.overworld.lobby;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;
import java.util.logging.Level;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Persists validated, world-specific environment overrides before applying them. */
public final class WorldSettingsCommands {
    @FunctionalInterface public interface ReloadSettings { void reload() throws Exception; }
    @FunctionalInterface interface ConfigWriter { void write(Path target, byte[] contents) throws IOException; }
    private static final List<String> TIME_VALUES = List.of("day", "night", "noon", "midnight", "off", "default");
    private static final List<String> WEATHER_VALUES = List.of("clear", "rain", "thunder", "off", "default");
    private final JavaPlugin plugin;
    private final Supplier<Settings> settings;
    private final ReloadSettings reloadSettings;
    private final ConfigWriter writer;

    public WorldSettingsCommands(JavaPlugin plugin, Supplier<Settings> settings, ReloadSettings reloadSettings) {
        this(plugin, settings, reloadSettings, WorldSettingsCommands::writeAtomically);
    }

    WorldSettingsCommands(JavaPlugin plugin, Supplier<Settings> settings, ReloadSettings reloadSettings, ConfigWriter writer) {
        this.plugin = plugin;
        this.settings = settings;
        this.reloadSettings = reloadSettings;
        this.writer = writer;
    }

    /** Returns false only when the arguments belong to another lobby subcommand. */
    public boolean handle(CommandSender sender, String label, String[] args) {
        if (args.length == 0 || !recognized(args[0])) return false;
        String type = args[0].toLowerCase(Locale.ROOT);
        if (!authorized(sender)) {
            sender.sendMessage("§c월드의 시간·날씨를 변경할 권한이 없습니다.");
            return true;
        }
        if (args.length < 2 || args.length > 3) {
            usage(sender, label, type);
            return true;
        }
        String value = args[1].toLowerCase(Locale.ROOT);
        Long ticks = null;
        if (type.equals("time")) {
            try { ticks = timeTicks(value); }
            catch (IllegalArgumentException error) { usage(sender, label, type); return true; }
        } else if (!WEATHER_VALUES.contains(value)) {
            usage(sender, label, type);
            return true;
        }
        World world;
        if (args.length == 3) world = plugin.getServer().getWorld(args[2]);
        else if (sender instanceof Player player) world = player.getWorld();
        else {
            sender.sendMessage("§c콘솔에서는 변경할 월드 이름을 입력하세요.");
            return true;
        }
        if (world == null || !plugin.getServer().getWorlds().contains(world)) {
            sender.sendMessage("§c로드된 월드를 찾을 수 없습니다.");
            return true;
        }
        if (!settings.get().protects(world)) {
            sender.sendMessage("§c로비 보호가 적용되는 월드만 변경할 수 있습니다.");
            return true;
        }
        if (type.equals("time") && ticks != null && world.isFixedTime()) {
            sender.sendMessage("§c이 월드는 고정된 차원 시간을 사용하므로 시간을 변경할 수 없습니다.");
            return true;
        }
        if (type.equals("weather") && !value.equals("off") && !value.equals("default")
            && (world.getEnvironment() == World.Environment.NETHER || world.getEnvironment() == World.Environment.THE_END)) {
            sender.sendMessage("§c이 차원은 일반 날씨를 지원하지 않아 날씨를 변경할 수 없습니다.");
            return true;
        }
        Path file = plugin.getDataFolder().toPath().resolve("config.yml");
        byte[] original;
        byte[] updated;
        try {
            original = Files.readAllBytes(file);
            YamlConfiguration candidate = read(original);
            Settings existing = Settings.load(candidate);
            DenialMessageSettings.load(candidate);
            if (!existing.protects(world)) throw new IllegalArgumentException("파일 설정에서 해당 월드가 보호 대상이 아닙니다.");
            update(candidate, world.getName(), type, value, ticks);
            Settings.load(candidate);
            DenialMessageSettings.load(candidate);
            updated = candidate.saveToString().getBytes(StandardCharsets.UTF_8);
        } catch (Exception error) {
            sender.sendMessage("§c설정을 변경하지 못했습니다: " + error.getMessage());
            return true;
        }
        try {
            writer.write(file, updated);
        } catch (Exception error) {
            plugin.getLogger().log(Level.WARNING, "월드 설정 저장 실패", error);
            sender.sendMessage("§c설정을 저장하지 못해 기존 설정을 유지합니다.");
            return true;
        }
        try {
            reloadSettings.reload();
        } catch (Exception error) {
            try {
                writer.write(file, original);
                reloadSettings.reload();
            } catch (Exception rollbackError) {
                error.addSuppressed(rollbackError);
                plugin.getLogger().log(Level.SEVERE, "월드 설정 적용 및 복원 실패", error);
                sender.sendMessage("§c설정 복원에 실패했습니다. 서버 로그를 확인하세요.");
                return true;
            }
            plugin.getLogger().log(Level.WARNING, "월드 설정 적용 실패로 기존 설정 복원", error);
            sender.sendMessage("§c설정 적용에 실패해 기존 설정을 복원했습니다.");
            return true;
        }
        sender.sendMessage("§a" + world.getName() + " 월드의 " + (type.equals("time") ? "시간" : "날씨")
            + " 설정을 " + value + "로 적용했습니다.");
        return true;
    }

    /** Null delegates unrelated subcommands back to the main completer. */
    public List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 0 || !recognized(args[0])) return null;
        if (!authorized(sender)) return List.of();
        if (args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return (args[0].equalsIgnoreCase("time") ? TIME_VALUES : WEATHER_VALUES).stream()
                .filter(value -> value.startsWith(prefix)).toList();
        }
        if (args.length == 3) {
            String prefix = args[2].toLowerCase(Locale.ROOT);
            return plugin.getServer().getWorlds().stream().filter(settings.get()::protects)
                .map(World::getName).filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
        }
        return List.of();
    }

    private static boolean recognized(String command) {
        return command.equalsIgnoreCase("time") || command.equalsIgnoreCase("weather");
    }
    private static boolean authorized(CommandSender sender) {
        return sender.isOp() || sender.hasPermission("overworld.lobby.admin");
    }
    private static void usage(CommandSender sender, String label, String type) {
        sender.sendMessage("§e사용법: /" + label + " " + type + " <"
            + (type.equals("time") ? "day|night|noon|midnight|0..23999|off|default" : "clear|rain|thunder|off|default")
            + "> [world]");
    }
    private static Long timeTicks(String value) {
        return switch (value) {
            case "day" -> 1000L;
            case "night" -> 13000L;
            case "noon" -> 6000L;
            case "midnight" -> 18000L;
            case "off", "default" -> null;
            default -> {
                if (!value.matches("[0-9]+")) throw new IllegalArgumentException();
                long ticks;
                try { ticks = Long.parseLong(value); } catch (NumberFormatException error) { throw new IllegalArgumentException(error); }
                if (ticks > 23999) throw new IllegalArgumentException();
                yield ticks;
            }
        };
    }
    private static YamlConfiguration read(byte[] contents) throws Exception {
        YamlConfiguration candidate = new YamlConfiguration();
        candidate.options().parseComments(true).pathSeparator('\u0000');
        candidate.loadFromString(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(contents)).toString());
        candidate.options().pathSeparator('.');
        return candidate;
    }
    private static void update(YamlConfiguration candidate, String worldName, String type, String value, Long ticks) {
        ConfigurationSection overrides = candidate.getConfigurationSection("world-settings");
        if (overrides == null) overrides = candidate.createSection("world-settings");
        ConfigurationSection world;
        candidate.options().pathSeparator('\u0000');
        try {
            world = overrides.getConfigurationSection(worldName);
            if (world == null) world = overrides.createSection(worldName);
        } finally { candidate.options().pathSeparator('.'); }
        if (value.equals("default")) world.set(type, null);
        else {
            ConfigurationSection section = world.getConfigurationSection(type);
            if (section == null) section = world.createSection(type);
            section.set("enabled", !value.equals("off"));
            if (!value.equals("off")) section.set(type.equals("time") ? "ticks" : "kind", ticks == null ? value.toUpperCase(Locale.ROOT) : ticks);
        }
        if (world.getKeys(false).isEmpty()) {
            candidate.options().pathSeparator('\u0000');
            try { overrides.set(worldName, null); } finally { candidate.options().pathSeparator('.'); }
        }
        if (overrides.getKeys(false).isEmpty()) candidate.set("world-settings", null);
    }

    private static void writeAtomically(Path target, byte[] contents) throws IOException {
        Path temporary = Files.createTempFile(target.toAbsolutePath().getParent(), ".overworld-lobby-", ".tmp");
        try {
            Set<PosixFilePermission> permissions = null;
            try { permissions = Files.getPosixFilePermissions(target); }
            catch (UnsupportedOperationException ignored) { }
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer data = ByteBuffer.wrap(contents);
                while (data.hasRemaining()) channel.write(data);
                channel.force(true);
            }
            if (permissions != null) Files.setPosixFilePermissions(temporary, permissions);
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
        }
    }
}
