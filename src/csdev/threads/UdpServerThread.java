package csdev.threads;

import csdev.Protocol;
import csdev.utils.Logger;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <p>Udp server thread for handling client connections
 * @author cin-tie
 * @version 1.0
 */
public class UdpServerThread extends Thread {

    private DatagramSocket socket;
    private volatile boolean running = true;
    private byte[] buffer = new byte[1024];

    private ConcurrentHashMap<String, UdpClientSession> sessions = new ConcurrentHashMap<>();

    private String username = null;
    private String usernameFull;
    private volatile boolean gracefulShutdown = false;
    private String currentDirectory;

    private boolean disconnected = false;

    public UdpServerThread() throws IOException {
        this.socket = new DatagramSocket(Protocol.PORT);
        this.socket.setSoTimeout(1000);
        this.setDaemon(true);
        logInfo("UDP server thread created ");
    }

    private void logInfo(String message) {
        System.out.println();
        Logger.logInfo(message);
        restorePrompt();
    }

    private void logWarning(String message) {
        System.out.println();
        Logger.logWarning(message);
        restorePrompt();
    }

    private void logError(String message) {
        System.out.println();
        Logger.logError(message);
        restorePrompt();
    }
    private void logDebug(String message) {
        System.out.println();
        Logger.logDebug(message);
        restorePrompt();
    }

    private void restorePrompt() {
        System.out.print("server> ");
        System.out.flush();
    }
}