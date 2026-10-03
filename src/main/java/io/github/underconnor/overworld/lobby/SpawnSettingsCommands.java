package io.github.underconnor.overworld.lobby;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.logging.Level;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Changes the plugin's exact spawn without changing native or Multiverse world spawns. */
public final class SpawnSettingsCommands {
    private final JavaPlugin plugin;
    private final Supplier<Settings> settings;
    private final WorldSettingsCommands.ReloadSettings reloadSettings;
    private final WorldSettingsCommands.ConfigWriter writer;

    public SpawnSettingsCommands(JavaPlugin plugin, Supplier<Settings> settings,
                                 WorldSettingsCommands.ReloadSettings reloadSettings) {
        this(plugin, settings, reloadSettings, WorldSettingsCommands::writeAtomically);
    }
    SpawnSettingsCommands(JavaPlugin plugin, Supplier<Settings> settings,
                          WorldSettingsCommands.ReloadSettings reloadSettings, WorldSettingsCommands.ConfigWriter writer) {
        this.plugin = plugin;
        this.settings = settings;
        this.reloadSettings = reloadSettings;
        this.writer = writer;
    }

    /** Arguments include the setspawn subcommand, including when routed from /setspawn. */
    public boolean handle(CommandSender sender, String label, String[] args) {
        if (args.length == 0 || !args[0].equalsIgnoreCase("setspawn")) return false;
        if (!authorized(sender)) {
            sender.sendMessage("§c로비 스폰을 설정할 권한이 없습니다.");
            return true;
        }
        if (args.length != 1 && (args.length < 5 || args.length > 7)) {
            usage(sender, label);
            return true;
        }
        Location target;
        try {
            if (args.length == 1) {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("§c콘솔에서는 월드와 좌표를 입력하세요.");
                    return true;
                }
                target = player.getLocation().clone();
                target.setYaw(normalizeImplicitYaw(target.getYaw()));
            } else {
                World world = plugin.getServer().getWorld(args[1]);
                float yaw = sender instanceof Player player ? normalizeImplicitYaw(player.getLocation().getYaw()) : 0;
                float pitch = sender instanceof Player player ? player.getLocation().getPitch() : 0;
                if (args.length >= 6) yaw = angle(args[5], -360, 360, "yaw");
                if (args.length >= 7) pitch = angle(args[6], -90, 90, "pitch");
                target = new Location(world, coordinate(args[2]), coordinate(args[3]), coordinate(args[4]), yaw, pitch);
            }
            validate(target);
        } catch (IllegalArgumentException error) {
            sender.sendMessage("§c스폰을 설정하지 못했습니다: " + error.getMessage());
            return true;
        }
        World world = target.getWorld();
        if (world == null || !plugin.getServer().getWorlds().contains(world)) {
            sender.sendMessage("§c로드된 월드를 찾을 수 없습니다.");
            return true;
        }
        if (!settings.get().protects(world)) {
            sender.sendMessage("§c로비 보호가 적용되는 월드만 스폰으로 설정할 수 있습니다.");
            return true;
        }
        if (target.getY() < world.getMinHeight() || target.getY() >= world.getMaxHeight()) {
            sender.sendMessage("§c높이는 월드의 최소 높이 이상, 최대 높이 미만이어야 합니다.");
            return true;
        }
        Path file = plugin.getDataFolder().toPath().resolve("config.yml");
        byte[] original;
        byte[] updated;
        try {
            original = Files.readAllBytes(file);
            YamlConfiguration candidate = WorldSettingsCommands.read(original);
            Settings existing = Settings.load(candidate);
            DenialMessageSettings.load(candidate);
            if (!existing.protects(world)) throw new IllegalArgumentException("파일 설정에서 해당 월드가 보호 대상이 아닙니다.");
            Settings.BorderSettings border = existing.borderFor(world);
            if (border != null && border.enabled() && !border.contains(target.getX(), target.getZ()))
                throw new IllegalArgumentException("스폰은 월드 경계 안에 설정하세요.");
            candidate.set("spawn.enabled", true);
            candidate.set("spawn.world", world.getName());
            candidate.set("spawn.x", target.getX()); candidate.set("spawn.y", target.getY()); candidate.set("spawn.z", target.getZ());
            candidate.set("spawn.yaw", target.getYaw()); candidate.set("spawn.pitch", target.getPitch());
            Settings.load(candidate);
            DenialMessageSettings.load(candidate);
            updated = candidate.saveToString().getBytes(StandardCharsets.UTF_8);
        } catch (Exception error) {
            sender.sendMessage("§c설정을 변경하지 못했습니다: " + error.getMessage());
            return true;
        }
        try { writer.write(file, updated); }
        catch (Exception error) {
            plugin.getLogger().log(Level.WARNING, "스폰 설정 저장 실패", error);
            sender.sendMessage("§c설정을 저장하지 못해 기존 스폰을 유지합니다.");
            return true;
        }
        try { reloadSettings.reload(); }
        catch (Exception error) {
            try { writer.write(file, original); reloadSettings.reload(); }
            catch (Exception rollbackError) {
                error.addSuppressed(rollbackError);
                plugin.getLogger().log(Level.SEVERE, "스폰 설정 적용 및 복원 실패", error);
                sender.sendMessage("§c스폰 설정 복원에 실패했습니다. 서버 로그를 확인하세요.");
                return true;
            }
            plugin.getLogger().log(Level.WARNING, "스폰 설정 적용 실패로 기존 설정 복원", error);
            sender.sendMessage("§c설정 적용에 실패해 기존 스폰을 복원했습니다.");
            return true;
        }
        sender.sendMessage("§a" + world.getName() + " 월드의 로비 스폰을 " + target.getX() + ", " + target.getY() + ", "
            + target.getZ() + " (시선 " + target.getYaw() + ", " + target.getPitch() + ")로 설정했습니다.");
        return true;
    }

    public List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 0 || !args[0].equalsIgnoreCase("setspawn")) return null;
        if (!authorized(sender)) return List.of();
        if (args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return plugin.getServer().getWorlds().stream().filter(settings.get()::protects).map(World::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
        }
        if (sender instanceof Player player && args.length >= 3 && args.length <= 7) {
            Location location = player.getLocation();
            String suggestion = switch (args.length) {
                case 3 -> Double.toString(location.getX());
                case 4 -> Double.toString(location.getY());
                case 5 -> Double.toString(location.getZ());
                case 6 -> Float.toString(normalizeImplicitYaw(location.getYaw()));
                case 7 -> Float.toString(location.getPitch());
                default -> "";
            };
            return suggestion.startsWith(args[args.length - 1]) ? List.of(suggestion) : List.of();
        }
        return List.of();
    }
    private static boolean authorized(CommandSender sender) {
        return sender.isOp() || sender.hasPermission("overworld.lobby.admin");
    }
    private static void usage(CommandSender sender, String label) {
        String base = label.equalsIgnoreCase("setspawn") || label.toLowerCase(Locale.ROOT).endsWith(":setspawn")
            ? "/" + label : "/" + label + " setspawn";
        sender.sendMessage("§e사용법: " + base + " [world x y z [yaw] [pitch]]");
    }
    private static double coordinate(String value) {
        double result;
        try { result = Double.parseDouble(value); }
        catch (NumberFormatException error) { throw new IllegalArgumentException("좌표는 숫자여야 합니다."); }
        if (!Double.isFinite(result)) throw new IllegalArgumentException("좌표는 유한한 숫자여야 합니다.");
        return result;
    }
    private static float angle(String value, double minimum, double maximum, String name) {
        double result = coordinate(value);
        if (result < minimum || result > maximum) throw new IllegalArgumentException(name + "은 " + minimum + "부터 " + maximum + " 사이여야 합니다.");
        return (float) result;
    }
    private static float normalizeImplicitYaw(float yaw) {
        return yaw < -360 || yaw > 360 ? yaw % 360 : yaw;
    }
    private static void validate(Location location) {
        if (!Double.isFinite(location.getX()) || !Double.isFinite(location.getY()) || !Double.isFinite(location.getZ()))
            throw new IllegalArgumentException("좌표는 유한한 숫자여야 합니다.");
        if (Math.abs(location.getX()) > Settings.MAX_HORIZONTAL_COORDINATE || Math.abs(location.getZ()) > Settings.MAX_HORIZONTAL_COORDINATE)
            throw new IllegalArgumentException("X·Z 좌표는 -29999984부터 29999984 사이여야 합니다.");
        if (!Float.isFinite(location.getYaw()) || !Float.isFinite(location.getPitch())
            || location.getYaw() < -360 || location.getYaw() > 360 || location.getPitch() < -90 || location.getPitch() > 90)
            throw new IllegalArgumentException("yaw는 -360부터 360, pitch는 -90부터 90 사이여야 합니다.");
    }
}
