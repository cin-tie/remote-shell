package csdev.client;

import csdev.utils.Logger;

/**
 * <p>Main class of client application using RMI protocol
 * <p>Remote shell client for MacOS/Linux/Unix servers
 * <br>Use arguments: userNic userFullName host [password]
 * @author cin-tie
 * @version 1.0
 */
public class RmiClientMain {

    public static void main(String[] args) {
        Logger.logClient("Starting Remote Shell RMI Client...");

        if(args.length < 3 || args.length > 4) {
            Logger.logError("Invalid number of arguments\nUse: nic name host [password]");
            Logger.logError("Examples:");
            Logger.logError("       john \"John Doe\" localhost");
            Logger.logError("       john \"John Doe\" localhost mypassword");
            waitKeyToStop();
            return;
        }

        String password = args.length == 4 ? args[3] : "";
        String host = args[2];
        Logger.logInfo("Connecting to " + host + " as " + args[0] + " (" + args[1] + ")");
        if (password.isEmpty()) {
            Logger.logWarning("No password provided - connection may fail if server requires authentication");
        }

        try {
            Logger.logClient("RMI Client initialized");
            session(args[0], args[1], password, host);
        } catch (Exception e) {
            Logger.logError("RMI Connection failed: " + e.getMessage());
        } finally {
            Logger.logClient("RMI Client shutdown");
        }
    }

    static void waitKeyToStop() {
        Logger.logInfo("Press enter to stop...");
        try {
            System.in.read();
        } catch (Exception e) {
        }
    }

    static class RmiSession{
        boolean connected = false;
        String username = null;
        String usernameFull = null;
        String password = "";
        String currentDirectory = "";
        RemoteShellService remoteShellService = null;

        RmiSession(String username, String usernameFull, String password){
            this.username = username;
            this.usernameFull = usernameFull;
            this.password = password;
        }
    }

    static void session(String username, String password, String host) {

    }
}
