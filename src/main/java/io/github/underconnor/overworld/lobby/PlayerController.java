package io.github.underconnor.overworld.lobby;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** Session-scoped player settings are restored when a player leaves the lobby. */
public final class PlayerController implements Listener, AutoCloseable {
    private final JavaPlugin plugin;
    private final ProtectionPolicy policy;
    private final Map<UUID, PlayerState> states = new HashMap<>();
    private final Map<UUID, FlightState> flightStates = new HashMap<>();
    private final Map<UUID, FoodState> foodStates = new HashMap<>();
    private final Set<UUID> bypassSessions = new HashSet<>();
    private BukkitTask task;
    private boolean changing;
    private boolean closed;

    public PlayerController(JavaPlugin plugin, ProtectionPolicy policy) {
        this.plugin = plugin;
        this.policy = policy;
    }

    public void start() {
        if (task != null) return;
        closed = false;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        refresh();
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::refresh, 20L, 20L);
    }

    /** Re-evaluating Bukkit permissions also picks up LuckPerms updates without a hard dependency. */
    public void refresh() {
        for (Player player : plugin.getServer().getOnlinePlayers()) apply(player);
    }

    private boolean managed(Player player) {
        return policy.protects(player.getWorld()) && !policy.hasGlobalBypass(player)
            && !player.hasPermission("overworld.lobby.bypass.mode");
    }

    private boolean canFly(Player player) {
        return policy.settings().allowFlight() && player.hasPermission("overworld.lobby.fly");
    }

    private static boolean vanillaFlight(GameMode mode) {
        return mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR;
    }

    private void apply(Player player) {
        boolean globalBypass = policy.hasGlobalBypass(player);
        if (!globalBypass) bypassSessions.remove(player.getUniqueId());
        applyFood(player);
        if (!managed(player)) {
            releaseMode(player);
            if (!policy.protects(player.getWorld())) {
                releaseFlight(player);
                return;
            }
            FlightState previous = flightStates.get(player.getUniqueId());
            if (previous != null && previous.globalBypass != globalBypass) releaseFlight(player);
            FlightState state = flightStates.computeIfAbsent(player.getUniqueId(), id -> new FlightState(player, globalBypass));
            applyFlight(player, vanillaFlight(player.getGameMode()) || ((globalBypass || canFly(player)) && state.flightEnabled));
            if (globalBypass && bypassSessions.add(player.getUniqueId()))
                scheduleBypassDefault(player);
            return;
        }
        releaseFlight(player);
        PlayerState state = states.computeIfAbsent(player.getUniqueId(), id -> new PlayerState(player));
        Settings current = policy.settings();
        changing = true;
        try {
            if (player.getGameMode() != current.gameMode()) player.setGameMode(current.gameMode());
            applyFlight(player, vanillaFlight(player.getGameMode()) || (canFly(player) && state.flightEnabled));
            if (player.getFlySpeed() != current.flySpeed()) player.setFlySpeed(current.flySpeed());
        } finally {
            changing = false;
        }
    }

    private void applyFlight(Player player, boolean allowed) {
        if (!allowed && player.isFlying()) {
            player.setFlying(false);
            player.setFallDistance(0);
        }
        if (player.getAllowFlight() != allowed) player.setAllowFlight(allowed);
    }

    /** Starts or stops flight while retaining the player's choice during permission refreshes. */
    public boolean toggleFlight(Player player, boolean enabled) {
        if (!policy.protects(player.getWorld()) || (!policy.hasGlobalBypass(player) && !canFly(player))
            || (managed(player) && vanillaFlight(policy.settings().gameMode()))) return false;
        apply(player);
        if (managed(player)) states.get(player.getUniqueId()).flightEnabled = enabled;
        else flightStates.get(player.getUniqueId()).flightEnabled = enabled;
        apply(player);
        if (player.isFlying() != enabled) player.setFlying(enabled);
        if (!enabled) player.setFallDistance(0);
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        bypassSessions.remove(event.getPlayer().getUniqueId());
        apply(event.getPlayer());
        scheduleApply(event.getPlayer());
    }

    private void scheduleBypassDefault(Player player) {
        // Spawn and Multiverse login positioning run first; the staff default is applied once afterwards.
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (closed || !player.isOnline() || !bypassSessions.contains(player.getUniqueId())
                || !policy.protects(player.getWorld()) || !policy.hasGlobalBypass(player)) return;
            if (player.getGameMode() != GameMode.CREATIVE) player.setGameMode(GameMode.CREATIVE);
            apply(player);
        }, 2L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        apply(event.getPlayer());
        scheduleApply(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        // Respawn finishes after the event and may replace the player's vanilla flight flags.
        scheduleApply(event.getPlayer());
    }

    private void scheduleApply(Player player) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!closed && player.isOnline()) apply(player);
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        if (changing) return;
        if (managed(event.getPlayer()) && event.getNewGameMode() != policy.settings().gameMode()) {
            event.setCancelled(true);
            return;
        }
        // Bukkit applies vanilla abilities after this event, so repair only on the following tick.
        if (policy.protects(event.getPlayer().getWorld())) scheduleApply(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (!policy.protects(player.getWorld()) || vanillaFlight(player.getGameMode()) || !event.isFlying()) return;
        PlayerState state = states.get(player.getUniqueId());
        FlightState bypassed = flightStates.get(player.getUniqueId());
        if ((!policy.hasGlobalBypass(player) && !canFly(player)) || (state != null && !state.flightEnabled)
            || (bypassed != null && !bypassed.flightEnabled)) {
            event.setCancelled(true);
            apply(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        bypassSessions.remove(event.getPlayer().getUniqueId());
        restoreFood(event.getPlayer());
        releaseMode(event.getPlayer());
        releaseFlight(event.getPlayer());
    }

    private void releaseFlight(Player player) {
        FlightState state = flightStates.remove(player.getUniqueId());
        if (state == null) return;
        boolean allowed = vanillaFlight(player.getGameMode()) || (!vanillaFlight(state.mode) && state.allowFlight);
        if (player.isFlying()) player.setFlying(false);
        if (player.getAllowFlight() != allowed) player.setAllowFlight(allowed);
        if (state.flying && allowed) player.setFlying(true);
        player.setFallDistance(0);
    }

    private void releaseMode(Player player) {
        PlayerState state = states.remove(player.getUniqueId());
        if (state == null) return;
        changing = true;
        try {
            if (player.isFlying()) player.setFlying(false);
            if (player.getGameMode() != state.mode) player.setGameMode(state.mode);
            player.setAllowFlight(state.allowFlight);
            player.setFlySpeed(state.flySpeed);
            if (state.flying && state.allowFlight) player.setFlying(true);
            player.setFallDistance(0);
        } finally {
            changing = false;
        }
    }

    @Override public void close() {
        closed = true;
        if (task != null) { task.cancel(); task = null; }
        HandlerList.unregisterAll(this);
        for (PlayerState state : java.util.List.copyOf(states.values())) releaseMode(state.player);
        states.clear();
        for (FlightState state : java.util.List.copyOf(flightStates.values())) releaseFlight(state.player);
        flightStates.clear();
        for (FoodState state : java.util.List.copyOf(foodStates.values())) restoreFood(state.player);
        foodStates.clear();
        bypassSessions.clear();
    }

    private void applyFood(Player player) {
        if (policy.settings().keepFoodFull() && policy.blocks(Action.HUNGER, player, player.getWorld())) {
            foodStates.computeIfAbsent(player.getUniqueId(), id ->
                new FoodState(player, player.getFoodLevel(), player.getSaturation(), player.getExhaustion()));
            if (player.getFoodLevel() != 20) player.setFoodLevel(20);
            if (player.getSaturation() != 20) player.setSaturation(20);
            if (player.getExhaustion() != 0) player.setExhaustion(0);
        } else restoreFood(player);
    }

    private void restoreFood(Player player) {
        FoodState saved = foodStates.remove(player.getUniqueId());
        if (saved == null) return;
        player.setFoodLevel(saved.level);
        player.setSaturation(saved.saturation);
        player.setExhaustion(saved.exhaustion);
    }

    private static final class PlayerState {
        final Player player;
        final GameMode mode;
        final boolean allowFlight;
        final boolean flying;
        final float flySpeed;
        boolean flightEnabled = true;
        PlayerState(Player player) {
            this.player = player;
            mode = player.getGameMode();
            allowFlight = player.getAllowFlight();
            flying = player.isFlying();
            flySpeed = player.getFlySpeed();
        }
    }
    private record FoodState(Player player, int level, float saturation, float exhaustion) { }
    private static final class FlightState {
        final Player player;
        final GameMode mode;
        final boolean allowFlight;
        final boolean flying;
        final boolean globalBypass;
        boolean flightEnabled = true;
        FlightState(Player player, boolean globalBypass) {
            this.player = player;
            mode = player.getGameMode();
            allowFlight = player.getAllowFlight();
            flying = player.isFlying();
            this.globalBypass = globalBypass;
        }
    }
}
