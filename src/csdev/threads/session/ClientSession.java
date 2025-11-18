package csdev.threads.session;

import csdev.messages.Message;
import csdev.server.ServerMain;
import csdev.utils.Logger;

import java.io.IOException;

/**
 * <p>Base client session class
 * @author cin-tie
 * @version 1.0
 */
public abstract class ClientSession {
    protected String username = null;
    protected String usernameFull;
    protected String currentDirectory;
    protected volatile boolean gracefulShutdown = false;
    protected boolean disconnected = false;

    public ClientSession(){
        this.currentDirectory = System.getProperty("user.dir");
    }

    public abstract void sendMessage(Message msg) throws IOException;
    public abstract void disconnect();
    public abstract void gracefulDisconnect();

    public boolean isConnected() {
        return !disconnected;
    }

    public String getUsername() {
        return username;
    }

    public String getUsernameFull() {
        return usernameFull;
    }

    public String getCurrentDirectory() {
        return currentDirectory;
    }

    public void setCurrentDirectory(String directory) {
        this.currentDirectory = directory;
    }

    protected void register(String username, String usernameFull) {
        ClientSession old = ServerMain.registerUser(username, this);
        if(old == null){
            if(this.username == null){
                this.username = username;
                this.usernameFull = usernameFull;
                logInfo("User '" + usernameFull + "' registered as '" + username + "'");
            }
        }
    }

    protected void unregister(){
        if(this.username != null){
            ServerMain.setUser(username, null);
            username = null;
        }
    }

    protected void logInfo(String message) {
        System.out.println();
        Logger.logInfo(message);
        restorePrompt();
    }

    protected void logWarning(String message) {
        System.out.println();
        Logger.logWarning(message);
        restorePrompt();
    }

    protected void logError(String message) {
        System.out.println();
        Logger.logError(message);
        restorePrompt();
    }

    protected void logDebug(String message) {
        System.out.println();
        Logger.logDebug(message);
        restorePrompt();
    }

    protected void restorePrompt() {
        System.out.print("server> ");
        System.out.flush();
    }
}
