package csdev;

import java.io.Serializable;

/**
 * MessageGetdirResult class: current directory result
 * @author cin-tie
 * @version 1.0
 */
public class MessageGetdirResult extends MessageResult implements Serializable {

    private static final long serialVersionUID = 1;

    public String currentDirectory;     // Current directory
    public long filesCount;             // Amount of files

    public MessageGetdirResult(String errorMessage) {
        super(Protocol.CMD_GETDIR, errorMessage);
        this.currentDirectory = "";
    }

    public MessageGetdirResult(String currentDirectory,  long filesCount) {
        super(Protocol.CMD_GETDIR);
        this.currentDirectory = currentDirectory;
        this.filesCount = filesCount;
    }
}
