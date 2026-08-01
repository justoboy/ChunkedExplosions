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
 * Command to configure whether explosion sound volume is split when using multi-stage timing modes.
 * <p>
 * When {@code soundTiming} is set to START_END or SPREAD, this setting determines
 * whether the sound volume is split between phases or played at full volume.
 * </p>
 * 
 * <h2>Usage</h2>
 * <ul>
 *   <li>{@code /chunkedexplosions soundVolumeSplit} - Get current split setting</li>
 *   <li>{@code /chunkedexplosions soundVolumeSplit true} - Enable volume splitting</li>
 *   <li>{@code /chunkedexplosions soundVolumeSplit false} - Disable volume splitting</li>
 * </ul>
 * 
 * <h2>Volume Splitting Behavior</h2>
 * <ul>
 *   <li><b>Enabled (true):</b> Sound volume is split between phases
 *     <ul>
 *       <li>START_END: Volume is split 50/50 between start and end</li>
 *       <li>SPREAD: Volume is distributed across ticks during processing</li>
 *     </ul>
 *   </li>
 *   <li><b>Disabled (false):</b> Sound is played at full volume
 *     <ul>
 *       <li>START_END: Full volume at start, full volume at end</li>
 *       <li>SPREAD: Full volume accumulated and played once per tick</li>
 *     </ul>
 *   </li>
 * </ul>
 * 
 * <h2>Recommendations</h2>
 * <ul>
 *   <li>Enable splitting for quieter explosions during long-running events</li>
 *   <li>Disable splitting for louder, more impactful explosions</li>
 * </ul>
 */
public class SoundVolumeSplitCommand {

    static {
        CommandComments.addComment("soundVolumeSplit", "Whether the explosion sound volume is split when soundTiming is set to START_END or SPREAD. Options: true, false");
    }
    
    /**
     * Registers the soundVolumeSplit command with the command dispatcher.
     * 
     * @param ignoredBuildContext the command build context (unused for this command)
     * @return an argument builder for the soundVolumeSplit command
     */
    public static ArgumentBuilder<CommandSourceStack, ?> register(CommandBuildContext ignoredBuildContext) {
        return Commands.literal("soundVolumeSplit")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(context -> setValue(context, BoolArgumentType.getBool(context, "value"))))
                .executes(SoundVolumeSplitCommand::sendValueMessage);
    }

    /**
     * Sets whether sound volume should be split.
     * 
     * @param context the command context
     * @param value true to split volume, false for full volume
     * @return the command execution result (1 for success)
     */
    private static int setValue(CommandContext<CommandSourceStack> context, boolean value) {
        ModConfig.setSoundVolumeSplit(value);
        sendValueMessage(context);
        return 1;
    }

    /**
     * Sends a message showing the current sound volume split setting.
     * 
     * @param context the command context
     * @return the command execution result (1 for success)
     */
    private static int sendValueMessage(CommandContext<CommandSourceStack> context) {
        String message = ModConfig.getSoundVolumeSplit() ? "enabled" : "disabled";
        context.getSource().sendSuccess(() -> Component.literal("Sound volume split: " + message), true);
        return 1;
    }
}