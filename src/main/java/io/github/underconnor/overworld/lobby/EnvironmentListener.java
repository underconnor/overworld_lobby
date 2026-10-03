package io.github.underconnor.overworld.lobby;

import java.util.EnumSet;
import java.util.Set;
import org.bukkit.World;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.world.StructureGrowEvent;

/** Environmental changes have no permission owner; player-caused changes keep their bypass. */
public final class EnvironmentListener implements Listener {
    private static final Set<SpawnReason> AUTOMATIC_SPAWNS = EnumSet.of(
        SpawnReason.NATURAL, SpawnReason.JOCKEY, SpawnReason.CHUNK_GEN, SpawnReason.SPAWNER,
        SpawnReason.TRIAL_SPAWNER, SpawnReason.LIGHTNING, SpawnReason.VILLAGE_DEFENSE,
        SpawnReason.VILLAGE_INVASION, SpawnReason.BREEDING, SpawnReason.SLIME_SPLIT,
        SpawnReason.REINFORCEMENTS, SpawnReason.NETHER_PORTAL, SpawnReason.DISPENSE_EGG,
        SpawnReason.INFECTION, SpawnReason.OCELOT_BABY, SpawnReason.SILVERFISH_BLOCK,
        SpawnReason.MOUNT, SpawnReason.TRAP, SpawnReason.DROWNED, SpawnReason.EXPLOSION,
        SpawnReason.RAID, SpawnReason.PATROL, SpawnReason.SPELL, SpawnReason.FROZEN,
        SpawnReason.METAMORPHOSIS, SpawnReason.DUPLICATION, SpawnReason.ENCHANTMENT,
        SpawnReason.OMINOUS_ITEM_SPAWNER, SpawnReason.POTION_EFFECT, SpawnReason.REANIMATE,
        SpawnReason.REHYDRATION);
    private final ProtectionPolicy policy;

    public EnvironmentListener(ProtectionPolicy policy) { this.policy = policy; }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (policy.protects(event.getLocation().getWorld()) && policy.settings().preventNaturalSpawns()
            && AUTOMATIC_SPAWNS.contains(event.getSpawnReason())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplosion(EntityExplodeEvent event) {
        if (policy.blocks(EnvironmentRule.EXPLOSIONS, event.getLocation().getWorld())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplosion(BlockExplodeEvent event) {
        if (policy.blocks(EnvironmentRule.EXPLOSIONS, event.getBlock().getWorld())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        if (policy.blocks(EnvironmentRule.FIRE, event.getBlock().getWorld())
            && !policy.bypasses(event.getPlayer(), Action.ITEM_USE)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (policy.blocks(EnvironmentRule.FIRE, event.getBlock().getWorld())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        if (policy.blocks(EnvironmentRule.FLUID_FLOW, event.getBlock().getWorld())
            || policy.blocks(EnvironmentRule.FLUID_FLOW, event.getToBlock().getWorld())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGrow(BlockGrowEvent event) {
        if (policy.blocks(EnvironmentRule.BLOCK_GROWTH, event.getBlock().getWorld())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFertilize(BlockFertilizeEvent event) {
        if (policy.blocks(EnvironmentRule.BLOCK_GROWTH, event.getBlock().getWorld())
            && !policy.bypasses(event.getPlayer(), Action.ITEM_USE)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onStructureGrow(StructureGrowEvent event) {
        if (policy.blocks(EnvironmentRule.BLOCK_GROWTH, event.getWorld())
            && !policy.bypasses(event.getPlayer(), Action.ITEM_USE)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent event) {
        if (policy.blocks(EnvironmentRule.BLOCK_SPREAD, event.getBlock().getWorld())
            || (event.getNewState().getType() == org.bukkit.Material.FIRE
                && policy.blocks(EnvironmentRule.FIRE, event.getBlock().getWorld()))) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLeavesDecay(LeavesDecayEvent event) {
        if (policy.blocks(EnvironmentRule.LEAF_DECAY, event.getBlock().getWorld())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onForm(BlockFormEvent event) {
        if (event instanceof EntityBlockFormEvent byEntity && byEntity.getEntity() instanceof Player player
            && policy.bypasses(player, Action.BLOCK_PLACE)) return;
        if (policy.blocks(EnvironmentRule.BLOCK_FORM, event.getBlock().getWorld())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFade(BlockFadeEvent event) {
        if (policy.blocks(EnvironmentRule.BLOCK_FORM, event.getBlock().getWorld())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityBlockChange(EntityChangeBlockEvent event) {
        if (event.getEntity() instanceof Player) return;
        if (policy.blocks(EnvironmentRule.MOB_GRIEF, event.getBlock().getWorld())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityInteract(EntityInteractEvent event) {
        if (event.getEntity() instanceof Player) return;
        if (policy.blocks(EnvironmentRule.MOB_GRIEF, event.getBlock().getWorld())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDoorBreak(EntityBreakDoorEvent event) {
        if (policy.blocks(EnvironmentRule.MOB_GRIEF, event.getBlock().getWorld())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (policy.blocks(EnvironmentRule.PISTONS, event.getBlock().getWorld())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (policy.blocks(EnvironmentRule.PISTONS, event.getBlock().getWorld())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRedstone(BlockRedstoneEvent event) {
        if (policy.blocks(EnvironmentRule.REDSTONE, event.getBlock().getWorld())) event.setNewCurrent(event.getOldCurrent());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakEvent event) {
        World world = event.getEntity().getWorld();
        // Explosions remain a world rule even when a player lit the TNT.
        if (event.getCause() == HangingBreakEvent.RemoveCause.EXPLOSION) {
            if (policy.blocks(EnvironmentRule.EXPLOSIONS, world)) event.setCancelled(true);
            return;
        }
        if (event instanceof HangingBreakByEntityEvent byEntity && playerCaused(byEntity)) return;
        if (policy.blocks(EnvironmentRule.MOB_GRIEF, world)) event.setCancelled(true);
    }

    private boolean playerCaused(HangingBreakByEntityEvent event) {
        if (playerSource(event.getRemover())) return true;
        DamageSource source = event.getDamageSource();
        return source != null && (playerSource(source.getCausingEntity()) || playerSource(source.getDirectEntity()));
    }

    private boolean playerSource(Entity entity) {
        return entity instanceof Player
            || (entity instanceof Projectile projectile && projectile.getShooter() instanceof Player);
    }
}
