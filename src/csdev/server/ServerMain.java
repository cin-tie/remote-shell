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
 * @version 1.2
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
                        logConnection(socket.getInetAddress().getHostName() + " connected");
                        ServerThread server = new ServerThread(socket);
                        server.start();
                    } else {
                        logConnection(socket.getInetAddress().getHostName() + " connection rejected - max users reached");
                        socket.close();
                    }
                }
                if (ServerMain.getStopFlag()) {
                    break;
                }
            }

        } catch (IOException e){
            if (!getStopFlag()) {
                Logger.logError("Server error: " + e.getMessage());
            }
        } finally {
            stopAllUsers();
            waitForUsersToDisconnect();
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
                ut.gracefulDisconnect();
            }
        }

    }

    private static void waitForUsersToDisconnect() {
        int maxWaitTime = 10000; // 10 секунд максимум
        int waitInterval = 100; // Проверяем каждые 100мс
        int totalWaited = 0;

        while (getNumUsers() > 0 && totalWaited < maxWaitTime) {
            try {
                Thread.sleep(waitInterval);
                totalWaited += waitInterval;
                Logger.logDebug("Waiting for users to disconnect... " + getNumUsers() + " remaining");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        if (getNumUsers() > 0) {
            Logger.logWarning("Forcefully disconnecting " + getNumUsers() + " remaining users");
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

    public static ServerThread registerUser(String username, ServerThread user) {
        synchronized (ServerMain.syncUsers) {
            ServerThread old = ServerMain.users.get(username);
            if(old == null) {
                ServerMain.users.put(username, user);

                logInfo("Registered user: " + username + "(Total: " + users.size() + ")");
            }
            return old;
        }
    }

    public static ServerThread setUser(String username, ServerThread user) {
        synchronized (ServerMain.syncUsers) {
            ServerThread res = ServerMain.users.put(username, user);
            if (user == null) {
                ServerMain.users.remove(username);
                logInfo("User unregistered: " + username + " (Remaining: " + users.size() + ")");
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

    private static void logInfo(String message) {
        System.out.println();
        Logger.logInfo(message);
        restorePrompt();
    }

    private static void logConnection(String message) {
        System.out.println();
        Logger.logServer(message);
        restorePrompt();
    }


    private static void restorePrompt() {
        System.out.print("server> ");
        System.out.flush();
    }
}
