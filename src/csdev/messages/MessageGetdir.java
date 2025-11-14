package csdev.messages;

import csdev.Protocol;

import java.io.Serializable;

/**
 * <p>MessageGetdir class: get current working directory
 * @author cin-tie
 * @version 1.0
 */
public class MessageGetdir extends Message implements Serializable {

    private static final long serialVersionUID = 1L;

    public MessageGetdir() {
        super(Protocol.CMD_GETDIR);
    }
}
