package io.github.underconnor.overworld.lobby;

public enum EnvironmentRule {
    EXPLOSIONS("explosions"), FIRE("fire"), FLUID_FLOW("fluid-flow"),
    BLOCK_GROWTH("block-growth"), BLOCK_SPREAD("block-spread"), LEAF_DECAY("leaf-decay"),
    BLOCK_FORM("block-form"), MOB_GRIEF("mob-grief"), PISTONS("pistons"),
    REDSTONE("redstone");

    private final String key;
    EnvironmentRule(String key) { this.key = key; }
    public String key() { return key; }
}
