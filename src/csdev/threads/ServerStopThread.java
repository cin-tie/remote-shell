package csdev.threads;

import csdev.server.ServerMain;

import java.util.Scanner;

/**
 * <p>Thread for handling server stop commands
 * @author cin-tie
 * @version 1.0
 */
public class ServerStopThread extends CommandThread {

    static final String cmd = "q";
    static final String cmdL = "quit";
    static final String cmdStop = "stop";
    static final String cmdStatus = "status";

    Scanner fin;

    public ServerStopThread() {
        fin = new Scanner(System.in);
        ServerMain.setS
    }
}
