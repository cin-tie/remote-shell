package csdev.server;

import csdev.Protocol;
import csdev.threads.ServerStopThread;
import csdev.utils.Logger;

import java.io.IOException;
import java.net.ServerSocket;

/**
 * <p>Main class of server application for remote shell
 * <p>Realized in console
 * @author cin-tie
 * @version 1.0
 */
public class ServerMain {

    private static int MAX_USERS = 50;
    private static ServerSocket serverSocket;

    public static void main(String[] args) {
        Logger.logServer("Starting remote-shell server...");

        try(ServerSocket serv = new ServerSocket(Protocol.PORT)) {

            serverSocket = serv;
            Logger.logServer("Server initialized on port " + serverSocket.getLocalPort());

            ServerStopThread stopThread = new ServerStopThread();

        } catch (IOException e){
            Logger.logError("Server error: " + e.getMessage());
        } finally {

        }
    }
}
