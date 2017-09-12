package org.sbt.devops;

/**
 * Created by SBT-Kamalov-AN on 21.08.2017.
 */
public class Commands {
    private static final String commandInitChar = "/";
    /// Help command
    public static final String help = commandInitChar + "help";
    /// Upload command
    public static final String uploadCommand = commandInitChar + "upload";
    /// Start command
    public static final String startCommand = commandInitChar + "start";
    /// Cancel command
    public static final String cancelCommand = commandInitChar + "cancel";
    /// Delete command
    public static final String deleteCommand = commandInitChar + "delete";
    /// List command
    public static final String listCommand = commandInitChar + "list";

    public static final String STOPCOMMAND = commandInitChar + "stop";

    public static final String restartCommand = commandInitChar + "restart";
}
