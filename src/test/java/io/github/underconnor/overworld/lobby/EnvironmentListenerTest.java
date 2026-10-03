package io.github.underconnor.overworld.lobby;

import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Player;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EnvironmentListenerTest {
    private final YamlConfiguration config = new YamlConfiguration();
    private final World world = mock(World.class);
    private final Block block = mock(Block.class);
    private EnvironmentListener listener;

    @BeforeEach void setup() {
        config.set("worlds", List.of("lobby"));
        when(world.getName()).thenReturn("lobby");
        when(block.getWorld()).thenReturn(world);
        listener = new EnvironmentListener(new ProtectionPolicy(() -> Settings.load(config)));
    }

    @ParameterizedTest
    @EnumSource(value = SpawnReason.class, names = {"NATURAL", "CHUNK_GEN", "PATROL", "REINFORCEMENTS", "VILLAGE_DEFENSE", "VILLAGE_INVASION", "BREEDING", "SPAWNER", "TRIAL_SPAWNER", "DISPENSE_EGG", "RAID", "SLIME_SPLIT"})
    void automaticSpawnsArePrevented(SpawnReason reason) {
        CreatureSpawnEvent event = spawn(reason);
        listener.onSpawn(event);
        verify(event).setCancelled(true);
    }

    @ParameterizedTest
    @EnumSource(value = SpawnReason.class, names = {"CUSTOM", "COMMAND", "DEFAULT", "SPAWNER_EGG", "EGG", "BUCKET", "BUILD_IRONGOLEM", "BUILD_WITHER"})
    void explicitPlayerAndPluginSpawnsKeepTheirOwnPermissionPolicy(SpawnReason reason) {
        CreatureSpawnEvent event = spawn(reason);
        listener.onSpawn(event);
        verify(event, never()).setCancelled(anyBoolean());
    }

    @Test void spawnSettingAndWorldScopeAreRespected() {
        config.set("prevent-natural-spawns", false);
        CreatureSpawnEvent configurable = spawn(SpawnReason.NATURAL);
        listener.onSpawn(configurable);
        verify(configurable, never()).setCancelled(anyBoolean());
        config.set("prevent-natural-spawns", true);
        when(world.getName()).thenReturn("wild");
        CreatureSpawnEvent outside = spawn(SpawnReason.NATURAL);
        listener.onSpawn(outside);
        verify(outside, never()).setCancelled(anyBoolean());
    }

    @Test void explosionsFromBlocksAndEntitiesCannotDamageLobby() {
        BlockExplodeEvent blockExplosion = mock(BlockExplodeEvent.class);
        when(blockExplosion.getBlock()).thenReturn(block);
        listener.onBlockExplosion(blockExplosion);
        verify(blockExplosion).setCancelled(true);
        EntityExplodeEvent entityExplosion = mock(EntityExplodeEvent.class);
        when(entityExplosion.getLocation()).thenReturn(new Location(world, 1, 2, 3));
        listener.onEntityExplosion(entityExplosion);
        verify(entityExplosion).setCancelled(true);
        config.set("environment.explosions", false);
        clearInvocations(entityExplosion);
        listener.onEntityExplosion(entityExplosion);
        verify(entityExplosion, never()).setCancelled(anyBoolean());
    }

    @Test void naturalFireBurnsAreBlockedButPermissionedPlayerMayIgnite() {
        BlockBurnEvent burn = new BlockBurnEvent(block);
        listener.onBurn(burn);
        assertTrue(burn.isCancelled());
        Player builder = mock(Player.class);
        when(builder.hasPermission("overworld.lobby.bypass.item-use")).thenReturn(true);
        BlockIgniteEvent allowed = new BlockIgniteEvent(block, BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL, builder);
        listener.onIgnite(allowed);
        assertFalse(allowed.isCancelled());
        BlockIgniteEvent natural = new BlockIgniteEvent(block, BlockIgniteEvent.IgniteCause.LAVA, (Entity) null);
        listener.onIgnite(natural);
        assertTrue(natural.isCancelled());
    }

    @Test void lavaWaterGrowthDecayAndFormationRespectSeparateFlags() {
        BlockFromToEvent flow = new BlockFromToEvent(block, block);
        listener.onFlow(flow);
        assertTrue(flow.isCancelled());
        BlockState state = mock(BlockState.class);
        BlockGrowEvent growth = new BlockGrowEvent(block, state);
        listener.onGrow(growth);
        assertTrue(growth.isCancelled());
        LeavesDecayEvent leaves = new LeavesDecayEvent(block);
        listener.onLeavesDecay(leaves);
        assertTrue(leaves.isCancelled());
        BlockFormEvent form = new BlockFormEvent(block, state);
        listener.onForm(form);
        assertTrue(form.isCancelled());
        BlockFadeEvent fade = new BlockFadeEvent(block, state);
        listener.onFade(fade);
        assertTrue(fade.isCancelled());
        config.set("environment.fluid-flow", false);
        BlockFromToEvent allowed = new BlockFromToEvent(block, block);
        listener.onFlow(allowed);
        assertFalse(allowed.isCancelled());
    }

    @Test void playerFertilizingAndFrostWalkerCanUseTheirActionBypass() {
        Player builder = mock(Player.class);
        when(builder.hasPermission("overworld.lobby.bypass.item-use")).thenReturn(true);
        when(builder.hasPermission("overworld.lobby.bypass.block-place")).thenReturn(true);
        BlockFertilizeEvent fertilize = new BlockFertilizeEvent(block, builder, List.of());
        listener.onFertilize(fertilize);
        assertFalse(fertilize.isCancelled());
        BlockFormEvent frost = new EntityBlockFormEvent(builder, block, mock(BlockState.class));
        listener.onForm(frost);
        assertFalse(frost.isCancelled());
        BlockFertilizeEvent automated = new BlockFertilizeEvent(block, null, List.of());
        listener.onFertilize(automated);
        assertTrue(automated.isCancelled());
    }

    @Test void mobBlockChangesAndMapPhysicsCannotModifyDecorations() {
        EntityChangeBlockEvent grief = mock(EntityChangeBlockEvent.class);
        when(grief.getEntity()).thenReturn(mock(Entity.class));
        when(grief.getBlock()).thenReturn(block);
        listener.onEntityBlockChange(grief);
        verify(grief).setCancelled(true);
        HangingBreakEvent decoration = mock(HangingBreakEvent.class);
        org.bukkit.entity.Hanging hanging = mock(org.bukkit.entity.Hanging.class);
        when(hanging.getWorld()).thenReturn(world);
        when(decoration.getEntity()).thenReturn(hanging);
        when(decoration.getCause()).thenReturn(HangingBreakEvent.RemoveCause.PHYSICS);
        listener.onHangingBreak(decoration);
        verify(decoration).setCancelled(true);
    }

    @Test void permittedPlayerProjectileCanRemoveDecorationAcrossBothListeners() {
        Player builder = mock(Player.class);
        when(builder.hasPermission(Action.ENTITY_DAMAGE.permission())).thenReturn(true);
        Arrow arrow = mock(Arrow.class);
        when(arrow.getShooter()).thenReturn(builder);
        Hanging hanging = mock(Hanging.class);
        when(hanging.getWorld()).thenReturn(world);
        var damage = mock(DamageSource.class);
        var event = new HangingBreakByEntityEvent(hanging, arrow, damage, HangingBreakEvent.RemoveCause.ENTITY);
        var protections = new ProtectionListener(new ProtectionPolicy(() -> Settings.load(config)));
        protections.onHangingBreak(event);
        listener.onHangingBreak(event);
        assertFalse(event.isCancelled());

        when(builder.hasPermission(Action.ENTITY_DAMAGE.permission())).thenReturn(false);
        var denied = new HangingBreakByEntityEvent(hanging, arrow, damage, HangingBreakEvent.RemoveCause.ENTITY);
        protections.onHangingBreak(denied);
        listener.onHangingBreak(denied);
        assertTrue(denied.isCancelled());
    }

    @Test void hangingDamageSourceAlsoIdentifiesPermissionedPlayer() {
        Player builder = mock(Player.class);
        when(builder.hasPermission(Action.ENTITY_DAMAGE.permission())).thenReturn(true);
        Hanging hanging = mock(Hanging.class);
        when(hanging.getWorld()).thenReturn(world);
        DamageSource damage = mock(DamageSource.class);
        when(damage.getCausingEntity()).thenReturn(builder);
        var event = new HangingBreakByEntityEvent(hanging, mock(Entity.class), damage, HangingBreakEvent.RemoveCause.ENTITY);
        new ProtectionListener(new ProtectionPolicy(() -> Settings.load(config))).onHangingBreak(event);
        listener.onHangingBreak(event);
        assertFalse(event.isCancelled());
    }

    @Test void evenPermissionedPlayerExplosionUsesIndependentWorldFlag() {
        Player builder = mock(Player.class);
        when(builder.hasPermission("overworld.lobby.bypass")).thenReturn(true);
        Hanging hanging = mock(Hanging.class);
        when(hanging.getWorld()).thenReturn(world);
        DamageSource damage = mock(DamageSource.class);
        when(damage.getCausingEntity()).thenReturn(builder);
        var blocked = new HangingBreakByEntityEvent(hanging, builder, damage, HangingBreakEvent.RemoveCause.EXPLOSION);
        new ProtectionListener(new ProtectionPolicy(() -> Settings.load(config))).onHangingBreak(blocked);
        assertFalse(blocked.isCancelled());
        listener.onHangingBreak(blocked);
        assertTrue(blocked.isCancelled());
        config.set("environment.explosions", false);
        var allowed = new HangingBreakByEntityEvent(hanging, builder, damage, HangingBreakEvent.RemoveCause.EXPLOSION);
        listener.onHangingBreak(allowed);
        assertFalse(allowed.isCancelled());
    }

    @Test void redstoneAndPistonsAreSuppressedOnlyWhenConfigured() {
        BlockRedstoneEvent redstone = new BlockRedstoneEvent(block, 0, 15);
        listener.onRedstone(redstone);
        assertEquals(0, redstone.getNewCurrent());
        BlockPistonExtendEvent piston = mock(BlockPistonExtendEvent.class);
        when(piston.getBlock()).thenReturn(block);
        listener.onPistonExtend(piston);
        verify(piston).setCancelled(true);
        config.set("environment.redstone", false);
        BlockRedstoneEvent allowed = new BlockRedstoneEvent(block, 0, 15);
        listener.onRedstone(allowed);
        assertEquals(15, allowed.getNewCurrent());
    }

    @Test void fireSpreadIsBlockedEvenWhenOrdinarySpreadIsAllowed() {
        config.set("environment.block-spread", false);
        BlockState fire = mock(BlockState.class);
        when(fire.getType()).thenReturn(Material.FIRE);
        BlockSpreadEvent spread = new BlockSpreadEvent(block, block, fire);
        listener.onSpread(spread);
        assertTrue(spread.isCancelled());
    }

    private CreatureSpawnEvent spawn(SpawnReason reason) {
        CreatureSpawnEvent event = mock(CreatureSpawnEvent.class);
        when(event.getLocation()).thenReturn(new Location(world, 0, 64, 0));
        when(event.getSpawnReason()).thenReturn(reason);
        return event;
    }
}
