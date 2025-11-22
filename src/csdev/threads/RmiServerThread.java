package csdev.threads;

import csdev.threads.session.RmiClientSession;

import java.rmi.RemoteException;
import java.rmi.registry.Registry;
import java.util.concurrent.ConcurrentHashMap;

public class RmiServerThread extends Thread implements RemoteShellService{

    private Registry registry;
    private boolean running = true;
    private ConcurrentHashMap<String, RmiClientSession> sessions = new ConcurrentHashMap<>();

    public RmiServerThread() throws RemoteException {
        super();
        this.setDaemon(true);
        this.setName("RmiServerThread");
    }
}
