package csdev.threads;

import csdev.messages.*;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * <p>RMI Remote interface for Remote Shell Service
 * @author cin-tie
 * @version 1.0
 */
public interface RemoteShellService extends Remote {
    MessageConnectResult connect(MessageConnect msg, String sessionId) throws RemoteException;
    void disconnect(MessageDisconnect msg, String sessionId) throws RemoteException;

    MessageResult processCommand(Message msg, String sessionId)  throws RemoteException;
}
