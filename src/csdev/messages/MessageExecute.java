package csdev.messages;

import csdev.Protocol;

import java.io.Serializable;

/**
 * <p>MessageExecute class: execute shell command on server
 * @author cin-tie
 * @version 1.0
 */
public class MessageExecute extends Message implements Serializable {

    private static final long serialVersionUID = 1L;

    public String command;      // Command to execute
    public String workingDir;   // Working directory
    public long timeMillis;     // Time of executing

    public MessageExecute(String command) {
        super(Protocol.CMD_EXECUTE);
        this.command = command;
        this.workingDir = "";
        this.timeMillis = 30000;
    }

    public MessageExecute(String command, String workingDir, long timeMillis) {
        super(Protocol.CMD_EXECUTE);
        this.command = command;
        this.workingDir = workingDir;
        this.timeMillis = timeMillis;
    }
}
