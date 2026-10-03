package io.github.underconnor.overworld.lobby;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/** Invisible, world-scoped rectangles; every player, including OP, stays inside. */
public final class BorderController implements Listener, AutoCloseable {
    private final JavaPlugin plugin;
    private final ProtectionPolicy policy;
    private final DenialMessages messages;
    private final Map<UUID, Location> lastInside = new HashMap<>();
    private final Set<UUID> warned = new HashSet<>();
    private BukkitTask task;
    private boolean closed;

    public BorderController(JavaPlugin plugin, ProtectionPolicy policy, DenialMessages messages) {
        this.plugin = plugin; this.policy = policy; this.messages = messages;
    }
    public void start() {
        if (task != null) return;
        closed = false;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        refresh();
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::refresh, 20L, 20L);
    }
    public void refresh() {
        for (Player player : plugin.getServer().getOnlinePlayers()) recover(player);
    }
    private Settings.BorderSettings active(World world) {
        if (world == null || !policy.protects(world) || !policy.settings().protections().contains(Action.WORLD_BORDER)) return null;
        Settings.BorderSettings border = policy.settings().borderFor(world);
        return border != null && border.enabled() ? border : null;
    }
    private static boolean inside(Location location, Settings.BorderSettings border) {
        return location != null && border.contains(location.getX(), location.getZ());
    }
    private void remember(Player player, Location location) {
        lastInside.put(player.getUniqueId(), location.clone());
        warned.remove(player.getUniqueId());
    }
    private void denied(Player player) {
        if (messages != null) messages.send(player, Action.WORLD_BORDER);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event instanceof PlayerTeleportEvent || event.getTo() == null) return;
        Location to = event.getTo(); Settings.BorderSettings border = active(to.getWorld());
        if (border == null) return;
        if (inside(to, border)) { remember(event.getPlayer(), to); return; }
        Location from = event.getFrom();
        if (from.getWorld() == to.getWorld() && inside(from, border)) {
            Location restored = from.clone(); restored.setYaw(to.getYaw()); restored.setPitch(to.getPitch());
            event.setTo(restored); remember(event.getPlayer(), restored);
        } else {
            Location restored = safe(event.getPlayer(), to, border);
            if (restored == null) event.setCancelled(true); else event.setTo(restored);
        }
        denied(event.getPlayer());
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getTo() == null) return;
        Settings.BorderSettings border = active(event.getTo().getWorld());
        if (border != null && !inside(event.getTo(), border)) { event.setCancelled(true); denied(event.getPlayer()); }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        Location target = event.getRespawnLocation(); Settings.BorderSettings border = active(target.getWorld());
        if (border == null || inside(target, border)) return;
        Location corrected = safe(event.getPlayer(), target, border);
        if (corrected != null) event.setRespawnLocation(corrected);
        queue(event.getPlayer());
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) { lastInside.remove(event.getPlayer().getUniqueId()); queue(event.getPlayer()); }
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) { queue(event.getPlayer()); }
    @EventHandler public void onQuit(PlayerQuitEvent event) {
        lastInside.remove(event.getPlayer().getUniqueId()); warned.remove(event.getPlayer().getUniqueId());
    }
    private void queue(Player player) {
        plugin.getServer().getScheduler().runTask(plugin, () -> { if (!closed && player.isOnline()) recover(player); });
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVehicleEnter(VehicleEnterEvent event) {
        if (!(event.getEntered() instanceof Player player)) return;
        Location location = event.getVehicle().getLocation(); Settings.BorderSettings border = active(location.getWorld());
        if (border != null && !inside(location, border)) { event.setCancelled(true); denied(player); }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onVehicleMove(VehicleMoveEvent event) {
        Location to = event.getTo(); Settings.BorderSettings border = active(to.getWorld());
        if (border == null || inside(to, border)) return;
        ArrayDeque<Entity> queue = new ArrayDeque<>(event.getVehicle().getPassengers());
        Set<Entity> visited = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        while (!queue.isEmpty()) {
            Entity passenger = queue.removeFirst(); if (!visited.add(passenger)) continue;
            queue.addAll(passenger.getPassengers());
            if (!(passenger instanceof Player player)) continue;
            Location from = event.getFrom();
            Location target = from.getWorld() == to.getWorld() && inside(from, border) ? from.clone() : safe(player, to, border);
            player.leaveVehicle();
            if (target != null && player.teleport(target)) {
                player.setFallDistance(0); player.setVelocity(new Vector()); remember(player, target);
            }
            denied(player);
        }
    }
    private void recover(Player player) {
        Location current = player.getLocation(); Settings.BorderSettings border = active(current.getWorld());
        if (border == null) { lastInside.remove(player.getUniqueId()); warned.remove(player.getUniqueId()); return; }
        if (inside(current, border)) { remember(player, current); return; }
        Location target = safe(player, current, border);
        if (target == null) {
            denied(player);
            if (warned.add(player.getUniqueId())) plugin.getLogger().warning("월드 경계 안의 안전한 복구 지점을 찾지 못했습니다: " + current.getWorld().getName());
            return;
        }
        if (player.isInsideVehicle()) player.leaveVehicle();
        if (player.teleport(target)) {
            player.setFallDistance(0); player.setVelocity(new Vector()); remember(player, target); denied(player);
        }
    }
    private Location safe(Player player, Location current, Settings.BorderSettings border) {
        World world = current.getWorld();
        Location saved = lastInside.get(player.getUniqueId());
        if (saved != null && saved.getWorld() == world && inside(saved, border) && clear(saved)) return saved.clone();
        Settings.SpawnSettings spawn = policy.settings().spawn();
        boolean spawnWorld = spawn.world().isBlank()
            ? plugin.getServer().getWorlds().stream().filter(policy::protects).findFirst().orElse(null) == world
            : spawn.world().equals(world.getName());
        if (spawn.enabled() && spawnWorld && border.contains(spawn.x(), spawn.z())) {
            Location target = new Location(world, spawn.x(), spawn.y(), spawn.z(), spawn.yaw(), spawn.pitch());
            if (clear(target) && (airSafe(player) || solidFloor(target))) return target;
        }
        double x = Math.max(border.minX(), Math.min(border.maxX(), current.getX()));
        double z = Math.max(border.minZ(), Math.min(border.maxZ(), current.getZ()));
        double y = Math.max(world.getMinHeight() + 1, Math.min(world.getMaxHeight() - 2, current.getY()));
        Location air = new Location(world, x, y, z, current.getYaw(), current.getPitch());
        if (airSafe(player) && clear(air)) return air;
        for (int radius = 0; radius <= 4; radius++) for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
            double sx = Math.floor(x) + dx + 0.5, sz = Math.floor(z) + dz + 0.5;
            if (!border.contains(sx, sz)) continue;
            int top = world.getHighestBlockYAt((int)Math.floor(sx), (int)Math.floor(sz));
            Location target = new Location(world, sx, top + 1.0, sz, current.getYaw(), current.getPitch());
            if (clear(target) && solidFloor(target)) return target;
        }
        return null;
    }
    private static boolean airSafe(Player player) {
        return player.isFlying() || player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR;
    }
    private static boolean clear(Location location) {
        World world = location.getWorld();
        if (world == null || !Double.isFinite(location.getY()) || location.getY() < world.getMinHeight() || location.getY() + 1.8 >= world.getMaxHeight()) return false;
        for (int x = (int)Math.floor(location.getX() - 0.31); x <= (int)Math.floor(location.getX() + 0.31); x++)
            for (int z = (int)Math.floor(location.getZ() - 0.31); z <= (int)Math.floor(location.getZ() + 0.31); z++)
                for (int y = (int)Math.floor(location.getY()); y <= (int)Math.floor(location.getY() + 1.8); y++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (!block.isPassable() || block.isLiquid() || hazardous(block.getType())) return false;
                }
        return true;
    }
    private static boolean solidFloor(Location location) {
        Block floor = location.getWorld().getBlockAt(location.getBlockX(), (int)Math.floor(location.getY() - 0.01), location.getBlockZ());
        return floor.getType().isSolid() && !hazardous(floor.getType());
    }
    private static boolean hazardous(Material material) {
        return material == Material.LAVA || material == Material.FIRE || material == Material.SOUL_FIRE
            || material == Material.MAGMA_BLOCK || material == Material.CAMPFIRE || material == Material.SOUL_CAMPFIRE || material == Material.CACTUS;
    }
    @Override public void close() {
        closed = true;
        if (task != null) { task.cancel(); task = null; }
        HandlerList.unregisterAll(this); lastInside.clear(); warned.clear();
    }
}
