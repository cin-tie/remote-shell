package csdev.client;

import csdev.utils.Logger;

/**
 * <p>Main class of client application
 * <p>Remote shell client for MacOS/Linux/Unix servers
 * <br>Use arguments: userNic userFullName [host]
 * @author cin-tie
 * @version 1.0
 */
public class ClientMain {

    public static void main(String[] args) {

    }

    static void waitKeyToStop(){
        Logger.logInfo("Press enter to stop...");
        try {
            System.in.read();
        } catch (Exception e) {
        }
    }

    static class Session {
        boolean connected = false;
    }
}
