package io.github.underconnor.overworld.lobby;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class OverworldLobby extends JavaPlugin {
    private Settings settings;
    private DenialMessageSettings messageSettings;
    private DenialMessages messages;
    private ProtectionPolicy policy;
    private WorldController worlds;
    private PlayerController players;
    private SpawnController spawn;
    private WorldSettingsCommands worldCommands;
    private SpawnSettingsCommands spawnCommands;
    private BorderCommands borderCommands;
    private BorderController borders;

    @Override public void onEnable() {
        saveDefaultConfig();
        try {
            LoadedSettings candidate = readSettings();
            settings = candidate.protection();
            messageSettings = candidate.messages();
        }
        catch (Exception ex) {
            getLogger().severe("설정을 읽지 못했습니다: " + ex.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        policy = new ProtectionPolicy(() -> settings);
        messages = new DenialMessages(() -> messageSettings);
        worlds = new WorldController(this, () -> settings);
        players = new PlayerController(this, policy);
        spawn = new SpawnController(this, policy);
        worldCommands = new WorldSettingsCommands(this, () -> settings, this::reloadSettings);
        spawnCommands = new SpawnSettingsCommands(this, () -> settings, this::reloadSettings);
        borderCommands = new BorderCommands(this, () -> settings, this::reloadSettings);
        borders = new BorderController(this, policy, messages);
        getServer().getPluginManager().registerEvents(new ProtectionListener(policy, messages), this);
        getServer().getPluginManager().registerEvents(new EnvironmentListener(policy), this);
        Objects.requireNonNull(getCommand("lobby")).setExecutor(this);
        Objects.requireNonNull(getCommand("lobby")).setTabCompleter(this);
        Objects.requireNonNull(getCommand("spawn")).setExecutor(this);
        Objects.requireNonNull(getCommand("setspawn")).setExecutor(this);
        Objects.requireNonNull(getCommand("setspawn")).setTabCompleter(this);
        worlds.start();
        players.start();
        spawn.start();
        borders.start();
        getLogger().info("로비 보호 활성화. LuckPerms를 포함한 Bukkit 권한 제공자의 권한을 매번 확인합니다.");
    }

    @Override public void onDisable() {
        if (borders != null) borders.close();
        if (spawn != null) spawn.close();
        if (players != null) players.close();
        if (worlds != null) worlds.close();
        if (messages != null) messages.close();
    }

    private LoadedSettings readSettings() throws Exception {
        YamlConfiguration candidate = new YamlConfiguration();
        candidate.options().pathSeparator('\0');
        candidate.load(new File(getDataFolder(), "config.yml"));
        candidate.options().pathSeparator('.');
        return new LoadedSettings(Settings.load(candidate), DenialMessageSettings.load(candidate));
    }

    private void reloadSettings() throws Exception {
        LoadedSettings candidate = readSettings();
        settings = candidate.protection();
        messageSettings = candidate.messages();
        messages.close();
        worlds.refresh();
        players.refresh();
        spawn.refresh();
        borders.refresh();
    }

    private record LoadedSettings(Settings protection, DenialMessageSettings messages) { }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("setspawn")) {
            spawnCommands.handle(sender, "lobby", withSetSpawn(args));
            return true;
        }
        if (command.getName().equalsIgnoreCase("spawn")) {
            if (!sender.hasPermission("overworld.lobby.spawn")) {
                sender.sendMessage("§c로비 스폰으로 이동할 권한이 없습니다.");
            } else if (!(sender instanceof Player player)) {
                sender.sendMessage("플레이어만 사용할 수 있습니다.");
            } else if (args.length != 0) {
                sender.sendMessage("§e사용법: /" + label);
            } else if (!spawn.teleport(player)) {
                sender.sendMessage("§c스폰 월드나 이동 설정을 확인할 수 없거나 이동이 취소됐습니다.");
            }
            return true;
        }
        if (worldCommands.handle(sender, label, args)) return true;
        if (spawnCommands.handle(sender, label, args)) return true;
        if (borderCommands.handle(sender, label, args)) return true;
        if (args.length == 1 && args[0].equalsIgnoreCase("status")) {
            if (!sender.isOp() && !sender.hasPermission("overworld.lobby.admin")) {
                sender.sendMessage("§c로비 상태를 확인할 권한이 없습니다.");
                return true;
            }
            int online = 0, operators = 0, permissionBypass = 0, effectiveBypass = 0, creative = 0, survival = 0;
            for (Player player : getServer().getOnlinePlayers()) {
                online++;
                if (player.isOp()) operators++;
                if (player.hasPermission("overworld.lobby.bypass")) permissionBypass++;
                if (policy.hasGlobalBypass(player)) effectiveBypass++;
                if (player.getGameMode() == org.bukkit.GameMode.CREATIVE) creative++;
                if (player.getGameMode() == org.bukkit.GameMode.SURVIVAL) survival++;
            }
            sender.sendMessage("OverworldLobby " + getPluginMeta().getVersion() + " online=" + online
                + " op=" + operators + " permission-bypass=" + permissionBypass + " effective-bypass=" + effectiveBypass
                + " creative=" + creative + " survival=" + survival);
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.isOp() && !sender.hasPermission("overworld.lobby.admin")) {
                sender.sendMessage("§c설정을 다시 불러올 권한이 없습니다.");
                return true;
            }
            try {
                reloadSettings();
                sender.sendMessage("§a로비 설정을 다시 불러왔습니다.");
            } catch (Exception ex) {
                sender.sendMessage("§c설정을 적용하지 못했습니다: " + ex.getMessage());
                getLogger().warning("로비 설정 다시 불러오기 실패: " + ex.getMessage());
            }
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("fly")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("플레이어만 사용할 수 있습니다.");
                return true;
            }
            if (args.length > 2 || (args.length == 2 && !List.of("on", "off").contains(args[1].toLowerCase(Locale.ROOT)))) {
                sender.sendMessage("§e사용법: /" + label + " fly [on|off]");
                return true;
            }
            if (!settings.protects(player.getWorld()) || (!policy.hasGlobalBypass(player)
                && (!settings.allowFlight() || !player.hasPermission("overworld.lobby.fly")))) {
                sender.sendMessage("§c이 월드에서는 로비 비행을 사용할 수 없습니다.");
                return true;
            }
            boolean flying = args.length == 2 ? args[1].equalsIgnoreCase("on") : !player.isFlying();
            if (!players.toggleFlight(player, flying)) {
                sender.sendMessage("§c현재 게임 모드나 보호 우회 권한에서는 로비 비행을 적용하지 않습니다.");
                return true;
            }
            sender.sendMessage(flying ? "§a비행을 켰습니다." : "§a비행을 껐습니다.");
            return true;
        }
        sender.sendMessage("§e/" + label + " fly [on|off] — 로비 비행 켜기·끄기");
        if (sender.isOp() || sender.hasPermission("overworld.lobby.admin")) {
            sender.sendMessage("§e/" + label + " setspawn — 현재 위치·시선으로 기본 스폰 저장");
            sender.sendMessage("§e/" + label + " border set <x1> <z1> <x2> <z2> [월드] — 보이지 않는 사각 경계 저장");
            sender.sendMessage("§e/" + label + " border <pos1|pos2|off|info> — 코너 선택·해제·조회");
            sender.sendMessage("§e/" + label + " time <day|night|noon|midnight|0..23999|off|default> [월드] — 월드별 시간");
            sender.sendMessage("§e/" + label + " weather <clear|rain|thunder|off|default> [월드] — 월드별 날씨");
            sender.sendMessage("§e/" + label + " reload — 설정 다시 불러오기");
            sender.sendMessage("§e/" + label + " status — 접속자의 OP·우회·게임 모드 집계");
        }
        return true;
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("setspawn")) {
            List<String> suggestions = spawnCommands.complete(sender, withSetSpawn(args));
            return suggestions == null ? List.of() : suggestions;
        }
        if (args.length == 1) return (sender.isOp() || sender.hasPermission("overworld.lobby.admin")
            ? List.of("fly", "time", "weather", "setspawn", "border", "reload", "status") : List.of("fly")).stream()
            .filter(value -> value.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        List<String> worldSuggestions = worldCommands.complete(sender, args);
        if (worldSuggestions != null) return worldSuggestions;
        List<String> spawnSuggestions = spawnCommands.complete(sender, args);
        if (spawnSuggestions != null) return spawnSuggestions;
        List<String> borderSuggestions = borderCommands.complete(sender, args);
        if (borderSuggestions != null) return borderSuggestions;
        if (args.length == 2 && args[0].equalsIgnoreCase("fly"))
            return Arrays.stream(new String[]{"on", "off"}).filter(value -> value.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        return List.of();
    }

    private static String[] withSetSpawn(String[] args) {
        String[] forwarded = new String[args.length + 1];
        forwarded[0] = "setspawn";
        System.arraycopy(args, 0, forwarded, 1, args.length);
        return forwarded;
    }
}
