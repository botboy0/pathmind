package com.pathmind.nodes;

import net.minecraft.util.Identifier;

import java.util.function.Consumer;

/**
 * Registration surface passed to Pathmind node addon entrypoints.
 */
@FunctionalInterface
public interface PathmindNodeRegistrar {
    PathmindNodeDefinition register(Identifier id, Consumer<PathmindNodeDefinition.Builder> configurer);
}
