package csdev.threads.session;

import csdev.messages.Message;
import csdev.messages.MessageDisconnect;
import csdev.threads.UdpServerThread;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;

/**
 * <p>UDP client session implementation
 * @author cin-tie
 * @version 1.0
 */
public class UdpClientSession extends ClientSession{
    private InetAddress address;
    private int port;
    private UdpServerThread server;
    private long lastActivity;

    public  UdpClientSession(InetAddress address, int port, UdpServerThread udpServerThread) {
        super();
        this.address = address;
        this.port = port;
        this.server = udpServerThread;
        this.lastActivity = System.currentTimeMillis();
    }

    @Override
    public void sendMessage(Message msg) throws IOException{
        server.sendMessage(address, port, msg);
        updateActivity();
    }

    @Override
    public void disconnect(){
        if(!disconnected){
            disconnected = true;
            unregister();
            String clientKey = getClientKey();
            server.removeSession(clientKey);
            logInfo("UDP Session cleaned up: " + getClientInfo());
        }
    }

    @Override
    public void gracefulDisconnect(){
        gracefulShutdown = true;
        try {
            MessageDisconnect disconnectMsg = new MessageDisconnect("Server is shutting down");
            sendMessage(disconnectMsg);
        } catch (IOException e) {
            logDebug("Could not send UDP graceful disconnect message: " + e.getMessage());
        }
        disconnect();
    }

    public String readStream(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }

    public void registerUser(String username, String usernameFull) {
        register(username, usernameFull);
    }

    private void updateActivity(){
        this.lastActivity = System.currentTimeMillis();
    }

    public long getLastActivity(){
        return lastActivity;
    }

    private String getClientKey(){
        return address.getHostAddress() + ":" + port;
    }

    public String getClientInfo(){
        return getClientKey() + "(" + username + ")";
    }
}
