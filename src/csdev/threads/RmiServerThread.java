package csdev.threads;

import java.rmi.registry.Registry;
import java.util.concurrent.ConcurrentHashMap;

public class RmiServerThread extends Thread implements RemoteShellService{

    private Registry registry;
    private boolean running = true;
    private ConcurrentHashMap<String, RmiClientSession> sessions = new ConcurrentHashMap<>();
}
