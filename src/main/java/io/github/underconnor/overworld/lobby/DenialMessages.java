package io.github.underconnor.overworld.lobby;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;

/** One cooldown per actor avoids duplicate main-hand/off-hand and chained event messages. */
public final class DenialMessages implements AutoCloseable {
    private final Supplier<DenialMessageSettings> settings;
    private final LongSupplier clockNanos;
    private final Map<UUID, Long> lastSent = new HashMap<>();

    public DenialMessages(Supplier<DenialMessageSettings> settings) { this(settings, System::nanoTime); }

    DenialMessages(Supplier<DenialMessageSettings> settings, LongSupplier clockNanos) {
        this.settings = settings;
        this.clockNanos = clockNanos;
    }

    public boolean send(Player actor, Action action) {
        if (actor == null || action == Action.PLAYER_DAMAGE || action == Action.HUNGER) return false;
        DenialMessageSettings current = settings.get();
        String text = current.denied().get(action);
        if (!current.enabled() || text == null || text.isBlank()) return false;
        long now = clockNanos.getAsLong();
        UUID playerId = actor.getUniqueId();
        Long previous = lastSent.get(playerId);
        if (previous != null && now - previous < TimeUnit.MILLISECONDS.toNanos(current.cooldownMillis())) return false;
        Component message = LegacyComponentSerializer.legacyAmpersand().deserialize(current.prefix() + text);
        if (current.channel() == DenialMessageSettings.Channel.ACTION_BAR) actor.sendActionBar(message);
        else actor.sendMessage(message);
        lastSent.put(playerId, now);
        return true;
    }

    public void clear(UUID playerId) { lastSent.remove(playerId); }
    @Override public void close() { lastSent.clear(); }
}
