package io.github.underconnor.overworld.lobby;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Uses an exact player location instead of the world's block-aligned spawn position. */
public final class SpawnController implements Listener, AutoCloseable {
    private final JavaPlugin plugin;
    private final ProtectionPolicy policy;
    private boolean running;
    private String lastWarning;

    public SpawnController(JavaPlugin plugin, ProtectionPolicy policy) {
        this.plugin = plugin;
        this.policy = policy;
    }

    public void start() {
        if (running) return;
        running = true;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        refresh();
        if (policy.settings().spawn().onJoin()) {
            for (Player player : plugin.getServer().getOnlinePlayers()) scheduleJoin(player);
        }
    }

    /** Revalidates a changed configuration without relocating players already in the lobby. */
    public void refresh() { destination(); }

    /** Command permissions are checked by the command handler; automatic bypasses do not disable /spawn. */
    public boolean teleport(Player player) {
        Location destination = destination();
        if (destination == null || !player.teleport(destination)) return false;
        player.setFallDistance(0);
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (policy.settings().spawn().onJoin()) scheduleJoin(event.getPlayer());
    }

    private void scheduleJoin(Player player) {
        if (!automaticallyMoves(player)) return;
        // A next-tick teleport avoids the server's login positioning overwriting the exact spawn.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (running && player.isOnline() && policy.settings().spawn().onJoin() && automaticallyMoves(player))
                teleport(player);
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (!policy.settings().spawn().onRespawn() || !automaticallyMoves(player)) return;
        Location destination = destination();
        if (destination != null) {
            event.setRespawnLocation(destination);
            player.setFallDistance(0);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVoidMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();
        if (to == null || !policy.settings().spawn().voidRescue() || !automaticallyMoves(player)
            || to.getWorld() == null || !policy.protects(to.getWorld())
            || to.getY() >= to.getWorld().getMinHeight() - 8) return;
        Location destination = destination();
        if (destination != null) {
            event.setTo(destination);
            player.setFallDistance(0);
        }
    }

    private boolean automaticallyMoves(Player player) {
        return policy.settings().spawn().enabled() && policy.protects(player.getWorld())
            && !player.hasPermission("overworld.lobby.bypass")
            && !player.hasPermission("overworld.lobby.bypass.spawn");
    }

    private Location destination() {
        Settings settings = policy.settings();
        Settings.SpawnSettings spawn = settings.spawn();
        if (!settings.enabled() || !spawn.enabled()) return null;
        World world;
        if (spawn.world().isBlank()) {
            world = plugin.getServer().getWorlds().stream().filter(policy::protects).findFirst().orElse(null);
            if (world == null) return warn("로비 스폰에 사용할 보호된 월드가 로드되지 않았습니다.");
        } else {
            world = plugin.getServer().getWorld(spawn.world());
            if (world == null) return warn("로비 스폰 월드가 로드되지 않았습니다: " + spawn.world());
            if (!policy.protects(world)) return warn("로비 스폰 월드가 worlds 보호 범위 밖에 있습니다: " + spawn.world());
        }
        lastWarning = null;
        return new Location(world, spawn.x(), spawn.y(), spawn.z(), spawn.yaw(), spawn.pitch());
    }

    private Location warn(String message) {
        if (!message.equals(lastWarning)) {
            plugin.getLogger().warning(message);
            lastWarning = message;
        }
        return null;
    }

    @Override public void close() {
        running = false;
        HandlerList.unregisterAll(this);
    }
}
