package com.github.justoboy.chunkedexplosions.common.command;

import com.github.justoboy.chunkedexplosions.core.ModConfig;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;

import java.util.concurrent.CompletableFuture;

public class SuggestionProviders {

    public static CompletableFuture<Suggestions> timingSuggestions(CommandContext<CommandSourceStack> ignoredContext, SuggestionsBuilder builder) {
        for (ModConfig.Timing timing : ModConfig.Timing.values()) {
            builder.suggest(timing.name().toLowerCase());
        }
        return builder.buildFuture();
    }

    public static CompletableFuture<Suggestions> methodSuggestions(CommandContext<CommandSourceStack> ignoredContext, SuggestionsBuilder builder) {
        for (ModConfig.Method method : ModConfig.Method.values()) {
            builder.suggest(method.name().toLowerCase());
        }
        return builder.buildFuture();
    }

    public static CompletableFuture<Suggestions> integerSuggestions(CommandContext<CommandSourceStack> ignoredContext, SuggestionsBuilder builder) {
        builder.suggest(0);
        builder.suggest(1);
        builder.suggest(16);
        builder.suggest(256);
        builder.suggest(4096);
        return builder.buildFuture();
    }
}