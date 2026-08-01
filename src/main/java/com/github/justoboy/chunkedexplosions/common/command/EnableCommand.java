package com.github.justoboy.chunkedexplosions.common.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import com.github.justoboy.chunkedexplosions.core.ModConfig;

/**
 * Command to enable or disable chunked explosions.
 * <p>
 * This command allows toggling the chunked explosions feature on or off.
 * When disabled, explosions will use the vanilla behavior.
 * </p>
 * 
 * <h2>Usage</h2>
 * <ul>
 *   <li>{@code /chunkedexplosions enable} - Get current enable status</li>
 *   <li>{@code /chunkedexplosions enable true} - Enable chunked explosions</li>
 *   <li>{@code /chunkedexplosions enable false} - Disable chunked explosions</li>
 * </ul>
 */
public class EnableCommand {

    static {
        CommandComments.addComment("enable", "Enable or disable chunked explosions. When disabled, explosions use vanilla behavior.");
    }
    
    /**
     * Registers the enable command with the command dispatcher.
     * 
     * @param ignoredBuildContext the command build context (unused for this command)
     * @return an argument builder for the enable command
     */
    public static ArgumentBuilder<CommandSourceStack, ?> register(CommandBuildContext ignoredBuildContext) {
        return Commands.literal("enable")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(context -> setValue(context, BoolArgumentType.getBool(context, "value"))))
        .executes(EnableCommand::sendValueMessage);
    }

    /**
     * Sets the enable status for chunked explosions.
     * 
     * @param context the command context
     * @param value true to enable, false to disable
     * @return the command execution result (1 for success)
     */
    private static int setValue(CommandContext<CommandSourceStack> context, boolean value) {
        ModConfig.setEnable(value);
        sendValueMessage(context);
        return 1;
    }

    /**
     * Sends a message showing the current enable status.
     * 
     * @param context the command context
     * @return the command execution result (1 for success)
     */
    private static int sendValueMessage(CommandContext<CommandSourceStack> context) {
        String message = ModConfig.getEnable() ? "enabled" : "disabled";
        context.getSource().sendSuccess(() -> Component.literal("Chunked Explosions: " + message), true);
        return 1;
    }
}