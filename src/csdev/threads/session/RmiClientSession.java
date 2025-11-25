package csdev.threads.session;

import csdev.messages.Message;
import csdev.messages.MessageDisconnect;

import java.io.IOException;
import java.rmi.RemoteException;

/**
 * <p>RMI client session implementation
 * @author cin-tie
 * @version 1.0
 */
public class RmiClientSession extends ClientSession {

    public RmiClientSession(){
        super();
    }

    @Override
    public void sendMessage(Message msg){
        // Not used
    }

    @Override
    public void disconnect(){
        if(!disconnected){
            disconnected = true;
            unregister();
            logInfo("Rmi Session cleaned up: " + getClientInfo());
        }
    }

    @Override
    public void gracefulDisconnect(){
        gracefulShutdown = true;
        disconnect();
    }

    public void registerUser(String username, String usernameFull){
        register(username, usernameFull);
    }

    public String getClientInfo() {
        return "RMI:" + username;
    }
}
