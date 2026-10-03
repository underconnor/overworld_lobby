package io.github.underconnor.overworld.lobby;

public enum Action {
    BLOCK_BREAK("block-break"), BLOCK_PLACE("block-place"), BUCKETS("buckets"),
    INTERACT("interact"), CONTAINERS("containers"), ENTITY_INTERACT("entity-interact"),
    ENTITY_DAMAGE("entity-damage"), PVP("pvp"), ITEM_USE("item-use"),
    ITEM_DROP("item-drop"), ITEM_PICKUP("item-pickup"), VEHICLES("vehicles"),
    PORTALS("portals"), PLAYER_DAMAGE("player-damage"), HUNGER("hunger");

    private final String key;
    Action(String key) { this.key = key; }
    public String key() { return key; }
    public String permission() { return "overworld.lobby.bypass." + key; }
}
