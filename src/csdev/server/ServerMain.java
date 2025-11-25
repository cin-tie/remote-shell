package csdev.server;

import csdev.Protocol;
import csdev.threads.RmiServerThread;
import csdev.threads.ServerStopThread;
import csdev.threads.TcpServerThread;
import csdev.threads.UdpServerThread;
import csdev.threads.session.ClientSession;
import csdev.threads.session.UdpClientSession;
import csdev.utils.Logger;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * <p>Main class of server application for remote shell
 * <p>Realized in console
 * <br>Use arguments: [password]
 * @author cin-tie
 * @version 1.4
 */
public class ServerMain {

    public static final int MAX_USERS = 50;
    private static ServerSocket tcpServerSocket;
    private static UdpServerThread udpServerThread;
    private static RmiServerThread rmiServerThread;
    private static String serverPassword;
    private static boolean passwordRequired = false;
    private static Object syncFlags = new Object();
    private static boolean stopFlag = false;
    private static Object syncUsers = new Object();
    private static TreeMap<String, TcpServerThread> users = new TreeMap<>();


    public static void main(String[] args) {
        Logger.logServer("Starting Remote Shell server...");

        if(args.length > 1) {
            Logger.logError("Invalid number of arguments\nUse: [password]");
            waitKeyToStop();
            return;
        }

        serverPassword = args.length == 1 ? args[0] : "";
        System.out.println("Server Password: " + serverPassword);
        passwordRequired = !serverPassword.isEmpty();

        Logger.logServer("Password authentication: " + (passwordRequired ? "ENABLED" : "DISABLED"));

        try(ServerSocket serv = new ServerSocket(Protocol.PORT)) {

            tcpServerSocket = serv;
            try {
                udpServerThread = new UdpServerThread();
                udpServerThread.start();
                Logger.logServer("UDP Server started on port " + Protocol.PORT);
            } catch (IOException e) {
                Logger.logError("Failed to start UDP server: " + e.getMessage());
            }

            try {
                rmiServerThread = new RmiServerThread();
                rmiServerThread.start();
                Logger.logServer("RMI Server started on port " + Protocol.RMI_PORT);
            } catch (IOException e) {
                Logger.logError("Failed to start RMI server: " + e.getMessage());
            }

            Logger.logServer("TCP Server initialized on port " + tcpServerSocket.getLocalPort());

            ServerStopThread stopThread = new ServerStopThread();
            stopThread.start();
            Logger.logServer("Stop thread started");

            while (true) {
                Socket socket = accept(serv);
                if (socket != null) {
                    if (ServerMain.getNumUsers() < ServerMain.MAX_USERS) {
                        logConnection("TCP: " + socket.getInetAddress().getHostName() + " connected");
                        TcpServerThread server = new TcpServerThread(socket);
                        server.start();
                    } else {
                        logConnection("TCP: " + socket.getInetAddress().getHostName() + " connection rejected - max users reached");
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

    static void waitKeyToStop(){
        Logger.logInfo("Press enter to stop...");
        try {
            System.in.read();
        } catch (Exception e) {
        }
    }

    public static boolean isPasswordRequired(){
        return passwordRequired;
    }

    public static String getServerPassword(){
        return serverPassword;
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

    private static void stopAllServers() {
        try {
            if(tcpServerSocket != null && !tcpServerSocket.isClosed()){
                tcpServerSocket.close();
            }
        } catch (IOException e) {
            Logger.logError("Error closing TCP server socket: " + e.getMessage());
        }

        if(udpServerThread != null && udpServerThread.isAlive()){
            udpServerThread.stopServer();
            try {
                udpServerThread.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Logger.logWarning("Interrupted while waiting for UDP server to stop");
            }
        }

        if(rmiServerThread != null && rmiServerThread.isAlive()){
            rmiServerThread.stopServer();
            try {
                rmiServerThread.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Logger.logWarning("Interrupted while waiting for RMI server to stop");
            }
        }
    }

    private static void stopAllUsers() {
        String[] users = getUsers();
        Logger.logInfo("Disconnecting all users: " + users.length + " active sessions");
        for (String user : users) {
            TcpServerThread ut = getUser(user);
            if (ut != null) {
                ut.gracefulDisconnect();
            }
        }
    }

    private static void waitForUsersToDisconnect() {
        int maxWaitTime = 10000;
        int waitInterval = 100;
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
        stopAllServers();
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

    public static TcpServerThread getUser(String user) {
        synchronized (ServerMain.syncUsers) {
            return ServerMain.users.get(user);
        }
    }

    public static TcpServerThread registerUser(String username, TcpServerThread user) {
        synchronized (ServerMain.syncUsers) {
            TcpServerThread old = ServerMain.users.get(username);
            if(old == null) {
                ServerMain.users.put(username, user);
                logInfo("Registered user: " + username + " (Total: " + users.size() + ")");
            }
            return old;
        }
    }

    public static TcpServerThread setUser(String username, TcpServerThread user) {
        synchronized (ServerMain.syncUsers) {
            TcpServerThread res = ServerMain.users.put(username, user);
            if (user == null) {
                ServerMain.users.remove(username);
                logInfo("User unregistered: " + username + " (Remaining: " + users.size() + ")");
            }
            return res;
        }
    }

    public static String[] getUsers() {
        synchronized (ServerMain.syncUsers) {
            String[] tcp = ServerMain.users.keySet().toArray(new String[0]);
            String[] udp = udpServerThread.getUsers();
            String[] res = Stream.concat(Arrays.stream(tcp), Arrays.stream(udp)).toArray(String[]::new);
            return res;
        }
    }

    public static int getNumUsers() {
        synchronized (ServerMain.syncUsers) {
            return ServerMain.users.keySet().size() + udpServerThread.getNumUsers();
        }
    }

    private static void logInfo(String message) {
        System.out.print(" ");
        Logger.logInfo(message);
        restorePrompt();
    }

    private static void logConnection(String message) {
        System.out.print(" ");
        Logger.logServer(message);
        restorePrompt();
    }

    private static void restorePrompt() {
        System.out.print("server> ");
        System.out.flush();
    }
}


