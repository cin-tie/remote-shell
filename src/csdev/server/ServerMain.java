package csdev.server;

import csdev.Protocol;
import csdev.threads.ServerStopThread;
import csdev.threads.ServerThread;
import csdev.utils.Logger;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.TreeMap;

/**
 * <p>Main class of server application for remote shell
 * <p>Realized in console
 * @author cin-tie
 * @version 1.0
 */
public class ServerMain {

    public static final int MAX_USERS = 50;
    private static ServerSocket serverSocket;
    private static Object syncFlags = new Object();
    private static boolean stopFlag = false;
    private static Object syncUsers = new Object();
    private static TreeMap<String, ServerThread> users = new TreeMap<String, ServerThread>();

    public static void main(String[] args) {
        Logger.logServer("Starting remote-shell server...");

        try(ServerSocket serv = new ServerSocket(Protocol.PORT)) {

            serverSocket = serv;
            Logger.logServer("Server initialized on port " + serverSocket.getLocalPort());

            ServerStopThread stopThread = new ServerStopThread();
            stopThread.start();
            Logger.logServer("Stop thread started");

            while (true) {
                Socket socket = accept(serv);
                if (socket != null) {
                    if (ServerMain.getNumUsers() < ServerMain.MAX_USERS) {
                        Logger.logServer(socket.getInetAddress().getHostName() + " connected");
                        ServerThread server = new ServerThread(socket);
                        server.start();
                    } else {
                        Logger.logWarning(socket.getInetAddress().getHostName() + " connection rejected - max users reached");
                        socket.close();
                    }
                }
                if (ServerMain.getStopFlag()) {
                    break;
                }
            }

        } catch (IOException e){
            Logger.logError("Server error: " + e.getMessage());
        } finally {
            stopAllUsers();
            Logger.logServer("Server stopped");
        }

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Logger.logDebug("Shutdown sleep interrupted");
        }
    }

    public static Socket accept(ServerSocket serverSocket) {
        assert(serverSocket != null);
        try {
            serverSocket.setSoTimeout(1000);
            Socket socket = serverSocket.accept();
            return socket;
        } catch (IOException e) {

        }
        return null;
    }

    private static void stopAllUsers() {
        String[] users = getUsers();
        Logger.logInfo("Disconnecting all users: " + users.length + " active sessions");
        for (String user : users) {
            ServerThread ut = getUser(user);
            if (ut != null) {
                ut.disconnect();
            }
        }
    }

    public static void stopServer(){
        setStopFlag(true);
        try {
            if(serverSocket != null && !serverSocket.isClosed()){
                serverSocket.close();
            }
        } catch (IOException e) {
            Logger.logError("Error closing server socket: " + e.getMessage());
        }
    }

    public static boolean getStopFlag() {
        synchronized (ServerMain.syncFlags) {
            return stopFlag;
        }
    }

    public static void setStopFlag(boolean stopFlag) {
        synchronized (ServerMain.syncFlags) {
            ServerMain.stopFlag = stopFlag;
        }
    }

    public static ServerThread getUser(String user) {
        synchronized (ServerMain.syncUsers) {
            return ServerMain.users.get(user);
        }
    }

    public static ServerThread registerUser(String userNick, ServerThread user) {
        synchronized (ServerMain.syncUsers) {
            ServerThread old = ServerMain.users.get(userNick);
            if(old == null) {
                ServerMain.users.put(userNick, user);
                Logger.logInfo("Registered user: " + userNick + "(Total: " + users.size() + ")");
            }
            return old;
        }
    }

    public static ServerThread setUser(String userNic, ServerThread user) {
        synchronized (ServerMain.syncUsers) {
            ServerThread res = ServerMain.users.put(userNic, user);
            if (user == null) {
                ServerMain.users.remove(userNic);
                Logger.logInfo("User unregistered: " + userNic + " (Remaining: " + users.size() + ")");
            }
            return res;
        }
    }

    public static String[] getUsers() {
        synchronized (ServerMain.syncUsers) {
            return ServerMain.users.keySet().toArray(new String[0]);
        }
    }

    public static int getNumUsers() {
        synchronized (ServerMain.syncUsers) {
            return ServerMain.users.keySet().size();
        }
    }
}
