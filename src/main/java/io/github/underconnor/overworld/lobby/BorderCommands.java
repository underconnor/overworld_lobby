package io.github.underconnor.overworld.lobby;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;
import java.util.logging.Level;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Two-corner rectangular limits stored separately for each literal world name. */
public final class BorderCommands {
    private record Corner(World world, double x, double z) { }
    private final JavaPlugin plugin;
    private final Supplier<Settings> settings;
    private final WorldSettingsCommands.ReloadSettings reloadSettings;
    private final WorldSettingsCommands.ConfigWriter writer;
    private final Map<CommandSender, Corner> firstCorners = new WeakHashMap<>();
    public BorderCommands(JavaPlugin plugin, Supplier<Settings> settings, WorldSettingsCommands.ReloadSettings reloadSettings) {
        this(plugin, settings, reloadSettings, WorldSettingsCommands::writeAtomically);
    }
    BorderCommands(JavaPlugin plugin, Supplier<Settings> settings, WorldSettingsCommands.ReloadSettings reloadSettings,
                   WorldSettingsCommands.ConfigWriter writer) {
        this.plugin = plugin; this.settings = settings; this.reloadSettings = reloadSettings; this.writer = writer;
    }
    public boolean handle(CommandSender sender, String label, String[] args) {
        if (args.length == 0 || !args[0].equalsIgnoreCase("border")) return false;
        if (!authorized(sender)) {
            sender.sendMessage("§c월드 경계를 변경하거나 확인할 권한이 없습니다."); return true;
        }
        if (args.length < 2) { usage(sender, label); return true; }
        String action = args[1].toLowerCase(Locale.ROOT);
        try {
            switch (action) {
                case "set" -> {
                    if (args.length != 6 && args.length != 7) { usage(sender, label); return true; }
                    World world = resolve(sender, args.length == 7 ? args[6] : null);
                    Settings.BorderSettings old = settings.get().borderFor(world);
                    Settings.BorderSettings border = rectangle(coordinate(args[2]), coordinate(args[3]), coordinate(args[4]), coordinate(args[5]), old);
                    if (persist(sender, world, border)) firstCorners.remove(sender);
                }
                case "pos1", "pos2" -> {
                    if (args.length < 2 || args.length > 5) { usage(sender, label); return true; }
                    boolean currentPosition = args.length <= 3;
                    String namedWorld = currentPosition ? (args.length == 3 ? args[2] : null) : (args.length == 5 ? args[4] : null);
                    World world = resolve(sender, namedWorld);
                    double x, z;
                    if (currentPosition) {
                        if (!(sender instanceof Player player)) throw new IllegalArgumentException("콘솔에서는 X·Z 좌표와 월드 이름을 입력하세요.");
                        Location location = player.getLocation(); x = validCoordinate(location.getX()); z = validCoordinate(location.getZ());
                    } else { x = coordinate(args[2]); z = coordinate(args[3]); }
                    if (action.equals("pos1")) {
                        firstCorners.put(sender, new Corner(world, x, z));
                        sender.sendMessage("§a" + world.getName() + " 월드의 첫째 꼭짓점을 " + x + ", " + z + "로 선택했습니다.");
                    } else {
                        Corner first = firstCorners.get(sender);
                        if (first == null) throw new IllegalArgumentException("먼저 border pos1으로 첫째 꼭짓점을 선택하세요.");
                        if (first.world() != world) throw new IllegalArgumentException("두 꼭짓점은 같은 월드에서 선택해야 합니다.");
                        if (persist(sender, world, rectangle(first.x(), first.z(), x, z, settings.get().borderFor(world)))) firstCorners.remove(sender);
                    }
                }
                case "off", "info" -> {
                    if (args.length != 2 && args.length != 3) { usage(sender, label); return true; }
                    World world = resolve(sender, args.length == 3 ? args[2] : null);
                    Settings.BorderSettings border = settings.get().borderFor(world);
                    if (action.equals("info")) {
                        sender.sendMessage("§e" + world.getName() + ": " + (border == null ? "경계 없음" : describe(border))); return true;
                    }
                    if (border == null || !border.enabled()) sender.sendMessage("§a" + world.getName() + " 월드의 경계가 이미 꺼져 있습니다.");
                    else persist(sender, world, new Settings.BorderSettings(false, border.minX(), border.maxX(), border.minZ(), border.maxZ()));
                }
                default -> usage(sender, label);
            }
        } catch (IllegalArgumentException error) { sender.sendMessage("§c경계를 변경하지 못했습니다: " + error.getMessage()); }
        return true;
    }
    public List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 0 || !args[0].equalsIgnoreCase("border")) return null;
        if (!authorized(sender)) return List.of();
        if (args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return List.of("set", "pos1", "pos2", "off", "info").stream().filter(value -> value.startsWith(prefix)).toList();
        }
        String action = args.length < 2 ? "" : args[1].toLowerCase(Locale.ROOT);
        boolean worldArgument = ((action.equals("off") || action.equals("info") || action.equals("pos1") || action.equals("pos2")) && args.length == 3)
            || ((action.equals("pos1") || action.equals("pos2")) && args.length == 5) || (action.equals("set") && args.length == 7);
        if (worldArgument) {
            String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
            return plugin.getServer().getWorlds().stream().filter(settings.get()::protects).map(World::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
        }
        return List.of();
    }
    private World resolve(CommandSender sender, String name) {
        World world;
        if (name != null) world = plugin.getServer().getWorld(name);
        else if (sender instanceof Player player) world = player.getWorld();
        else throw new IllegalArgumentException("콘솔에서는 월드 이름을 입력하세요.");
        if (world == null || !plugin.getServer().getWorlds().contains(world)) throw new IllegalArgumentException("로드된 월드를 찾을 수 없습니다.");
        if (!settings.get().protects(world)) throw new IllegalArgumentException("로비 보호가 적용되는 월드만 사용할 수 있습니다.");
        return world;
    }
    private static Settings.BorderSettings rectangle(double x1, double z1, double x2, double z2, Settings.BorderSettings old) {
        return new Settings.BorderSettings(true, Math.min(x1, x2), Math.max(x1, x2), Math.min(z1, z2), Math.max(z1, z2));
    }
    private boolean persist(CommandSender sender, World world, Settings.BorderSettings border) {
        Path file = plugin.getDataFolder().toPath().resolve("config.yml"); byte[] original, updated;
        try {
            original = Files.readAllBytes(file); YamlConfiguration candidate = WorldSettingsCommands.read(original);
            Settings existing = Settings.load(candidate); DenialMessageSettings.load(candidate);
            if (!existing.protects(world)) throw new IllegalArgumentException("파일 설정에서 해당 월드가 보호 대상이 아닙니다.");
            Settings.SpawnSettings spawn = existing.spawn();
            World spawnWorld = spawn.world().isBlank() ? plugin.getServer().getWorlds().stream().filter(existing::protects).findFirst().orElse(null) : plugin.getServer().getWorld(spawn.world());
            if (border.enabled() && spawn.enabled() && world.equals(spawnWorld) && !border.contains(spawn.x(), spawn.z()))
                throw new IllegalArgumentException("현재 스폰을 경계 안으로 먼저 설정하세요.");
            ConfigurationSection borders = candidate.getConfigurationSection("world-borders");
            if (borders == null) borders = candidate.createSection("world-borders");
            ConfigurationSection configured;
            candidate.options().pathSeparator('\u0000');
            try {
                configured = borders.getConfigurationSection(world.getName());
                if (configured == null) configured = borders.createSection(world.getName());
            } finally { candidate.options().pathSeparator('.'); }
            configured.set("enabled", border.enabled()); configured.set("min-x", border.minX()); configured.set("max-x", border.maxX());
            configured.set("min-z", border.minZ()); configured.set("max-z", border.maxZ());
            Settings.load(candidate); DenialMessageSettings.load(candidate); updated = candidate.saveToString().getBytes(StandardCharsets.UTF_8);
        } catch (Exception error) { sender.sendMessage("§c설정을 변경하지 못했습니다: " + error.getMessage()); return false; }
        try { writer.write(file, updated); }
        catch (Exception error) {
            plugin.getLogger().log(Level.WARNING, "경계 설정 저장 실패", error);
            sender.sendMessage("§c설정을 저장하지 못해 기존 경계를 유지합니다."); return false;
        }
        try { reloadSettings.reload(); }
        catch (Exception error) {
            try { writer.write(file, original); reloadSettings.reload(); }
            catch (Exception rollbackError) {
                error.addSuppressed(rollbackError); plugin.getLogger().log(Level.SEVERE, "경계 설정 적용 및 복원 실패", error);
                sender.sendMessage("§c경계 설정 복원에 실패했습니다. 서버 로그를 확인하세요."); return false;
            }
            plugin.getLogger().log(Level.WARNING, "경계 설정 적용 실패로 기존 설정 복원", error);
            sender.sendMessage("§c설정 적용에 실패해 기존 경계를 복원했습니다."); return false;
        }
        sender.sendMessage("§a" + world.getName() + " 월드의 경계를 적용했습니다: " + describe(border)); return true;
    }
    private static String describe(Settings.BorderSettings border) {
        return (border.enabled() ? "켜짐" : "꺼짐") + ", X=" + border.minX() + ".." + border.maxX() + ", Z=" + border.minZ() + ".." + border.maxZ()
            + ", OP 포함 적용";
    }
    private static double coordinate(String value) {
        try { return validCoordinate(Double.parseDouble(value)); }
        catch (NumberFormatException error) { throw new IllegalArgumentException("좌표는 숫자여야 합니다."); }
    }
    private static double validCoordinate(double value) {
        if (!Double.isFinite(value) || Math.abs(value) > Settings.MAX_HORIZONTAL_COORDINATE)
            throw new IllegalArgumentException("X·Z 좌표는 -29999984부터 29999984 사이의 유한한 숫자여야 합니다.");
        return value;
    }
    private static boolean authorized(CommandSender sender) { return sender.isOp() || sender.hasPermission("overworld.lobby.admin"); }
    private static void usage(CommandSender sender, String label) {
        sender.sendMessage("§e/" + label + " border set <x1> <z1> <x2> <z2> [world]");
        sender.sendMessage("§e/" + label + " border pos1|pos2 [x z] [world] · off|info [world]");
    }
}
