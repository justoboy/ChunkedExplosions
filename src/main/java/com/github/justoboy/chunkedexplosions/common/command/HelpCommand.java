package com.github.justoboy.chunkedexplosions.common.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Provides help functionality for chunked explosions commands.
 * <p>
 * This command displays a list of all available commands with their
 * descriptions. When a command name is provided as an argument, it
 * shows detailed information about that specific command.
 * Help details are registered per-command via {@link HelpDetails#register}.
 * </p>
 * 
 * <h2>Usage</h2>
 * <ul>
 *   <li>{@code /chunkedexplosions help} - List all commands with brief descriptions</li>
 *   <li>{@code /chunkedexplosions help <command>} - Show detailed help for a specific command</li>
 * </ul>
 * 
 * <h2>Examples</h2>
 * <pre>{@code
 * /chunkedexplosions help                    # List all commands
 * /chunkedexplosions help enable             # Show detailed help for enable command
 * /chunkedexplosions help explosionsPerTick  # Show detailed help for explosionsPerTick command
 * }</pre>
 */
public class HelpCommand {

    static {
        CommandComments.addComment("help", "Displays available commands and their descriptions. Use '/chunkedexplosions help <command>' for detailed help on a specific command.");
        
        // Register timing/method command details
        registerTimingDetails();
    }

    private static void registerTimingDetails() {
        // damageTiming
        HelpDetails.register("damageTiming", new HelpDetails(List.of(
            HelpDetails.Section.builder()
                .title("Timing Options:")
                .line("  START     - Damage applied when explosion begins")
                .line("  END       - Damage applied when explosion finishes")
                .line("  START_END - Damage split between start and end")
                .line("  SPREAD    - Damage accumulated and applied per tick")
                .build()
        )));

        // damageMethod
        HelpDetails.register("damageMethod", new HelpDetails(List.of(
            HelpDetails.Section.builder()
                .title("Method Options:")
                .line("  SPREAD - Damage distributed over time based on timing")
                .line("  ONCE   - Damage applied all at once at configured timing")
                .build()
        )));

        // soundTiming
        HelpDetails.register("soundTiming", new HelpDetails(List.of(
            HelpDetails.Section.builder()
                .title("Timing Options:")
                .line("  START     - Sound played when explosion begins")
                .line("  END       - Sound played when explosion finishes")
                .line("  START_END - Sound split between start and end")
                .line("  SPREAD    - Sound accumulated and played per tick")
                .build()
        )));

        // soundVolumeSplit - boolean option, not timing
        HelpDetails.register("soundVolumeSplit", new HelpDetails(List.of(
            HelpDetails.Section.builder()
                .title("Volume Split Options:")
                .line("  true  - Volume is split between phases")
                .line("        START_END: Volume split 50/50 between start and end")
                .line("        SPREAD: Volume distributed across ticks")
                .line("  false - Full volume played at each phase")
                .line("        START_END: Full volume at start, full volume at end")
                .line("        SPREAD: Full volume accumulated and played once per tick")
                .build()
        )));

        // particleTiming
        HelpDetails.register("particleTiming", new HelpDetails(List.of(
            HelpDetails.Section.builder()
                .title("Timing Options:")
                .line("  START     - Particles shown when explosion begins")
                .line("  END       - Particles shown when explosion finishes")
                .line("  START_END - Particles split between start and end")
                .line("  SPREAD    - Particles accumulated and shown per tick")
                .build()
        )));

        // knockbackTiming
        HelpDetails.register("knockbackTiming", new HelpDetails(List.of(
            HelpDetails.Section.builder()
                .title("Timing Options:")
                .line("  START     - Knockback applied when explosion begins")
                .line("  END       - Knockback applied when explosion finishes")
                .line("  START_END - Knockback split between start and end")
                .line("  SPREAD    - Knockback accumulated and applied per tick")
                .build()
        )));

        // knockbackMethod
        HelpDetails.register("knockbackMethod", new HelpDetails(List.of(
            HelpDetails.Section.builder()
                .title("Method Options:")
                .line("  SPREAD - Knockback distributed over time based on timing")
                .line("  ONCE   - Knockback applied all at once at configured timing")
                .build()
        )));
    }
    
    /**
     * Registers the help command with the command dispatcher.
     * 
     * @param ignoredBuildContext the command build context (unused for this command)
     * @return an argument builder for the help command
     */
    public static ArgumentBuilder<CommandSourceStack, ?> register(CommandBuildContext ignoredBuildContext) {
        return Commands.literal("help")
                .then(Commands.argument("command", StringArgumentType.word())
                        .suggests(SuggestionProviders::commandSuggestions)
                        .executes(context -> sendDetailedHelp(context, StringArgumentType.getString(context, "command"))))
                .executes(HelpCommand::sendHelpMessage);
    }

    /**
     * Sends a help message listing all available commands with their descriptions.
     * 
     * @param context the command context containing the source
     * @return the command execution result (1 for success)
     */
    private static int sendHelpMessage(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.literal("=== Chunked Explosions Commands ==="), false);
        context.getSource().sendSuccess(() -> Component.literal(""), false);
        context.getSource().sendSuccess(() -> Component.literal("Use '/chunkedexplosions help <command>' for detailed help on a specific command."), false);
        context.getSource().sendSuccess(() -> Component.literal(""), false);

        CommandComments.COMMAND_COMMENTS.forEach((command, comment) ->
                context.getSource().sendSuccess(() -> Component.literal("/chunkedexplosions " + command + ": " + comment), false)
        );

        return 1;
    }

    /**
     * Sends detailed help for a specific command.
     * 
     * @param context the command context containing the source
     * @param commandName the name of the command to show detailed help for
     * @return the command execution result (1 for success, 0 if command not found)
     */
    private static int sendDetailedHelp(CommandContext<CommandSourceStack> context, String commandName) {
        if (!CommandComments.COMMAND_COMMENTS.containsKey(commandName)) {
            context.getSource().sendFailure(Component.literal("Unknown command: '" + commandName + "'. Use '/chunkedexplosions help' to list all commands."));
            return 0;
        }

        context.getSource().sendSuccess(() -> Component.literal("=== /chunkedexplosions " + commandName + " ==="), false);
        context.getSource().sendSuccess(() -> Component.literal(""), false);
        
        HelpDetails details = HelpDetails.get(commandName);
        if (details != null) {
            details.sendUsage(context, commandName);
            context.getSource().sendSuccess(() -> Component.literal(""), false);
            context.getSource().sendSuccess(() -> Component.literal("Description: " + CommandComments.getComment(commandName)), false);
            
            // Send registered detail sections
            for (HelpDetails.Section section : details.getSections()) {
                context.getSource().sendSuccess(() -> Component.literal(""), false);
                context.getSource().sendSuccess(() -> Component.literal(section.title()), false);
                for (String line : section.lines()) {
                    context.getSource().sendSuccess(() -> Component.literal(line), false);
                }
            }
        } else {
            context.getSource().sendSuccess(() -> Component.literal("Usage:"), false);
            context.getSource().sendSuccess(() -> Component.literal("  /chunkedexplosions " + commandName), false);
            context.getSource().sendSuccess(() -> Component.literal(""), false);
            context.getSource().sendSuccess(() -> Component.literal("Description: " + CommandComments.getComment(commandName)), false);
        }
        
        return 1;
    }

    /**
     * Holds help details for a single command. Commands register their details
     * via {@link #register(String, HelpDetails)} in a static block.
     * <p>
     * This class is extensible — new commands can add their own detail sections
     * without modifying HelpCommand.
     * </p>
     */
    static final class HelpDetails {
        
        /** Registered help details keyed by command name */
        private static final Map<String, HelpDetails> REGISTRY = new ConcurrentHashMap<>();

        private final List<Section> sections;

        /**
         * Creates help details with the given sections.
         * 
         * @param sections detail sections to include in the help output
         */
        private HelpDetails(List<Section> sections) {
            this.sections = Collections.unmodifiableList(new ArrayList<>(sections));
        }

        /**
         * Registers help details for a command. Called by each command class in a static block.
         * 
         * @param commandName the command name
         * @param details the help details for this command
         */
        static void register(String commandName, HelpDetails details) {
            REGISTRY.put(commandName, details);
        }

        /**
         * Gets the registered help details for a command.
         * 
         * @param commandName the command name
         * @return the help details, or null if not registered
         */
        static HelpDetails get(String commandName) {
            return REGISTRY.get(commandName);
        }

        /**
         * Sends the usage line for this command.
         * 
         * @param context the command context
         * @param commandName the command name
         */
        void sendUsage(CommandContext<CommandSourceStack> context, String commandName) {
            context.getSource().sendSuccess(() -> Component.literal("Usage:"), false);
            context.getSource().sendSuccess(() -> Component.literal("  /chunkedexplosions " + commandName), false);
        }

        /**
         * Gets the registered detail sections.
         * 
         * @return an unmodifiable list of sections
         */
        List<Section> getSections() {
            return sections;
        }

        /**
         * A section of help text with a title and lines.
         * 
         * @param title the section title
         * @param lines the lines in this section
         */
        record Section(String title, List<String> lines) {
            /**
             * Creates a section builder.
             * 
             * @return a new builder for this section
             */
            public static Builder builder() {
                return new Builder();
            }

            /**
             * Builder for creating Section instances.
             */
            static class Builder {
                private String title;
                private final List<String> lines = new ArrayList<>();

                /**
                 * Sets the section title.
                 * 
                 * @param title the section title
                 * @return this builder
                 */
                public Builder title(String title) {
                    this.title = title;
                    return this;
                }

                /**
                 * Adds a line to the section.
                 * 
                 * @param line the line to add
                 * @return this builder
                 */
                public Builder line(String line) {
                    lines.add(line);
                    return this;
                }

                /**
                 * Builds the section.
                 * 
                 * @return the built section
                 */
                public Section build() {
                    return new Section(title, Collections.unmodifiableList(new ArrayList<>(lines)));
                }
            }
        }
    }
}
