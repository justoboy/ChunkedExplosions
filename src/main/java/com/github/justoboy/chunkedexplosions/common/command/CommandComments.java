package com.github.justoboy.chunkedexplosions.common.command;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Manages command descriptions for the chunked explosions mod.
 * <p>
 * This class maintains a map of command names to their descriptions,
 * which is used by the help command to display available commands.
 * Command descriptions are registered in static initializer blocks
 * of each command class.
 * </p>
 * 
 * <h2>Usage</h2>
 * <p>
 * Each command class should register its description in a static block:
 * </p>
 * <pre>{@code
 * static {
 *     CommandComments.addComment("commandName", "Description of what this command does.");
 * }
 * }</pre>
 */
public class CommandComments {

    /** Map of command names to their descriptions */
    static final Map<String, String> COMMAND_COMMENTS = new LinkedHashMap<>();

    /**
     * Adds or updates a command description.
     * 
     * @param command the command name (without the main command prefix)
     * @param comment the description of what the command does
     */
    public static void addComment(String command, String comment) {
        COMMAND_COMMENTS.put(command, comment);
    }

    /**
     * Gets the description for a command.
     * 
     * @param command the command name
     * @return the command description, or a default message if not found
     */
    public static String getComment(String command) {
        return COMMAND_COMMENTS.getOrDefault(command, "No description available.");
    }
}