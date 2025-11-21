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
    MessageConnectResult connect(MessageConnect msg) throws RemoteException;
    void disconnect(MessageDisconnect msg) throws RemoteException;

    MessageResult processCommand(Message msg)  throws RemoteException;
}
