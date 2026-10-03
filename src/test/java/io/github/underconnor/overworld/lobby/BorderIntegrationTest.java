package io.github.underconnor.overworld.lobby;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BorderIntegrationTest {
    @TempDir Path folder;
    final JavaPlugin plugin = mock(JavaPlugin.class);
    final Server server = mock(Server.class);
    final World world = mock(World.class), other = mock(World.class);
    final Player player = mock(Player.class);
    final DenialMessages messages = mock(DenialMessages.class);
    final AtomicReference<Location> position = new AtomicReference<>();
    YamlConfiguration config;
    ProtectionPolicy policy;
    BorderController controller;
    @BeforeEach void setup() throws Exception {
        when(world.getName()).thenReturn("lobby"); when(other.getName()).thenReturn("other");
        when(server.getWorlds()).thenReturn(List.of(world,other)); when(server.getWorld("lobby")).thenReturn(world); when(server.getWorld("other")).thenReturn(other);
        when(plugin.getServer()).thenReturn(server); when(plugin.getDataFolder()).thenReturn(folder.toFile()); when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
        when(world.getMinHeight()).thenReturn(-64); when(world.getMaxHeight()).thenReturn(320); when(world.getHighestBlockYAt(anyInt(),anyInt())).thenReturn(64);
        Block air=mock(Block.class), stone=mock(Block.class); when(air.isPassable()).thenReturn(true); when(air.getType()).thenReturn(Material.AIR); when(stone.getType()).thenReturn(Material.STONE);
        when(world.getBlockAt(anyInt(),anyInt(),anyInt())).thenAnswer(call -> (int)call.getArgument(1)>=65 ? air : stone);
        position.set(new Location(world,0.5,65,0.5)); when(player.getLocation()).thenAnswer(call -> position.get().clone()); when(player.getWorld()).thenReturn(world);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL); when(player.getUniqueId()).thenReturn(UUID.randomUUID()); when(player.isOp()).thenReturn(true);
        when(player.teleport(any(Location.class))).thenAnswer(call -> { position.set(((Location)call.getArgument(0)).clone()); return true; });
        doReturn(List.of(player)).when(server).getOnlinePlayers();
        config=new YamlConfiguration(); config.set("world-borders.lobby.min-x",-10); config.set("world-borders.lobby.max-x",10); config.set("world-borders.lobby.min-z",-10); config.set("world-borders.lobby.max-z",10);
        policy=new ProtectionPolicy(() -> Settings.load(config)); controller=new BorderController(plugin,policy,messages);
    }
    @Test void operatorAndLegacyPermissionCannotCrossByMovementAndValidEdgesAreIncluded() {
        when(player.hasPermission(anyString())).thenReturn(true);
        var move=new PlayerMoveEvent(player,position.get(),new Location(world,30,65,0)); controller.onMove(move);
        assertEquals(0.5,move.getTo().getX()); verify(messages).send(player,Action.WORLD_BORDER);
        var edge=new PlayerMoveEvent(player,position.get(),new Location(world,10,65,-10)); controller.onMove(edge); assertEquals(10,edge.getTo().getX()); assertFalse(edge.isCancelled());
        assertFalse(policy.bypasses(player,Action.WORLD_BORDER));
    }
    @Test void allTeleportCausesRejectOutsideDestinationButOtherWorldAndInsideRemainAllowed() {
        for (var cause : List.of(PlayerTeleportEvent.TeleportCause.COMMAND,PlayerTeleportEvent.TeleportCause.ENDER_PEARL,PlayerTeleportEvent.TeleportCause.PLUGIN)) {
            var e=new PlayerTeleportEvent(player,position.get(),new Location(world,11,65,0),cause); controller.onTeleport(e); assertTrue(e.isCancelled());
        }
        var permitted=new PlayerTeleportEvent(player,position.get(),new Location(other,100,65,100)); controller.onTeleport(permitted); assertFalse(permitted.isCancelled());
    }
    @Test void disabledProtectionAndUnboundedWorldNeverInterceptMovement() {
        config.set("protection.world-border",false); var e=new PlayerMoveEvent(player,position.get(),new Location(world,100,65,100)); controller.onMove(e); assertEquals(100,e.getTo().getX());
        config.set("protection.world-border",true); config.set("worlds",List.of("other")); controller.onMove(e); assertEquals(100,e.getTo().getX()); verifyNoInteractions(messages);
    }
    @Test void alreadyOutsideFlyingRecoversInsideWithoutChangingModeOrBlocks() {
        when(player.isFlying()).thenReturn(true); position.set(new Location(world,30,65,0)); controller.refresh(); assertTrue(Settings.load(config).borderFor(world).contains(position.get().getX(),position.get().getZ())); assertEquals(65,position.get().getY());
        verify(player,never()).setGameMode(any()); verify(player,never()).setFlying(anyBoolean());
    }
    @Test void validReentryIsAlwaysAllowed() {
        var e=new PlayerMoveEvent(player,new Location(world,20,65,20),new Location(world,0,65,0));controller.onMove(e);assertFalse(e.isCancelled());assertEquals(0,e.getTo().getX());
    }
    @Test void vehicleEntryOutsideIsRejectedAndRiderIsReturnedWhenVehicleCrosses() {
        Vehicle vehicle=mock(Vehicle.class);when(vehicle.getLocation()).thenReturn(new Location(world,20,65,0));when(vehicle.getPassengers()).thenReturn(List.of(player));
        var enter=new VehicleEnterEvent(vehicle,player);controller.onVehicleEnter(enter);assertTrue(enter.isCancelled());
        controller.onVehicleMove(new VehicleMoveEvent(vehicle,new Location(world,9,65,0),new Location(world,20,65,0)));assertEquals(9,position.get().getX());verify(player).leaveVehicle();
    }
    @Test void approvedBorderMessageAndNonBypassMetadataAreUsed() { assertEquals("이 월드의 경계를 벗어날 수 없습니다.",DenialMessageSettings.defaults().denied().get(Action.WORLD_BORDER)); }
    @Test void rectangleRejectsNonfiniteEmptyAndUnsupportedBounds() {
        for(double x: new double[]{Double.NaN,Double.POSITIVE_INFINITY,30_000_000})assertThrows(IllegalArgumentException.class,()->new Settings.BorderSettings(true,-1,x,-1,1));
        assertThrows(IllegalArgumentException.class,()->new Settings.BorderSettings(true,1,1,-1,1));
    }
    @Test void commandsNormalizeCornersPersistIndependentlyAndOffRetainsSavedBounds() throws Exception {
        Path file=folder.resolve("config.yml"); Files.writeString(file,"worlds: [lobby, other]\ncustom-option: '유지'\nworld-settings:\n  other:\n    time: {ticks: 18000}\n");
        AtomicReference<Settings> active=new AtomicReference<>(Settings.load(read(file)));
        var command=new BorderCommands(plugin,active::get,()->active.set(Settings.load(read(file))));
        command.handle(player,"lobby",new String[]{"border","set","10","10","-10","-10"});
        assertEquals(-10,active.get().borderFor(world).minX());assertEquals(10,active.get().borderFor(world).maxZ());assertEquals(18000,active.get().timeFor(other).ticks());assertEquals("유지",read(file).getString("custom-option"));
        command.handle(player,"lobby",new String[]{"border","off"});assertFalse(active.get().borderFor(world).enabled());assertEquals(-10,active.get().borderFor(world).minX());
    }
    @Test void permissionInvalidShapeAndSpawnOutsideRejectWithoutWriting() throws Exception {
        Path file=folder.resolve("config.yml");Files.writeString(file,"spawn: {enabled: true, world: lobby, x: 0, y: 65, z: 0}\n");byte[] before=Files.readAllBytes(file);
        var command=new BorderCommands(plugin,()->Settings.load(config),()->fail("must not reload"));
        when(player.isOp()).thenReturn(false);command.handle(player,"lobby",new String[]{"border","set","-10","-10","10","10"});
        when(player.isOp()).thenReturn(true);command.handle(player,"lobby",new String[]{"border","set","0","0","0","1"});
        command.handle(player,"lobby",new String[]{"border","set","20","20","30","30"});assertArrayEquals(before,Files.readAllBytes(file));
    }
    private static YamlConfiguration read(Path file) throws Exception { var c=new YamlConfiguration();c.options().pathSeparator('\0');c.load(file.toFile());c.options().pathSeparator('.');return c; }
}
