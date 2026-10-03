package io.github.underconnor.overworld.lobby;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.file.FileConfiguration;

/** Configured separately so a reload can validate protection and message settings together. */
public record DenialMessageSettings(boolean enabled, Channel channel, long cooldownMillis,
                                    String prefix, Map<Action, String> denied) {
    public enum Channel { CHAT, ACTION_BAR }

    public DenialMessageSettings {
        if (channel == null || prefix == null || denied == null) throw new IllegalArgumentException("알림 설정은 null일 수 없습니다.");
        if (cooldownMillis < 0 || cooldownMillis > 60_000) throw new IllegalArgumentException("messages.cooldown-ms: 0부터 60000 사이여야 합니다.");
        denied = Map.copyOf(denied);
    }

    public static DenialMessageSettings defaults() {
        EnumMap<Action, String> denied = new EnumMap<>(Action.class);
        denied.put(Action.BLOCK_BREAK, "여기서는 블록을 부술 수 없습니다.");
        denied.put(Action.BLOCK_PLACE, "여기서는 블록을 설치할 수 없습니다.");
        denied.put(Action.INTERACT, "여기서는 이 블록을 사용할 수 없습니다.");
        denied.put(Action.ENTITY_INTERACT, "여기서는 이 엔티티를 조작할 수 없습니다.");
        denied.put(Action.PVP, "여기서는 공격할 수 없습니다.");
        denied.put(Action.ENTITY_DAMAGE, "여기서는 공격할 수 없습니다.");
        denied.put(Action.ITEM_USE, "여기서는 이 아이템을 사용할 수 없습니다.");
        denied.put(Action.BUCKETS, "여기서는 이 아이템을 사용할 수 없습니다.");
        denied.put(Action.ITEM_DROP, "여기서는 아이템을 버릴 수 없습니다.");
        denied.put(Action.ITEM_PICKUP, "여기서는 아이템을 주울 수 없습니다.");
        denied.put(Action.VEHICLES, "여기서는 탈것을 사용할 수 없습니다.");
        denied.put(Action.PORTALS, "여기서는 포털을 사용할 수 없습니다.");
        denied.put(Action.CONTAINERS, "여기서는 이 상자를 열 수 없습니다.");
        denied.put(Action.WORLD_BORDER, "이 월드의 경계를 벗어날 수 없습니다.");
        return new DenialMessageSettings(true, Channel.CHAT, 1000, "&c", denied);
    }

    public static DenialMessageSettings load(FileConfiguration config) {
        DenialMessageSettings defaults = defaults();
        Object enabledValue = config.get("messages.enabled");
        if (enabledValue != null && !(enabledValue instanceof Boolean)) throw invalid("enabled", "true 또는 false여야 합니다.");
        String channelName = string(config, "channel", defaults.channel().name()).trim().toUpperCase(Locale.ROOT);
        Channel channel;
        try { channel = Channel.valueOf(channelName); }
        catch (IllegalArgumentException error) { throw invalid("channel", "CHAT 또는 ACTION_BAR여야 합니다."); }
        Object cooldownValue = config.get("messages.cooldown-ms");
        long cooldown = defaults.cooldownMillis();
        if (cooldownValue != null) {
            if (!(cooldownValue instanceof Number number) || !Double.isFinite(number.doubleValue())
                || number.doubleValue() != Math.floor(number.doubleValue())
                || number.doubleValue() < 0 || number.doubleValue() > 60_000)
                throw invalid("cooldown-ms", "0부터 60000 사이의 정수여야 합니다.");
            cooldown = number.longValue();
        }
        Object messagesValue = config.get("messages");
        if (messagesValue != null && !config.isConfigurationSection("messages"))
            throw new IllegalArgumentException("messages: 알림 설정 항목이어야 합니다.");
        Object deniedValue = config.get("messages.denied");
        if (deniedValue != null && !config.isConfigurationSection("messages.denied"))
            throw invalid("denied", "행동별 문구 항목이어야 합니다.");
        EnumMap<Action, String> denied = new EnumMap<>(Action.class);
        for (Action action : Action.values()) {
            if (action == Action.PLAYER_DAMAGE || action == Action.HUNGER) continue;
            denied.put(action, string(config, "denied." + action.key(), defaults.denied().get(action)));
        }
        return new DenialMessageSettings(enabledValue == null || (Boolean) enabledValue, channel, cooldown,
            string(config, "prefix", defaults.prefix()), denied);
    }

    private static String string(FileConfiguration config, String key, String fallback) {
        Object value = config.get("messages." + key);
        if (value == null) return fallback;
        if (!(value instanceof String text)) throw invalid(key, "문자열이어야 합니다.");
        return text;
    }

    private static IllegalArgumentException invalid(String key, String message) {
        return new IllegalArgumentException("messages." + key + ": " + message);
    }
}
