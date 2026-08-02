package com.github.justoboy.chunkedexplosions.common.command;

import com.github.justoboy.chunkedexplosions.core.ModConfig;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Provides command suggestions for the chunked explosions mod.
 * <p>
 * This class contains suggestion providers for various command arguments,
 * including timing modes, method modes, and command names.
 * </p>
 */
public class SuggestionProviders {

    /**
     * Provides suggestions for timing modes (START, END, START_END, SPREAD).
     * 
     * @param ignoredContext the command context (unused)
     * @param builder the suggestions builder
     * @return a future containing the suggestions
     */
    public static CompletableFuture<Suggestions> timingSuggestions(CommandContext<CommandSourceStack> ignoredContext, SuggestionsBuilder builder) {
        for (ModConfig.Timing timing : ModConfig.Timing.values()) {
            builder.suggest(timing.name().toLowerCase());
        }
        return builder.buildFuture();
    }

    /**
     * Provides suggestions for boolean values.
     * 
     * @param ignoredContext the command context (unused)
     * @param builder the suggestions builder
     * @return a future containing the suggestions
     */
    public static CompletableFuture<Suggestions> boolSuggestions(CommandContext<CommandSourceStack> ignoredContext, SuggestionsBuilder builder) {
        builder.suggest("true");
        builder.suggest("false");
        return builder.buildFuture();
    }

    /**
     * Provides suggestions for integer values commonly used in the mod.
     * 
     * @param ignoredContext the command context (unused)
     * @param builder the suggestions builder
     * @return a future containing the suggestions
     */
    public static CompletableFuture<Suggestions> integerSuggestions(CommandContext<CommandSourceStack> ignoredContext, SuggestionsBuilder builder) {
        builder.suggest(0);
        builder.suggest(1);
        builder.suggest(16);
        builder.suggest(256);
        builder.suggest(4096);
        return builder.buildFuture();
    }

    /**
     * Provides suggestions for command names available in the mod.
     * 
     * @param ignoredContext the command context (unused)
     * @param builder the suggestions builder
     * @return a future containing the suggestions
     */
    public static CompletableFuture<Suggestions> commandSuggestions(CommandContext<CommandSourceStack> ignoredContext, SuggestionsBuilder builder) {
        for (String command : Map.copyOf(CommandComments.COMMAND_COMMENTS).keySet()) {
            builder.suggest(command);
        }
        return builder.buildFuture();
    }
}