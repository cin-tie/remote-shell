package csdev.messages;

import csdev.Protocol;

import java.io.Serializable;

/**
 * MessageChdir class: change working directory
 * @author cin-tie
 * @version 1.0
 */
public class MessageChdir extends Message implements Serializable {

    private static final long serialVersionUID = 1L;

    public String newDirectory;     // New directory

    public MessageChdir(String newDirectory) {
        super(Protocol.CMD_CHDIR);
        this.newDirectory = newDirectory;
    }
}
