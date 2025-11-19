package csdev.threads;

import csdev.Protocol;
import csdev.client.TcpClientMain;
import csdev.messages.*;
import csdev.server.ServerMain;
import csdev.threads.session.ClientSession;
import csdev.threads.session.TcpClientSession;
import csdev.utils.Logger;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Vector;
import java.util.concurrent.TimeUnit;

/**
 * <p>TCP server thread for handling client connections
 * @author cin-tie
 * @version 1.2
 */
public class TcpServerThread extends Thread {

    private TcpClientSession clientSession;

    public TcpServerThread(Socket s) throws IOException {
        this.clientSession = new TcpClientSession(s);
        this.setDaemon(true);
        logDebug("TCP Server thread created for: " + clientSession.getClientInfo());
    }

    public void run() {
        logDebug("TCP Client session started: " + clientSession.getClientInfo());

        try {
            clientSession.processMessages(this);
        } catch (Exception e) {
            if (!clientSession.isGracefulShutdown()) {
                logError("Unexpected error in TCP client session: " + e.getMessage());
            }
        } finally {
            clientSession.disconnect();
        }
    }

    public void gracefulDisconnect() {
        clientSession.gracefulDisconnect();
    }

    public String getUsername() {
        return clientSession.getUsername();
    }

    private void logDebug(String message) {
        System.out.println();
        Logger.logDebug(message);
        restorePrompt();
    }

    private void logError(String message) {
        System.out.println();
        Logger.logError(message);
        restorePrompt();
    }

    private void restorePrompt() {
        System.out.print("server> ");
        System.out.flush();
    }
}
