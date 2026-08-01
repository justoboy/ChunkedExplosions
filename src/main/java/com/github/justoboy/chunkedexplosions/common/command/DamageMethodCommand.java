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
 * Command to configure how explosion damage is applied to entities.
 * <p>
 * This controls whether damage is applied all at once or spread over time
 * during explosion processing. The behavior depends on the {@code damageTiming}
 * setting.
 * </p>
 * 
 * <h2>Usage</h2>
 * <ul>
 *   <li>{@code /chunkedexplosions damageMethod} - Get current damage method</li>
 *   <li>{@code /chunkedexplosions damageMethod <method>} - Set damage method</li>
 * </ul>
 * 
 * <h2>Damage Methods</h2>
 * <ul>
 *   <li><b>SPREAD:</b> Damage is distributed over time based on the timing mode
 *     <ul>
 *       <li>START: Full damage applied at start</li>
 *       <li>END: Full damage applied at end</li>
 *       <li>START_END: Damage split between start and end</li>
 *       <li>SPREAD: Damage accumulated and applied once per tick</li>
 *     </ul>
 *   </li>
 *   <li><b>ONCE:</b> Damage is applied all at once at the configured timing
 *     <ul>
 *       <li>START: Full damage at start</li>
 *       <li>END: Full damage at end</li>
 *       <li>START_END: Full damage split between start and end</li>
 *       <li>SPREAD: Full damage accumulated and applied once per tick</li>
 *     </ul>
 *   </li>
 * </ul>
 * 
 * <h2>Interaction with Damage Timing</h2>
 * <p>
 * The {@code damageMethod} works in conjunction with {@code damageTiming}:
 * <ul>
 *   <li>SPREAD method with SPREAD timing: Damage is accumulated per tick</li>
 *   <li>ONCE method with SPREAD timing: Full damage accumulated and applied once per tick</li>
 * </ul>
 * </p>
 */
public class DamageMethodCommand {

    static {
        CommandComments.addComment("damageMethod", "Whether explosion damage to entities is applied all at once or spread over time. Options: SPREAD, ONCE");
    }
    
    /**
     * Registers the damageMethod command with the command dispatcher.
     * 
     * @param ignoredBuildContext the command build context (unused for this command)
     * @return an argument builder for the damageMethod command
     */
    public static ArgumentBuilder<CommandSourceStack, ?> register(CommandBuildContext ignoredBuildContext) {
        return Commands.literal("damageMethod")
                .then(Commands.argument("value", StringArgumentType.word())
                        .suggests(SuggestionProviders::methodSuggestions)
                        .executes(context -> setValue(context, StringArgumentType.getString(context, "value"))))
                .executes(DamageMethodCommand::sendValueMessage);
    }

    /**
     * Sets the damage method.
     * 
     * @param context the command context
     * @param value the damage method as a string (case-insensitive)
     * @return the command execution result (1 for success, 0 for failure)
     */
    private static int setValue(CommandContext<CommandSourceStack> context, String value) {
        try {
            ModConfig.Method method = ModConfig.Method.valueOf(value.toUpperCase());
            ModConfig.setDamageMethod(method);
            sendValueMessage(context);
            return 1;
        } catch (IllegalArgumentException e) {
            context.getSource().sendFailure(Component.literal("/chunkedexplosions damageMethod <SPREAD | ONCE>"));
            return 0;
        }
    }

    /**
     * Sends a message showing the current damage method.
     * 
     * @param context the command context
     * @return the command execution result (1 for success)
     */
    private static int sendValueMessage(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.literal("Damage method: " + ModConfig.getDamageMethod()), true);
        return 1;
    }
}