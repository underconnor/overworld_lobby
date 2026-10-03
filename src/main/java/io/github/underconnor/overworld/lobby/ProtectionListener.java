package io.github.underconnor.overworld.lobby;

import com.destroystokyo.paper.event.player.PlayerPickupExperienceEvent;
import io.papermc.paper.event.player.PlayerInsertLecternBookEvent;
import io.papermc.paper.event.player.PlayerItemFrameChangeEvent;
import io.papermc.paper.event.player.PlayerOpenSignEvent;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.Event.Result;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExhaustionEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketEntityEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerPickupArrowEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.event.player.PlayerTakeLecternBookEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerUnleashEntityEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.inventory.ItemStack;

/** Player-facing protections. Permission checks are delegated to Bukkit/LuckPerms. */
public final class ProtectionListener implements Listener {
    private final ProtectionPolicy policy;
    private final DenialMessages messages;

    public ProtectionListener(ProtectionPolicy policy) {
        this(policy, null);
    }

    public ProtectionListener(ProtectionPolicy policy, DenialMessages messages) {
        this.policy = policy;
        this.messages = messages;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (policy.blocks(Action.BLOCK_BREAK, event.getPlayer(), event.getBlock().getWorld())) {
            deny(event, event.getPlayer(), Action.BLOCK_BREAK);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent event) {
        if (policy.blocks(Action.BLOCK_BREAK, event.getPlayer(), event.getBlock().getWorld())) {
            deny(event, event.getPlayer(), Action.BLOCK_BREAK);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (policy.blocks(Action.BLOCK_PLACE, event.getPlayer(), event.getBlock().getWorld())) {
            deny(event, event.getPlayer(), Action.BLOCK_PLACE);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEmptyBucket(PlayerBucketEmptyEvent event) {
        if (policy.blocks(Action.BUCKETS, event.getPlayer(), event.getBlock().getWorld())) {
            deny(event, event.getPlayer(), Action.BUCKETS);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFillBucket(PlayerBucketFillEvent event) {
        if (policy.blocks(Action.BUCKETS, event.getPlayer(), event.getBlock().getWorld())) {
            deny(event, event.getPlayer(), Action.BUCKETS);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketEntity(PlayerBucketEntityEvent event) {
        if (policy.blocks(Action.BUCKETS, event.getPlayer(), event.getEntity().getWorld())) {
            deny(event, event.getPlayer(), Action.BUCKETS);
        }
    }

    // Vanilla can mark an air interaction cancelled before dispatch. Process both
    // result channels independently, and never force ALLOW over another plugin.
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        World world = block == null ? player.getWorld() : block.getWorld();
        if (!policy.protects(world)) return;

        // Digging passes through this event before BlockBreakEvent. A builder's
        // block-break permission must also allow the initial click/tool channel.
        if (event.getAction() == org.bukkit.event.block.Action.LEFT_CLICK_BLOCK) {
            if (policy.blocks(Action.BLOCK_BREAK, player, world)) {
                boolean changed = event.useInteractedBlock() != Result.DENY || event.useItemInHand() != Result.DENY;
                event.setUseInteractedBlock(Result.DENY);
                event.setUseItemInHand(Result.DENY);
                if (changed) feedback(player, Action.BLOCK_BREAK);
            }
            return;
        }

        Action message = null;
        boolean openingContainer = block != null && event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK
            && policy.settings().allowedContainers().contains(block.getType());
        boolean containerAllowed = openingContainer && !policy.blocks(Action.CONTAINERS, player, world)
            && (!player.isSneaking() || event.getItem() == null || event.getItem().getType().isAir());
        if (block != null) {
            Action action = openingContainer ? Action.CONTAINERS : Action.INTERACT;
            if (policy.blocks(action, player, world)) {
                if (event.useInteractedBlock() != Result.DENY) message = action;
                event.setUseInteractedBlock(Result.DENY);
            }
        }

        Action itemAction = itemAction(event.getItem());
        if (policy.blocks(itemAction, player, world)) {
            boolean changed = event.useItemInHand() != Result.DENY;
            event.setUseItemInHand(Result.DENY);
            // Opening an allowed chest succeeds even while a held item's own use is blocked.
            if (message == null && changed && !containerAllowed && event.getItem() != null
                && !event.getItem().getType().isAir()
                && (event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK
                    || event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_AIR)) message = itemAction;
        }
        if (message != null && event.getAction() != org.bukkit.event.block.Action.PHYSICAL) feedback(player, message);
    }

    private Action itemAction(ItemStack item) {
        if (item == null) return Action.ITEM_USE;
        Material material = item.getType();
        String name = material.name();
        if (name.endsWith("BUCKET")) return Action.BUCKETS;
        if (name.endsWith("_BOAT") || name.endsWith("_RAFT") || name.endsWith("MINECART")) {
            return Action.VEHICLES;
        }
        if (material.isBlock() || name.endsWith("_SPAWN_EGG") || material == Material.ARMOR_STAND
            || material == Material.ITEM_FRAME || material == Material.GLOW_ITEM_FRAME
            || material == Material.PAINTING || material == Material.END_CRYSTAL) {
            return Action.BLOCK_PLACE;
        }
        return Action.ITEM_USE;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        if (policy.blocks(Action.ENTITY_INTERACT, event.getPlayer(), event.getRightClicked().getWorld())) {
            deny(event, event.getPlayer(), Action.ENTITY_INTERACT);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityInteractAt(PlayerInteractAtEntityEvent event) {
        if (policy.blocks(Action.ENTITY_INTERACT, event.getPlayer(), event.getRightClicked().getWorld())) {
            deny(event, event.getPlayer(), Action.ENTITY_INTERACT);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        if (policy.blocks(Action.ENTITY_INTERACT, event.getPlayer(), event.getRightClicked().getWorld())) {
            deny(event, event.getPlayer(), Action.ENTITY_INTERACT);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemFrame(PlayerItemFrameChangeEvent event) {
        if (policy.blocks(Action.ENTITY_INTERACT, event.getPlayer(), event.getItemFrame().getWorld())) {
            deny(event, event.getPlayer(), Action.ENTITY_INTERACT);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakEvent event) {
        Player player = event instanceof HangingBreakByEntityEvent byEntity
            ? responsible(byEntity.getDamageSource(), byEntity.getRemover()) : null;
        if (policy.blocks(Action.ENTITY_DAMAGE, player, event.getEntity().getWorld())) {
            deny(event, player, Action.ENTITY_DAMAGE);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        if (policy.blocks(Action.BLOCK_PLACE, event.getPlayer(), event.getEntity().getWorld())) {
            deny(event, event.getPlayer(), Action.BLOCK_PLACE);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityPlace(EntityPlaceEvent event) {
        // Null actors indicate programmatic/dispenser placement, not a player action.
        if (event.getPlayer() == null) return;
        Action action = event.getEntity() instanceof Vehicle ? Action.VEHICLES : Action.BLOCK_PLACE;
        if (policy.blocks(action, event.getPlayer(), event.getEntity().getWorld())) {
            deny(event, event.getPlayer(), action);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        Entity victim = event.getEntity();
        Entity direct = event instanceof EntityDamageByEntityEvent byEntity ? byEntity.getDamager() : null;
        Player attacker = responsible(event.getDamageSource(), direct);
        if (victim instanceof Player player) {
            // PvP is controlled by the attacking player's permission. The
            // player's own damage permission controls environmental/self harm.
            Action action = attacker != null && attacker != player ? Action.PVP : Action.PLAYER_DAMAGE;
            Player actor = action == Action.PVP ? attacker : player;
            if (policy.blocks(action, actor, victim.getWorld())) {
                deny(event, actor, action);
            }
        } else if (policy.blocks(Action.ENTITY_DAMAGE, attacker, victim.getWorld())) {
            deny(event, attacker, Action.ENTITY_DAMAGE);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player
            && policy.blocks(Action.HUNGER, player, player.getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExhaustion(EntityExhaustionEvent event) {
        if (event.getEntity() instanceof Player player
            && policy.blocks(Action.HUNGER, player, player.getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (policy.blocks(Action.ITEM_DROP, event.getPlayer(), event.getItemDrop().getWorld())) {
            deny(event, event.getPlayer(), Action.ITEM_DROP);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player
            && policy.blocks(Action.ITEM_PICKUP, player, event.getItem().getWorld())) {
            deny(event, player, Action.ITEM_PICKUP);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickupArrow(PlayerPickupArrowEvent event) {
        if (policy.blocks(Action.ITEM_PICKUP, event.getPlayer(), event.getArrow().getWorld())) {
            deny(event, event.getPlayer(), Action.ITEM_PICKUP);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickupExperience(PlayerPickupExperienceEvent event) {
        if (policy.blocks(Action.ITEM_PICKUP, event.getPlayer(), event.getExperienceOrb().getWorld())) {
            deny(event, event.getPlayer(), Action.ITEM_PICKUP);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProjectile(ProjectileLaunchEvent event) {
        Player player = responsible(event.getEntity());
        if (player != null && policy.blocks(Action.ITEM_USE, player, event.getEntity().getWorld())) {
            deny(event, player, Action.ITEM_USE);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (policy.blocks(Action.ITEM_USE, event.getPlayer(), event.getPlayer().getWorld())) {
            deny(event, event.getPlayer(), Action.ITEM_USE);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (policy.blocks(Action.ITEM_USE, event.getPlayer(), event.getHook().getWorld())) {
            deny(event, event.getPlayer(), Action.ITEM_USE);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onShear(PlayerShearEntityEvent event) {
        if (policy.blocks(Action.ENTITY_INTERACT, event.getPlayer(), event.getEntity().getWorld())) {
            deny(event, event.getPlayer(), Action.ENTITY_INTERACT);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLeash(PlayerLeashEntityEvent event) {
        if (policy.blocks(Action.ENTITY_INTERACT, event.getPlayer(), event.getEntity().getWorld())) {
            deny(event, event.getPlayer(), Action.ENTITY_INTERACT);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onUnleash(PlayerUnleashEntityEvent event) {
        if (policy.blocks(Action.ENTITY_INTERACT, event.getPlayer(), event.getEntity().getWorld())) {
            deny(event, event.getPlayer(), Action.ENTITY_INTERACT);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTame(EntityTameEvent event) {
        if (event.getOwner() instanceof Player player
            && policy.blocks(Action.ENTITY_INTERACT, player, event.getEntity().getWorld())) {
            deny(event, player, Action.ENTITY_INTERACT);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHarvest(PlayerHarvestBlockEvent event) {
        if (policy.blocks(Action.INTERACT, event.getPlayer(), event.getHarvestedBlock().getWorld())) {
            deny(event, event.getPlayer(), Action.INTERACT);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBed(PlayerBedEnterEvent event) {
        if (policy.blocks(Action.INTERACT, event.getPlayer(), event.getBed().getWorld())) {
            deny(event, event.getPlayer(), Action.INTERACT);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSignOpen(PlayerOpenSignEvent event) {
        if (policy.blocks(Action.INTERACT, event.getPlayer(), event.getSign().getWorld())) {
            deny(event, event.getPlayer(), Action.INTERACT);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        if (policy.blocks(Action.INTERACT, event.getPlayer(), event.getBlock().getWorld())) {
            deny(event, event.getPlayer(), Action.INTERACT);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLecternTake(PlayerTakeLecternBookEvent event) {
        if (policy.blocks(Action.INTERACT, event.getPlayer(), event.getLectern().getWorld())) {
            deny(event, event.getPlayer(), Action.INTERACT);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLecternInsert(PlayerInsertLecternBookEvent event) {
        if (policy.blocks(Action.INTERACT, event.getPlayer(), event.getBlock().getWorld())) {
            deny(event, event.getPlayer(), Action.INTERACT);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVehicleEnter(VehicleEnterEvent event) {
        if (event.getEntered() instanceof Player player
            && policy.blocks(Action.VEHICLES, player, event.getVehicle().getWorld())) {
            deny(event, player, Action.VEHICLES);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVehicleDamage(VehicleDamageEvent event) {
        if (policy.blocks(Action.ENTITY_DAMAGE, responsible(event.getDamageSource(), event.getAttacker()),
            event.getVehicle().getWorld())) {
            deny(event, responsible(event.getDamageSource(), event.getAttacker()), Action.ENTITY_DAMAGE);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        if (policy.blocks(Action.ENTITY_DAMAGE, responsible(event.getDamageSource(), event.getAttacker()),
            event.getVehicle().getWorld())) {
            deny(event, responsible(event.getDamageSource(), event.getAttacker()), Action.ENTITY_DAMAGE);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        if (blockedPortal(event)) {
            deny(event, event.getPlayer(), Action.PORTALS);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        // End gateways use the teleport handler list. Plugin/command teleports
        // remain available for server selectors and the Passport travel bridge.
        switch (event.getCause()) {
            case NETHER_PORTAL, END_PORTAL, END_GATEWAY -> {
                if (blockedPortal(event)) {
                    deny(event, event.getPlayer(), Action.PORTALS);
                }
            }
            default -> { }
        }
    }

    private boolean blockedPortal(PlayerTeleportEvent event) {
        return policy.blocks(Action.PORTALS, event.getPlayer(), event.getFrom().getWorld())
            || (event.getTo() != null
                && policy.blocks(Action.PORTALS, event.getPlayer(), event.getTo().getWorld()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPotionSplash(PotionSplashEvent event) {
        Player source = responsible(event.getEntity());
        if (source == null) return;
        if (policy.blocks(Action.ITEM_USE, source, event.getEntity().getWorld())) {
            deny(event, source, Action.ITEM_USE);
            return;
        }
        boolean restrictedPvp = false;
        for (var target : event.getAffectedEntities()) {
            if (target instanceof Player player && player != source
                && policy.blocks(Action.PVP, source, target.getWorld())) {
                restrictedPvp |= event.getIntensity(target) > 0;
                event.setIntensity(target, 0);
            }
        }
        if (restrictedPvp && !event.isCancelled()) feedback(source, Action.PVP);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPotionCloud(AreaEffectCloudApplyEvent event) {
        Player source = responsible(event.getEntity());
        if (source == null) return;
        if (policy.blocks(Action.ITEM_USE, source, event.getEntity().getWorld())) {
            event.setCancelled(true);
            return;
        }
        event.getAffectedEntities().removeIf(target -> target instanceof Player player
            && player != source && policy.blocks(Action.PVP, source, target.getWorld()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        if (messages != null) messages.clear(event.getPlayer().getUniqueId());
    }

    private void deny(Cancellable event, Player actor, Action action) {
        boolean alreadyCancelled = event.isCancelled();
        event.setCancelled(true);
        if (!alreadyCancelled) feedback(actor, action);
    }

    private void feedback(Player actor, Action action) {
        if (messages != null && actor != null && action != Action.PLAYER_DAMAGE && action != Action.HUNGER)
            messages.send(actor, action);
    }

    private Player responsible(DamageSource source, Entity fallback) {
        Player player = source == null ? null : responsible(source.getCausingEntity());
        if (player == null && source != null) player = responsible(source.getDirectEntity());
        return player == null ? responsible(fallback) : player;
    }

    private Player responsible(Entity entity) {
        Set<Entity> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        while (entity != null && seen.add(entity)) {
            if (entity instanceof Player player) return player;
            if (entity instanceof Projectile projectile) {
                entity = projectile.getShooter() instanceof Entity shooter ? shooter : null;
            } else if (entity instanceof TNTPrimed tnt) {
                entity = tnt.getSource();
            } else if (entity instanceof AreaEffectCloud cloud) {
                entity = cloud.getSource() instanceof Entity owner ? owner : null;
            } else if (entity instanceof Tameable tameable) {
                return tameable.getOwner() instanceof Player owner ? owner : null;
            } else {
                return null;
            }
        }
        return null;
    }
}
