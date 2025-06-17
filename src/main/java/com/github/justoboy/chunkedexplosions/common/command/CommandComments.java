package com.github.justoboy.chunkedexplosions.common.command;

import java.util.HashMap;
import java.util.Map;

public class CommandComments {

    static final Map<String, String> COMMAND_COMMENTS = new HashMap<>();

    public static void addComment(String command, String comment) {
        COMMAND_COMMENTS.put(command, comment);
    }

    public static String getComment(String command) {
        return COMMAND_COMMENTS.getOrDefault(command, "No description available.");
    }
}