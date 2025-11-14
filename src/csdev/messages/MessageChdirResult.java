package csdev.messages;

import csdev.Protocol;

import java.io.Serializable;

/**
 * MessageChdirResult class: directory change result
 * @author cin-tie
 * @version 1.0
 */
public class MessageChdirResult extends MessageResult implements Serializable {

    private static final long serialVersionUID = 1L;

    public String newDirectory;     // New directory
    public String oldDirectory;     // Old directory

    public MessageChdirResult(String errorMessage) {
        super(Protocol.CMD_CHDIR, errorMessage);
        this.newDirectory = "";
        this.oldDirectory = "";
    }

    public MessageChdirResult(String newDirectory, String oldDirectory) {
        super(Protocol.CMD_CHDIR);
        this.newDirectory = newDirectory;
        this.oldDirectory = oldDirectory;
    }
}
