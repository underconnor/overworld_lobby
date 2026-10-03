package io.github.underconnor.overworld.lobby;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DenialMessagesTest {
    private final Player player = mock(Player.class);
    private final AtomicLong clock = new AtomicLong();
    private final AtomicReference<DenialMessageSettings> settings = new AtomicReference<>(DenialMessageSettings.defaults());
    private final DenialMessages messages = new DenialMessages(settings::get, clock::get);

    @BeforeEach void playerId() { when(player.getUniqueId()).thenReturn(UUID.randomUUID()); }

    @Test void approvedDefaultChatMessageIsRedAndHasNoLobbyPrefix() {
        assertTrue(messages.send(player, Action.BLOCK_BREAK));
        ArgumentCaptor<Component> capture = ArgumentCaptor.forClass(Component.class);
        verify(player).sendMessage(capture.capture());
        assertEquals("여기서는 블록을 부술 수 없습니다.", PlainTextComponentSerializer.plainText().serialize(capture.getValue()));
        assertEquals(NamedTextColor.RED, capture.getValue().color());
        verify(player, never()).sendActionBar(any(Component.class));
    }

    @Test void cooldownIsGlobalPerPlayerAcrossActionsAndUsesExactMonotonicBoundary() {
        assertTrue(messages.send(player, Action.BLOCK_BREAK));
        assertFalse(messages.send(player, Action.INTERACT));
        clock.set(999_999_999);
        assertFalse(messages.send(player, Action.ITEM_DROP));
        clock.set(1_000_000_000);
        assertTrue(messages.send(player, Action.ITEM_DROP));
        verify(player, times(2)).sendMessage(any(Component.class));
        Player another = mock(Player.class);
        when(another.getUniqueId()).thenReturn(UUID.randomUUID());
        assertTrue(messages.send(another, Action.BLOCK_BREAK));
        verify(another).sendMessage(any(Component.class));
    }

    @Test void reloadCanChangeChannelColorTextAndCooldownWithoutRecreatingMessenger() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("messages.channel", "action_bar");
        config.set("messages.prefix", "&6");
        config.set("messages.cooldown-ms", 0);
        config.set("messages.denied.block-break", "설정한 안내 문구");
        settings.set(DenialMessageSettings.load(config));
        assertTrue(messages.send(player, Action.BLOCK_BREAK));
        assertTrue(messages.send(player, Action.BLOCK_BREAK));
        ArgumentCaptor<Component> capture = ArgumentCaptor.forClass(Component.class);
        verify(player, times(2)).sendActionBar(capture.capture());
        assertEquals("설정한 안내 문구", PlainTextComponentSerializer.plainText().serialize(capture.getValue()));
        assertEquals(NamedTextColor.GOLD, capture.getValue().color());
        verify(player, never()).sendMessage(any(Component.class));
    }

    @Test void disabledAndBlankMessagesDoNotConsumeCooldown() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("messages.enabled", false);
        settings.set(DenialMessageSettings.load(config));
        assertFalse(messages.send(player, Action.BLOCK_BREAK));
        config.set("messages.enabled", true);
        config.set("messages.denied.block-break", "");
        settings.set(DenialMessageSettings.load(config));
        assertFalse(messages.send(player, Action.BLOCK_BREAK));
        assertTrue(messages.send(player, Action.INTERACT));
        verify(player, times(1)).sendMessage(any(Component.class));
    }

    @Test void passiveDamageHungerAndUnattributedChangesAlwaysRemainSilent() {
        var denied = new EnumMap<Action, String>(Action.class);
        denied.put(Action.PLAYER_DAMAGE, "소음");
        denied.put(Action.HUNGER, "소음");
        settings.set(new DenialMessageSettings(true, DenialMessageSettings.Channel.CHAT, 0, "&c", denied));
        assertFalse(messages.send(player, Action.PLAYER_DAMAGE));
        assertFalse(messages.send(player, Action.HUNGER));
        assertFalse(messages.send(null, Action.BLOCK_BREAK));
        verify(player, never()).sendMessage(any(Component.class));
    }

    @Test void quitCleanupAndShutdownCleanupClearCooldownState() {
        assertTrue(messages.send(player, Action.BLOCK_BREAK));
        assertFalse(messages.send(player, Action.INTERACT));
        messages.clear(player.getUniqueId());
        assertTrue(messages.send(player, Action.INTERACT));
        messages.close();
        assertTrue(messages.send(player, Action.ITEM_DROP));
    }

    @Test void loadUsesAllApprovedStringsAndAllowsPerActionBlankOptOut() {
        DenialMessageSettings defaults = DenialMessageSettings.load(new YamlConfiguration());
        assertTrue(defaults.enabled());
        assertEquals(1000, defaults.cooldownMillis());
        assertEquals("&c", defaults.prefix());
        assertEquals("여기서는 공격할 수 없습니다.", defaults.denied().get(Action.PVP));
        assertEquals("여기서는 이 아이템을 사용할 수 없습니다.", defaults.denied().get(Action.BUCKETS));
        assertEquals("여기서는 이 상자를 열 수 없습니다.", defaults.denied().get(Action.CONTAINERS));
        assertFalse(defaults.denied().containsKey(Action.HUNGER));
        assertThrows(UnsupportedOperationException.class, () -> defaults.denied().put(Action.INTERACT, "변경"));
    }

    @Test void packagedMessagesExactlyMatchApprovedDefaults() {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new InputStreamReader(
            Objects.requireNonNull(getClass().getResourceAsStream("/config.yml")), StandardCharsets.UTF_8));
        assertEquals(DenialMessageSettings.defaults(), DenialMessageSettings.load(config));
    }

    @ParameterizedTest @ValueSource(strings = {"enabled", "channel", "prefix", "denied.block-break"})
    void wrongFieldTypesAreRejected(String field) {
        YamlConfiguration config = new YamlConfiguration();
        config.set("messages." + field, 12);
        assertThrows(IllegalArgumentException.class, () -> DenialMessageSettings.load(config));
    }

    @Test void invalidCooldownChannelAndDenialSectionCannotSilentlyFallBack() {
        for (Object value : new Object[]{-1, 60_001, 1.5, "1000", Double.NaN, Double.POSITIVE_INFINITY}) {
            YamlConfiguration config = new YamlConfiguration();
            config.set("messages.cooldown-ms", value);
            assertThrows(IllegalArgumentException.class, () -> DenialMessageSettings.load(config));
        }
        YamlConfiguration config = new YamlConfiguration();
        config.set("messages.channel", "TITLE");
        assertThrows(IllegalArgumentException.class, () -> DenialMessageSettings.load(config));
        config.set("messages.channel", "CHAT");
        config.set("messages.denied", "invalid");
        assertThrows(IllegalArgumentException.class, () -> DenialMessageSettings.load(config));
        config.set("messages", "invalid");
        assertThrows(IllegalArgumentException.class, () -> DenialMessageSettings.load(config));
        assertThrows(IllegalArgumentException.class, () -> new DenialMessageSettings(true, DenialMessageSettings.Channel.CHAT, -1, "", Map.of()));
    }
}
