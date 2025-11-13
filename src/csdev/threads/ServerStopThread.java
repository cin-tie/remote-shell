package csdev.threads;

import csdev.server.ServerMain;
import csdev.utils.Logger;

import java.io.IOException;
import java.util.Scanner;

/**
 * <p>Thread for handling server stop commands
 * @author cin-tie
 * @version 1.0
 */
public class ServerStopThread extends CommandThread {

    static final String cmd  = "q";
    static final String cmdL = "quit";
    static final String cmdStop  = "stop";
    static final String cmdStatus = "status";
    static final String cmdHelp = "help";

    Scanner fin;

    public ServerStopThread() {
        fin = new Scanner(System.in);
        ServerMain.setStopFlag(false);

        // Register commands handler
        putHandler(cmd, cmdL, new CmdHandler() {
            @Override
            public boolean onCommand(int[] errorCode) {
                return onCmdQuit();
            }
        });

        putHandler(cmdStop, cmdStop,  new CmdHandler() {
            @Override
            public boolean onCommand(int[] errorCode) {
                return onCmdQuit();
            }
        });

        putHandler(cmdStatus, cmdStatus, new CmdHandler() {
            @Override
            public boolean onCommand(int[] errorCode) { return onCmdStatus(); }
        });

        putHandler(cmdHelp, cmdHelp, new CmdHandler() {
            @Override
            public boolean onCommand(int[] errorCode) { return onCmdHelp(); }
        });
    }

    public boolean onCmdQuit() {
        Logger.logInfo("Stop command recieved - shutting down server...");
        ServerMain.setStopFlag(true);
        ServerMain.stopServer();
        return true;
    }

    public boolean onCmdStatus() {
        int userCount = ServerMain.getNumUsers();
        Logger.logInfo("Server status - Active users: " + userCount + ", Max users: " + ServerMain.MAX_USERS);
        String[] users = ServerMain.getUsers();
        if (users.length > 0) {
            Logger.logInfo("Connected users: " + String.join(", ", users));
            System.out.println("Active users: " + String.join(", ", users));
        } else {
            System.out.println("No active users");
        }
        System.out.println("Total connections: " + userCount + "/" + ServerMain.MAX_USERS);
        return false;
    }

    public boolean onCmdHelp() {
        System.out.println("\n=== Server Control Commands ===");
        System.out.println("q, quit, stop  - Stop the server gracefully");
        System.out.println("status         - Show server status and active users");
        System.out.println("help           - Show this help message");
        System.out.println("===============================\n");
        return false;
    }

    @Override
    public void run() {
        System.out.println("Server control thread started. Type 'help' for available commands.");
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        while (!ServerMain.getStopFlag()) {
            try {
                System.out.print("server> ");
                System.out.flush();
                if(fin.hasNextLine()) {
                    String line = fin.nextLine().trim();
                    if(!line.isEmpty()) {
                        boolean result = command(line);
                        if(result && (line.equals("q") || line.equals("quit") || line.equals("stop"))) {
                            break;
                        }
                    }
                }
                else{
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } catch (Exception e) {
                Logger.logError("Error in server control thread: " + e.getMessage());
                if(!ServerMain.getStopFlag()) {
                    System.out.print("server> ");
                    System.out.flush();
                }
            }
        }

        Logger.logInfo("Server control thread stopped.");
    }
}
