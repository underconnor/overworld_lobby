package io.github.underconnor.overworld.lobby;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Set;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.Event.Result;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExhaustionEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerPickupArrowEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class ProtectionListenerTest {
    private World lobby;
    private Player player;
    private Settings settings;
    private ProtectionListener listener;

    @BeforeEach void setUp() {
        lobby = mock(World.class);
        when(lobby.getName()).thenReturn("lobby");
        player = mock(Player.class);
        when(player.getWorld()).thenReturn(lobby);
        settings = settings(Set.of("lobby"), protections());
        listener = new ProtectionListener(new ProtectionPolicy(() -> settings));
    }

    private EnumSet<Action> protections() {
        var actions = EnumSet.allOf(Action.class);
        actions.remove(Action.CONTAINERS);
        return actions;
    }

    private Settings settings(Set<String> worlds, Set<Action> protections) {
        return new Settings(true, worlds, GameMode.SURVIVAL, true, 0.1f, true,
            new Settings.TimeSettings(true, 6000),
            new Settings.WeatherSettings(true, Settings.WeatherKind.CLEAR),
            new Settings.SpawnSettings(true, "", -4.5, 63.5, -1.5, 180f, 0f, true, true, true), true,
            protections, EnumSet.allOf(EnvironmentRule.class),
            Set.of(Material.CHEST, Material.TRAPPED_CHEST, Material.ENDER_CHEST,
                Material.BARREL, Material.SHULKER_BOX, Material.BLACK_SHULKER_BOX));
    }

    private Block block(Material material) {
        Block block = mock(Block.class);
        when(block.getWorld()).thenReturn(lobby);
        when(block.getType()).thenReturn(material);
        return block;
    }

    private PlayerInteractEvent interaction(org.bukkit.event.block.Action action,
                                           Material material, EquipmentSlot hand, ItemStack item) {
        return new PlayerInteractEvent(player, action, item,
            material == null ? null : block(material), BlockFace.UP, hand);
    }

    @ParameterizedTest @EnumSource(value = EquipmentSlot.class, names = {"HAND", "OFF_HAND"})
    void chestOpeningKeepsBlockUseAndDeniesHeldItemsInBothHands(EquipmentSlot hand) {
        var event = interaction(org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK, Material.CHEST, hand, null);
        Result before = event.useInteractedBlock();
        listener.onInteract(event);
        assertEquals(before, event.useInteractedBlock());
        assertNotEquals(Result.DENY, event.useInteractedBlock());
        assertEquals(Result.DENY, event.useItemInHand());
        assertFalse(event.isCancelled());
    }

    @ParameterizedTest @EnumSource(value = Material.class, names = {
        "TRAPPED_CHEST", "ENDER_CHEST", "BARREL", "SHULKER_BOX", "BLACK_SHULKER_BOX"
    }) void storageVariantsRemainAccessible(Material material) {
        var event = interaction(org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK, material, EquipmentSlot.HAND, null);
        listener.onInteract(event);
        assertNotEquals(Result.DENY, event.useInteractedBlock());
    }

    @ParameterizedTest @CsvSource({
        "RIGHT_CLICK_BLOCK, OAK_DOOR", "RIGHT_CLICK_BLOCK, STONE_BUTTON",
        "RIGHT_CLICK_BLOCK, LEVER", "RIGHT_CLICK_BLOCK, CRAFTING_TABLE",
        "RIGHT_CLICK_BLOCK, RED_BED", "PHYSICAL, FARMLAND", "PHYSICAL, STONE_PRESSURE_PLATE",
        "LEFT_CLICK_BLOCK, CHEST"
    }) void ordinaryBlockInteractionsAndTramplingAreDenied(org.bukkit.event.block.Action action, Material material) {
        var event = interaction(action, material, EquipmentSlot.OFF_HAND, null);
        listener.onInteract(event);
        assertEquals(Result.DENY, event.useInteractedBlock());
    }

    @Test void configuredContainerRestrictionAndPermissionAreIndependentFromDoorPermission() {
        var actions = protections();
        actions.add(Action.CONTAINERS);
        settings = settings(Set.of("lobby"), actions);
        var denied = interaction(org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK, Material.CHEST, EquipmentSlot.HAND, null);
        listener.onInteract(denied);
        assertEquals(Result.DENY, denied.useInteractedBlock());

        when(player.hasPermission(Action.CONTAINERS.permission())).thenReturn(true);
        var allowed = interaction(org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK, Material.CHEST, EquipmentSlot.HAND, null);
        listener.onInteract(allowed);
        assertNotEquals(Result.DENY, allowed.useInteractedBlock());
        var door = interaction(org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK, Material.OAK_DOOR, EquipmentSlot.HAND, null);
        listener.onInteract(door);
        assertEquals(Result.DENY, door.useInteractedBlock());
    }

    @Test void permissionsNeverUncancelAnotherPluginsContainerOrItemDenial() {
        when(player.hasPermission("overworld.lobby.bypass")).thenReturn(true);
        var event = interaction(org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK, Material.CHEST, EquipmentSlot.HAND, null);
        event.setUseInteractedBlock(Result.DENY);
        event.setUseItemInHand(Result.DENY);
        listener.onInteract(event);
        assertEquals(Result.DENY, event.useInteractedBlock());
        assertEquals(Result.DENY, event.useItemInHand());
    }

    @Test void airInteractionsAlreadyMarkedCancelledStillDenyItemUse() {
        var event = interaction(org.bukkit.event.block.Action.RIGHT_CLICK_AIR, null, EquipmentSlot.OFF_HAND, null);
        assertTrue(event.isCancelled());
        listener.onInteract(event);
        assertEquals(Result.DENY, event.useItemInHand());
    }

    @Test void blockPlacePermissionAllowsBuildingWithoutGrantingGenericItemUse() {
        // Material.isBlock() normally consults the running server's registry.
        // Stub this one property to keep the event test independent of a server.
        Material buildingMaterial = mock(Material.class);
        when(buildingMaterial.name()).thenReturn("STONE");
        when(buildingMaterial.isBlock()).thenReturn(true);
        ItemStack stone = mock(ItemStack.class);
        when(stone.getType()).thenReturn(buildingMaterial);
        when(player.hasPermission(Action.BLOCK_PLACE.permission())).thenReturn(true);
        var event = interaction(org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK, Material.STONE, EquipmentSlot.HAND, stone);
        listener.onInteract(event);
        assertNotEquals(Result.DENY, event.useItemInHand());
        assertEquals(Result.DENY, event.useInteractedBlock());
        verify(player, never()).hasPermission(Action.ITEM_USE.permission());
    }

    @Test void blockBreakPermissionAllowsInitialDiggingDespiteInteractionAndItemProtections() {
        when(player.hasPermission(Action.BLOCK_BREAK.permission())).thenReturn(true);
        var event = interaction(org.bukkit.event.block.Action.LEFT_CLICK_BLOCK, Material.STONE, EquipmentSlot.HAND, null);
        listener.onInteract(event);
        assertNotEquals(Result.DENY, event.useInteractedBlock());
        assertNotEquals(Result.DENY, event.useItemInHand());
        verify(player, never()).hasPermission(Action.INTERACT.permission());
        verify(player, never()).hasPermission(Action.ITEM_USE.permission());
    }

    @Test void bucketPermissionAllowsUseAndFillEmptyWithoutGenericItemPermission() {
        ItemStack bucket = mock(ItemStack.class);
        when(bucket.getType()).thenReturn(Material.WATER_BUCKET);
        when(player.hasPermission(Action.BUCKETS.permission())).thenReturn(true);
        var use = interaction(org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK, Material.STONE, EquipmentSlot.HAND, bucket);
        listener.onInteract(use);
        assertNotEquals(Result.DENY, use.useItemInHand());
        PlayerBucketEmptyEvent empty = mock(PlayerBucketEmptyEvent.class);
        Block emptyTarget = block(Material.STONE);
        when(empty.getPlayer()).thenReturn(player);
        when(empty.getBlock()).thenReturn(emptyTarget);
        listener.onEmptyBucket(empty);
        verify(empty, never()).setCancelled(anyBoolean());
        PlayerBucketFillEvent fill = mock(PlayerBucketFillEvent.class);
        Block fillTarget = block(Material.WATER);
        when(fill.getPlayer()).thenReturn(player);
        when(fill.getBlock()).thenReturn(fillTarget);
        listener.onFillBucket(fill);
        verify(fill, never()).setCancelled(anyBoolean());
        when(player.hasPermission(Action.BUCKETS.permission())).thenReturn(false);
        listener.onFillBucket(fill);
        verify(fill).setCancelled(true);
    }

    @Test void breakingAndPlacingRespectSeparateLivePermissions() {
        var broken = new BlockBreakEvent(block(Material.STONE), player);
        listener.onBreak(broken);
        assertTrue(broken.isCancelled());
        when(player.hasPermission(Action.BLOCK_BREAK.permission())).thenReturn(true);
        var allowed = new BlockBreakEvent(block(Material.STONE), player);
        listener.onBreak(allowed);
        assertFalse(allowed.isCancelled());
        BlockPlaceEvent placed = mock(BlockPlaceEvent.class);
        Block placeTarget = block(Material.STONE);
        when(placed.getPlayer()).thenReturn(player);
        when(placed.getBlock()).thenReturn(placeTarget);
        listener.onPlace(placed);
        verify(placed).setCancelled(true);
    }

    @Test void excludedWorldAndDisabledActionPassThrough() {
        World survival = mock(World.class);
        when(survival.getName()).thenReturn("survival");
        Block outside = mock(Block.class);
        when(outside.getWorld()).thenReturn(survival);
        var breakOutside = new BlockBreakEvent(outside, player);
        listener.onBreak(breakOutside);
        assertFalse(breakOutside.isCancelled());
        var actions = protections();
        actions.remove(Action.BLOCK_BREAK);
        settings = settings(Set.of("lobby"), actions);
        var breakInside = new BlockBreakEvent(block(Material.STONE), player);
        listener.onBreak(breakInside);
        assertFalse(breakInside.isCancelled());
    }

    @Test void entityInteractionPermissionDoesNotRequireItemUsePermission() {
        Entity target = mock(Entity.class);
        when(target.getWorld()).thenReturn(lobby);
        var event = new PlayerInteractEntityEvent(player, target, EquipmentSlot.OFF_HAND);
        listener.onEntityInteract(event);
        assertTrue(event.isCancelled());
        when(player.hasPermission(Action.ENTITY_INTERACT.permission())).thenReturn(true);
        var permitted = new PlayerInteractEntityEvent(player, target, EquipmentSlot.OFF_HAND);
        listener.onEntityInteract(permitted);
        assertFalse(permitted.isCancelled());
        verify(player, never()).hasPermission(Action.ITEM_USE.permission());
    }

    private EntityDamageByEntityEvent hit(Entity attacker, Entity target) {
        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        when(event.getEntity()).thenReturn(target);
        when(event.getDamager()).thenReturn(attacker);
        return event;
    }

    @Test void projectileDamageUsesShootersEntityDamagePermission() {
        Arrow arrow = mock(Arrow.class);
        when(arrow.getShooter()).thenReturn(player);
        Entity target = mock(Entity.class);
        when(target.getWorld()).thenReturn(lobby);
        var blocked = hit(arrow, target);
        listener.onDamage(blocked);
        verify(blocked).setCancelled(true);
        when(player.hasPermission(Action.ENTITY_DAMAGE.permission())).thenReturn(true);
        var allowed = hit(arrow, target);
        listener.onDamage(allowed);
        verify(allowed, never()).setCancelled(anyBoolean());
    }

    @Test void tntChainsAreAttributedAndCyclesCannotLoop() {
        TNTPrimed first = mock(TNTPrimed.class), second = mock(TNTPrimed.class);
        when(first.getSource()).thenReturn(second);
        when(second.getSource()).thenReturn(player);
        Entity target = mock(Entity.class);
        when(target.getWorld()).thenReturn(lobby);
        when(player.hasPermission(Action.ENTITY_DAMAGE.permission())).thenReturn(true);
        var permitted = hit(first, target);
        listener.onDamage(permitted);
        verify(permitted, never()).setCancelled(anyBoolean());
        when(second.getSource()).thenReturn(first);
        var cycle = hit(first, target);
        listener.onDamage(cycle);
        verify(cycle).setCancelled(true);
    }

    @Test void causingEntityFromDamageSourceProvidesAttribution() {
        Entity target = mock(Entity.class);
        when(target.getWorld()).thenReturn(lobby);
        DamageSource source = mock(DamageSource.class);
        when(source.getCausingEntity()).thenReturn(player);
        EntityDamageEvent event = mock(EntityDamageEvent.class);
        when(event.getDamageSource()).thenReturn(source);
        when(event.getEntity()).thenReturn(target);
        when(player.hasPermission(Action.ENTITY_DAMAGE.permission())).thenReturn(true);
        listener.onDamage(event);
        verify(event, never()).setCancelled(anyBoolean());
    }

    @Test void pvpPermissionAllowsAttackerDespiteVictimsEnvironmentalDamageProtection() {
        Player victim = mock(Player.class);
        when(victim.getWorld()).thenReturn(lobby);
        Arrow arrow = mock(Arrow.class);
        when(arrow.getShooter()).thenReturn(player);
        var denied = hit(arrow, victim);
        listener.onDamage(denied);
        verify(denied).setCancelled(true);
        when(player.hasPermission(Action.PVP.permission())).thenReturn(true);
        var permitted = hit(arrow, victim);
        listener.onDamage(permitted);
        verify(permitted, never()).setCancelled(anyBoolean());
        verify(victim, never()).hasPermission(Action.PLAYER_DAMAGE.permission());
    }

    @Test void environmentalDamageAndPvpPermissionsRemainSeparate() {
        EntityDamageEvent falling = mock(EntityDamageEvent.class);
        when(falling.getEntity()).thenReturn(player);
        listener.onDamage(falling);
        verify(falling).setCancelled(true);
        when(player.hasPermission(Action.PLAYER_DAMAGE.permission())).thenReturn(true);
        EntityDamageEvent allowedFall = mock(EntityDamageEvent.class);
        when(allowedFall.getEntity()).thenReturn(player);
        listener.onDamage(allowedFall);
        verify(allowedFall, never()).setCancelled(anyBoolean());
        Player victim = mock(Player.class);
        when(victim.getWorld()).thenReturn(lobby);
        var attack = hit(player, victim);
        listener.onDamage(attack);
        verify(attack).setCancelled(true);
    }

    @Test void hangingRemovalUsesDamageSourcePermissions() {
        var event = mock(HangingBreakByEntityEvent.class);
        var hanging = mock(org.bukkit.entity.Hanging.class);
        when(hanging.getWorld()).thenReturn(lobby);
        when(event.getEntity()).thenReturn(hanging);
        when(event.getRemover()).thenReturn(player);
        listener.onHangingBreak(event);
        verify(event).setCancelled(true);
    }

    @Test void playerInventoryDropsAndPickupsAreBlockedButMobPickupIsUnaffected() {
        Item item = mock(Item.class);
        when(item.getWorld()).thenReturn(lobby);
        var drop = new PlayerDropItemEvent(player, item);
        listener.onDrop(drop);
        assertTrue(drop.isCancelled());
        var pickup = new EntityPickupItemEvent(player, item, 0);
        listener.onPickup(pickup);
        assertTrue(pickup.isCancelled());
        var mobPickup = new EntityPickupItemEvent(mock(LivingEntity.class), item, 0);
        listener.onPickup(mobPickup);
        assertFalse(mobPickup.isCancelled());
    }

    @Test void projectileLaunchAndConsumptionRespectItemUsePermission() {
        Arrow arrow = mock(Arrow.class);
        when(arrow.getWorld()).thenReturn(lobby);
        when(arrow.getShooter()).thenReturn(player);
        var projectile = new ProjectileLaunchEvent(arrow);
        listener.onProjectile(projectile);
        assertTrue(projectile.isCancelled());
        PlayerItemConsumeEvent consume = mock(PlayerItemConsumeEvent.class);
        when(consume.getPlayer()).thenReturn(player);
        listener.onConsume(consume);
        verify(consume).setCancelled(true);
        when(player.hasPermission(Action.ITEM_USE.permission())).thenReturn(true);
        var permittedProjectile = new ProjectileLaunchEvent(arrow);
        listener.onProjectile(permittedProjectile);
        assertFalse(permittedProjectile.isCancelled());
    }

    @Test void dispenserProjectileIsNotMistakenForPlayerItemUse() {
        Arrow arrow = mock(Arrow.class);
        when(arrow.getWorld()).thenReturn(lobby);
        when(arrow.getShooter()).thenReturn(mock(org.bukkit.projectiles.BlockProjectileSource.class));
        var projectile = new ProjectileLaunchEvent(arrow);
        listener.onProjectile(projectile);
        assertFalse(projectile.isCancelled());
    }

    @Test void arrowPickupUsesItsOwnEventAndRespectsPickupPermission() {
        Arrow arrow = mock(Arrow.class);
        when(arrow.getWorld()).thenReturn(lobby);
        var blocked = new PlayerPickupArrowEvent(player, mock(Item.class), arrow);
        listener.onPickupArrow(blocked);
        assertTrue(blocked.isCancelled());
        when(player.hasPermission(Action.ITEM_PICKUP.permission())).thenReturn(true);
        var permitted = new PlayerPickupArrowEvent(player, mock(Item.class), arrow);
        listener.onPickupArrow(permitted);
        assertFalse(permitted.isCancelled());
    }

    @Test void hungerHasIndependentBypass() {
        var hunger = new FoodLevelChangeEvent(player, 19, null);
        listener.onHunger(hunger);
        assertTrue(hunger.isCancelled());
        when(player.hasPermission(Action.HUNGER.permission())).thenReturn(true);
        var allowed = new FoodLevelChangeEvent(player, 19, null);
        listener.onHunger(allowed);
        assertFalse(allowed.isCancelled());
    }

    @Test void exhaustionCannotDrainSaturationAndUsesHungerPermissionIndependently() {
        var exhaustion = new EntityExhaustionEvent(player,
            EntityExhaustionEvent.ExhaustionReason.SPRINT, 0.1f);
        listener.onExhaustion(exhaustion);
        assertTrue(exhaustion.isCancelled());
        when(player.hasPermission(Action.PLAYER_DAMAGE.permission())).thenReturn(true);
        var damageBypass = new EntityExhaustionEvent(player,
            EntityExhaustionEvent.ExhaustionReason.JUMP, 0.05f);
        listener.onExhaustion(damageBypass);
        assertTrue(damageBypass.isCancelled());
        when(player.hasPermission(Action.HUNGER.permission())).thenReturn(true);
        var allowed = new EntityExhaustionEvent(player,
            EntityExhaustionEvent.ExhaustionReason.SPRINT, 0.1f);
        listener.onExhaustion(allowed);
        assertFalse(allowed.isCancelled());
    }

    @Test void vehicleCreationOnlyRestrictsAttributablePlayerPlacement() {
        Vehicle vehicle = mock(Vehicle.class);
        when(vehicle.getWorld()).thenReturn(lobby);
        var placed = new EntityPlaceEvent(vehicle, player, block(Material.STONE), BlockFace.UP, EquipmentSlot.HAND);
        listener.onEntityPlace(placed);
        assertTrue(placed.isCancelled());
        when(player.hasPermission(Action.VEHICLES.permission())).thenReturn(true);
        var permitted = new EntityPlaceEvent(vehicle, player, block(Material.STONE), BlockFace.UP, EquipmentSlot.HAND);
        listener.onEntityPlace(permitted);
        assertFalse(permitted.isCancelled());
        var unattributed = new EntityPlaceEvent(vehicle, null, block(Material.STONE), BlockFace.UP, EquipmentSlot.HAND);
        listener.onEntityPlace(unattributed);
        assertFalse(unattributed.isCancelled());
    }

    @ParameterizedTest @EnumSource(value = PlayerTeleportEvent.TeleportCause.class,
        names = {"PLUGIN", "COMMAND", "UNKNOWN"})
    void normalServerTeleportsRemainAvailable(PlayerTeleportEvent.TeleportCause cause) {
        var event = new PlayerTeleportEvent(player, new Location(lobby, 0, 64, 0),
            new Location(lobby, 10, 64, 10), cause);
        listener.onTeleport(event);
        assertFalse(event.isCancelled());
    }

    @Test void gatewaysAndPortalArrivalIntoLobbyAreBlockedWithPermissionOverride() {
        var gateway = new PlayerTeleportEvent(player, new Location(lobby, 0, 64, 0),
            new Location(lobby, 10, 64, 10), PlayerTeleportEvent.TeleportCause.END_GATEWAY);
        listener.onTeleport(gateway);
        assertTrue(gateway.isCancelled());
        World survival = mock(World.class);
        when(survival.getName()).thenReturn("survival");
        var entering = new PlayerPortalEvent(player, new Location(survival, 0, 64, 0),
            new Location(lobby, 0, 64, 0), PlayerTeleportEvent.TeleportCause.NETHER_PORTAL);
        listener.onPortal(entering);
        assertTrue(entering.isCancelled());
        when(player.hasPermission(Action.PORTALS.permission())).thenReturn(true);
        var permitted = new PlayerPortalEvent(player, new Location(survival, 0, 64, 0),
            new Location(lobby, 0, 64, 0), PlayerTeleportEvent.TeleportCause.NETHER_PORTAL);
        listener.onPortal(permitted);
        assertFalse(permitted.isCancelled());
    }

    @Test void pvpAlsoProtectsOtherPlayersFromThrownPotionEffects() {
        Player target = mock(Player.class);
        when(target.getWorld()).thenReturn(lobby);
        var potion = mock(org.bukkit.entity.ThrownPotion.class);
        when(potion.getWorld()).thenReturn(lobby);
        when(potion.getShooter()).thenReturn(player);
        PotionSplashEvent event = mock(PotionSplashEvent.class);
        when(event.getEntity()).thenReturn(potion);
        when(event.getAffectedEntities()).thenReturn(java.util.List.of(player, target));
        when(player.hasPermission(Action.ITEM_USE.permission())).thenReturn(true);
        listener.onPotionSplash(event);
        verify(event).setIntensity(target, 0);
        verify(event, never()).setIntensity(player, 0);
        verify(event, never()).setCancelled(anyBoolean());
    }

    @Test void lingeringCloudPvpFilteringPreservesSourceAndMobTargets() {
        Player target = mock(Player.class);
        when(target.getWorld()).thenReturn(lobby);
        LivingEntity mob = mock(LivingEntity.class);
        var cloud = mock(org.bukkit.entity.AreaEffectCloud.class);
        when(cloud.getWorld()).thenReturn(lobby);
        when(cloud.getSource()).thenReturn(player);
        AreaEffectCloudApplyEvent event = mock(AreaEffectCloudApplyEvent.class);
        when(event.getEntity()).thenReturn(cloud);
        var targets = new ArrayList<LivingEntity>(java.util.List.of(player, target, mob));
        when(event.getAffectedEntities()).thenReturn(targets);
        when(player.hasPermission(Action.ITEM_USE.permission())).thenReturn(true);
        listener.onPotionCloud(event);
        assertEquals(java.util.List.of(player, mob), targets);
    }
}
