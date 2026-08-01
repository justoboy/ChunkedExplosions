package com.github.justoboy.chunkedexplosions.common.command;

import com.github.justoboy.chunkedexplosions.core.ModConfig;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * Command to configure how explosion knockback is applied to entities.
 * <p>
 * This controls whether knockback is applied all at once or spread over time
 * during explosion processing. The behavior depends on the {@code knockbackTiming}
 * setting.
 * </p>
 * 
 * <h2>Usage</h2>
 * <ul>
 *   <li>{@code /chunkedexplosions knockbackMethod} - Get current knockback method</li>
 *   <li>{@code /chunkedexplosions knockbackMethod <method>} - Set knockback method</li>
 * </ul>
 * 
 * <h2>Knockback Methods</h2>
 * <ul>
 *   <li><b>SPREAD:</b> Knockback is distributed over time based on the timing mode
 *     <ul>
 *       <li>START: Full knockback applied at start</li>
 *       <li>END: Full knockback applied at end</li>
 *       <li>START_END: Knockback split between start and end</li>
 *       <li>SPREAD: Knockback accumulated and applied once per tick</li>
 *     </ul>
 *   </li>
 *   <li><b>ONCE:</b> Knockback is applied all at once at the configured timing
 *     <ul>
 *       <li>START: Full knockback at start</li>
 *       <li>END: Full knockback at end</li>
 *       <li>START_END: Full knockback split between start and end</li>
 *       <li>SPREAD: Full knockback accumulated and applied once per tick</li>
 *     </ul>
 *   </li>
 * </ul>
 * 
 * <h2>Interaction with Knockback Timing</h2>
 * <p>
 * The {@code knockbackMethod} works in conjunction with {@code knockbackTiming}:
 * <ul>
 *   <li>SPREAD method with SPREAD timing: Knockback is accumulated per tick</li>
 *   <li>ONCE method with SPREAD timing: Full knockback accumulated and applied once per tick</li>
 * </ul>
 * </p>
 */
public class KnockbackMethodCommand {

    static {
        CommandComments.addComment("knockbackMethod", "Whether explosion knockback to entities is applied all at once or spread over time. Options: SPREAD, ONCE");
    }
    
    /**
     * Registers the knockbackMethod command with the command dispatcher.
     * 
     * @param ignoredBuildContext the command build context (unused for this command)
     * @return an argument builder for the knockbackMethod command
     */
    public static ArgumentBuilder<CommandSourceStack, ?> register(CommandBuildContext ignoredBuildContext) {
        return Commands.literal("knockbackMethod")
                .then(Commands.argument("value", StringArgumentType.word())
                        .suggests(SuggestionProviders::methodSuggestions)
                        .executes(context -> setValue(context, StringArgumentType.getString(context, "value"))))
                .executes(KnockbackMethodCommand::sendValueMessage);
    }

    /**
     * Sets the knockback method.
     * 
     * @param context the command context
     * @param value the knockback method as a string (case-insensitive)
     * @return the command execution result (1 for success, 0 for failure)
     */
    private static int setValue(CommandContext<CommandSourceStack> context, String value) {
        try {
            ModConfig.Method method = ModConfig.Method.valueOf(value.toUpperCase());
            ModConfig.setKnockbackMethod(method);
            sendValueMessage(context);
            return 1;
        } catch (IllegalArgumentException e) {
            context.getSource().sendFailure(Component.literal("/chunkedexplosions knockbackMethod <SPREAD | ONCE>"));
            return 0;
        }
    }

    /**
     * Sends a message showing the current knockback method.
     * 
     * @param context the command context
     * @return the command execution result (1 for success)
     */
    private static int sendValueMessage(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.literal("Knockback method: " + ModConfig.getKnockbackMethod()), true);
        return 1;
    }
}