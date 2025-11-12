package csdev.messages;

import csdev.Protocol;

import java.io.Serializable;

/**
 * <p>MessageConnectResult class: Server connection response
 */
public class MessageConnectResult extends MessageResult implements Serializable {

    private static final long serialVersionUID = 1L;

    public String serverOS;        // Server operating system
    public String currentDir;       // Initial working directory
    public String serverVersion;    // Server software version

    public MessageConnectResult(String errorMessage) {
        super(Protocol.CMD_CONNECT, errorMessage);
        this.serviceOS = "";
        this.currentDir = "";
        this.serverVersion = "";
    }

    public MessageConnectResult() {
        super(Protocol.CMD_CONNECT);
        this.serviceOS = "";
        this.currentDir = "";
        this.serverVersion = "";
    }

    public MessageConnectResult(String serviceOS, String currentDir, String serverVersion) {
        super(Protocol.CMD_CONNECT);
        this.serviceOS = serviceOS;
        this.currentDir = currentDir;
        this.serverVersion = serverVersion;
    }
}
